package com.adityachandel.booklore.service.metadata;

import com.adityachandel.booklore.model.MetadataClearFlags;
import com.adityachandel.booklore.model.MetadataUpdateContext;
import com.adityachandel.booklore.model.MetadataUpdateWrapper;
import com.adityachandel.booklore.model.dto.BookMetadata;
import com.adityachandel.booklore.model.dto.settings.MetadataPersistenceSettings;
import com.adityachandel.booklore.model.entity.*;
import com.adityachandel.booklore.model.enums.BookFileType;
import com.adityachandel.booklore.model.enums.MetadataReplaceMode;
import com.adityachandel.booklore.repository.AuthorRepository;
import com.adityachandel.booklore.repository.BookRepository;
import com.adityachandel.booklore.repository.CategoryRepository;
import com.adityachandel.booklore.repository.MoodRepository;
import com.adityachandel.booklore.repository.TagRepository;
import com.adityachandel.booklore.service.FileFingerprint;
import com.adityachandel.booklore.service.appsettings.AppSettingService;
import com.adityachandel.booklore.service.file.UnifiedFileMoveService;
import com.adityachandel.booklore.service.metadata.writer.MetadataWriterFactory;
import com.adityachandel.booklore.util.FileService;
import com.adityachandel.booklore.util.MetadataChangeDetector;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.Pair;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.io.File;
import java.net.InetAddress;
import java.net.URL;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Collectors;

@Slf4j
@Service
@AllArgsConstructor
public class BookMetadataUpdater {

    private final AuthorRepository authorRepository;
    private final CategoryRepository categoryRepository;
    private final MoodRepository moodRepository;
    private final TagRepository tagRepository;
    private final BookRepository bookRepository;
    private final FileService fileService;
    private final MetadataMatchService metadataMatchService;
    private final AppSettingService appSettingService;
    private final MetadataWriterFactory metadataWriterFactory;
    private final BookReviewUpdateService bookReviewUpdateService;
    private final UnifiedFileMoveService unifiedFileMoveService;

    @Transactional
    public void setBookMetadata(MetadataUpdateContext context) {
        // First: Update metadata in database within transaction
        updateMetadataInDatabase(context);
        
        // Second: Handle file operations outside of transaction to prevent session corruption
        handleFileOperationsAfterMetadataUpdate(context);
    }

    @Transactional
    private void updateMetadataInDatabase(MetadataUpdateContext context) {
        BookEntity bookEntity = context.getBookEntity();
        MetadataUpdateWrapper wrapper = context.getMetadataUpdateWrapper();
        boolean mergeCategories = context.isMergeCategories();
        boolean updateThumbnail = context.isUpdateThumbnail();
        MetadataReplaceMode replaceMode = context.getReplaceMode();

        Long bookId = bookEntity.getId();
        BookMetadata newMetadata = wrapper.getMetadata();

        if (newMetadata == null) {
            log.warn("Metadata is null for book ID {}. Skipping update.", bookId);
            return;
        }

        MetadataClearFlags clearFlags = wrapper.getClearFlags();
        BookMetadataEntity metadata = bookEntity.getMetadata();

        boolean thumbnailRequiresUpdate = StringUtils.hasText(newMetadata.getThumbnailUrl());
        boolean hasMetadataChanges = MetadataChangeDetector.isDifferent(newMetadata, metadata, clearFlags);
        if (!thumbnailRequiresUpdate && !hasMetadataChanges) {
            log.info("No changes in metadata for book ID {}. Skipping update.", bookId);
            return;
        }

        updateLocks(newMetadata, metadata);

        if (metadata.areAllFieldsLocked()) {
            log.warn("All fields are locked for book ID {}. Skipping update.", bookId);
            return;
        }

        // Database operations only - no file operations in this transaction
        updateBasicFields(newMetadata, metadata, clearFlags, replaceMode);
        updateAuthorsIfNeeded(newMetadata, metadata, clearFlags, mergeCategories, replaceMode);
        updateCategoriesIfNeeded(newMetadata, metadata, clearFlags, mergeCategories, replaceMode);
        updateMoodsIfNeeded(newMetadata, metadata, clearFlags, mergeCategories, replaceMode);
        updateTagsIfNeeded(newMetadata, metadata, clearFlags, mergeCategories, replaceMode);
        bookReviewUpdateService.updateBookReviews(newMetadata, metadata, clearFlags, mergeCategories);
        updateThumbnailIfNeeded(bookId, newMetadata, metadata, updateThumbnail);

        bookRepository.save(bookEntity);

        try {
            Float score = metadataMatchService.calculateMatchScore(bookEntity);
            bookEntity.setMetadataMatchScore(score);
        } catch (Exception e) {
            log.warn("Failed to calculate metadata match score for book ID {}: {}", bookId, e.getMessage());
        }
    }

    /**
     * Handles file operations (metadata writing and file movement) after database transaction has completed.
     * This prevents file operation failures from corrupting the Hibernate session.
     */
    private void handleFileOperationsAfterMetadataUpdate(MetadataUpdateContext context) {
        BookEntity bookEntity = context.getBookEntity();
        MetadataUpdateWrapper wrapper = context.getMetadataUpdateWrapper();
        boolean updateThumbnail = context.isUpdateThumbnail();
        BookMetadata newMetadata = wrapper.getMetadata();
        MetadataClearFlags clearFlags = wrapper.getClearFlags();
        
        Long bookId = bookEntity.getId();
        BookMetadataEntity metadata = bookEntity.getMetadata();
        
        MetadataPersistenceSettings settings = appSettingService.getAppSettings().getMetadataPersistenceSettings();
        boolean writeToFile = settings.isSaveToOriginalFile();
        boolean hasValueChanges = MetadataChangeDetector.hasValueChanges(newMetadata, metadata, clearFlags);
        boolean thumbnailRequiresUpdate = StringUtils.hasText(newMetadata.getThumbnailUrl());
        
        // Handle metadata file writing with isolated error handling
        if ((writeToFile && hasValueChanges) || thumbnailRequiresUpdate) {
            try {
                writeMetadataToFile(bookEntity, newMetadata, metadata, clearFlags, updateThumbnail, thumbnailRequiresUpdate);
            } catch (Exception e) {
                log.warn("Failed to write metadata to file for book ID {}: {}", bookId, e.getMessage());
                // Continue processing - don't fail the entire operation
            }
        }
        
        // Handle file movement with isolated error handling
        if (settings.isMoveFilesToLibraryPattern()) {
            try {
                unifiedFileMoveService.moveSingleBookFile(bookEntity);
            } catch (Exception e) {
                log.warn("Failed to move files for book ID {} after metadata update: {}", bookId, e.getMessage());
                // Continue processing - don't fail the entire operation
            }
        }
    }

    /**
     * Writes metadata to the book file, handling CBX conversion and file hash updates.
     */
    private void writeMetadataToFile(BookEntity bookEntity, BookMetadata newMetadata, BookMetadataEntity metadata, 
                                   MetadataClearFlags clearFlags, boolean updateThumbnail, boolean thumbnailRequiresUpdate) {
        Long bookId = bookEntity.getId();
        BookFileType bookType = bookEntity.getBookType();
        MetadataPersistenceSettings settings = appSettingService.getAppSettings().getMetadataPersistenceSettings();
        boolean convertCbrCb7ToCbz = settings.isConvertCbrCb7ToCbz();
        
        if (bookType == BookFileType.CBX && !convertCbrCb7ToCbz) {
            log.info("CBX metadata writing disabled for book ID {}", bookId);
            return;
        }
        
        metadataWriterFactory.getWriter(bookType).ifPresent(writer -> {
            try {
                String thumbnailUrl = updateThumbnail ? newMetadata.getThumbnailUrl() : null;

                if ((StringUtils.hasText(thumbnailUrl) && isLocalOrPrivateUrl(thumbnailUrl) || Boolean.TRUE.equals(metadata.getCoverLocked()))) {
                    log.debug("Blocked local/private thumbnail URL: {}", thumbnailUrl);
                    thumbnailUrl = null;
                }

                File file = new File(bookEntity.getFullFilePath().toUri());
                writer.writeMetadataToFile(file, metadata, thumbnailUrl, clearFlags);

                String newHash;

                // Special handling: If original file was .cbr or .cb7 and now .cbz exists, update to .cbz
                File resultingFile = file;
                if (!file.exists()) {
                    // Replace last extension .cbr or .cb7 (case-insensitive) with .cbz
                    String cbzName = file.getName().replaceFirst("(?i)\\.(cbr|cb7)$", ".cbz");
                    File cbzFile = new File(file.getParentFile(), cbzName);
                    if (cbzFile.exists()) {
                        bookEntity.setFileName(cbzName);
                        resultingFile = cbzFile;
                    }
                    bookEntity.setFileSizeKb(resultingFile.length() / 1024);
                    log.info("Converted to CBZ: {} -> {}", file.getAbsolutePath(), resultingFile.getAbsolutePath());
                    newHash = FileFingerprint.generateHash(resultingFile.toPath());
                } else {
                    newHash = FileFingerprint.generateHash(bookEntity.getFullFilePath());
                }

                bookEntity.setCurrentHash(newHash);
                bookRepository.save(bookEntity); // Save the updated hash and filename
            } catch (Exception e) {
                log.warn("Failed to write metadata for book ID {}: {}", bookId, e.getMessage());
                throw e; // Re-throw to be caught by caller for proper error handling
            }
        });
    }


    private void updateBasicFields(BookMetadata m, BookMetadataEntity e, MetadataClearFlags clear, MetadataReplaceMode replaceMode) {
        handleFieldUpdate(e.getTitleLocked(), clear.isTitle(), m.getTitle(), v -> e.setTitle(nullIfBlank(v)), e::getTitle, replaceMode);
        handleFieldUpdate(e.getSubtitleLocked(), clear.isSubtitle(), m.getSubtitle(), v -> e.setSubtitle(nullIfBlank(v)), e::getSubtitle, replaceMode);
        handleFieldUpdate(e.getPublisherLocked(), clear.isPublisher(), m.getPublisher(), v -> e.setPublisher(nullIfBlank(v)), e::getPublisher, replaceMode);
        handleFieldUpdate(e.getPublishedDateLocked(), clear.isPublishedDate(), m.getPublishedDate(), e::setPublishedDate, e::getPublishedDate, replaceMode);
        handleFieldUpdate(e.getDescriptionLocked(), clear.isDescription(), m.getDescription(), v -> e.setDescription(nullIfBlank(v)), e::getDescription, replaceMode);
        handleFieldUpdate(e.getSeriesNameLocked(), clear.isSeriesName(), m.getSeriesName(), e::setSeriesName, e::getSeriesName, replaceMode);
        handleFieldUpdate(e.getSeriesNumberLocked(), clear.isSeriesNumber(), m.getSeriesNumber(), e::setSeriesNumber, e::getSeriesNumber, replaceMode);
        handleFieldUpdate(e.getSeriesTotalLocked(), clear.isSeriesTotal(), m.getSeriesTotal(), e::setSeriesTotal, e::getSeriesTotal, replaceMode);
        handleFieldUpdate(e.getIsbn13Locked(), clear.isIsbn13(), m.getIsbn13(), v -> e.setIsbn13(nullIfBlank(v)), e::getIsbn13, replaceMode);
        handleFieldUpdate(e.getIsbn10Locked(), clear.isIsbn10(), m.getIsbn10(), v -> e.setIsbn10(nullIfBlank(v)), e::getIsbn10, replaceMode);
        handleFieldUpdate(e.getAsinLocked(), clear.isAsin(), m.getAsin(), v -> e.setAsin(nullIfBlank(v)), e::getAsin, replaceMode);
        handleFieldUpdate(e.getGoodreadsIdLocked(), clear.isGoodreadsId(), m.getGoodreadsId(), v -> e.setGoodreadsId(nullIfBlank(v)), e::getGoodreadsId, replaceMode);
        handleFieldUpdate(e.getComicvineIdLocked(), clear.isComicvineId(), m.getComicvineId(), v -> e.setComicvineId(nullIfBlank(v)), e::getComicvineId, replaceMode);
        handleFieldUpdate(e.getHardcoverIdLocked(), clear.isHardcoverId(), m.getHardcoverId(), v -> e.setHardcoverId(nullIfBlank(v)), e::getHardcoverId, replaceMode);
        handleFieldUpdate(e.getGoogleIdLocked(), clear.isGoogleId(), m.getGoogleId(), v -> e.setGoogleId(nullIfBlank(v)), e::getGoogleId, replaceMode);
        handleFieldUpdate(e.getPageCountLocked(), clear.isPageCount(), m.getPageCount(), e::setPageCount, e::getPageCount, replaceMode);
        handleFieldUpdate(e.getLanguageLocked(), clear.isLanguage(), m.getLanguage(), v -> e.setLanguage(nullIfBlank(v)), e::getLanguage, replaceMode);
        handleFieldUpdate(e.getPersonalRatingLocked(), clear.isPersonalRating(), m.getPersonalRating(), e::setPersonalRating, e::getPersonalRating, replaceMode);
        handleFieldUpdate(e.getAmazonRatingLocked(), clear.isAmazonRating(), m.getAmazonRating(), e::setAmazonRating, e::getAmazonRating, replaceMode);
        handleFieldUpdate(e.getAmazonReviewCountLocked(), clear.isAmazonReviewCount(), m.getAmazonReviewCount(), e::setAmazonReviewCount, e::getAmazonReviewCount, replaceMode);
        handleFieldUpdate(e.getGoodreadsRatingLocked(), clear.isGoodreadsRating(), m.getGoodreadsRating(), e::setGoodreadsRating, e::getGoodreadsRating, replaceMode);
        handleFieldUpdate(e.getGoodreadsReviewCountLocked(), clear.isGoodreadsReviewCount(), m.getGoodreadsReviewCount(), e::setGoodreadsReviewCount, e::getGoodreadsReviewCount, replaceMode);
        handleFieldUpdate(e.getHardcoverRatingLocked(), clear.isHardcoverRating(), m.getHardcoverRating(), e::setHardcoverRating, e::getHardcoverRating, replaceMode);
        handleFieldUpdate(e.getHardcoverReviewCountLocked(), clear.isHardcoverReviewCount(), m.getHardcoverReviewCount(), e::setHardcoverReviewCount, e::getHardcoverReviewCount, replaceMode);
    }

    private <T> void handleFieldUpdate(Boolean locked, boolean shouldClear, T newValue, Consumer<T> setter, Supplier<T> getter, MetadataReplaceMode replaceMode) {
        if (Boolean.TRUE.equals(locked)) return;
        if (shouldClear) {
            setter.accept(null);
            return;
        } else if (replaceMode == MetadataReplaceMode.REPLACE_ALL) {
            setter.accept(newValue);
        } else if (replaceMode == MetadataReplaceMode.REPLACE_MISSING) {
            T currentValue = getter.get();
            if (isValueMissing(currentValue)) {
                setter.accept(newValue);
            }
        }
    }

    private <T> boolean isValueMissing(T value) {
        if (value == null) return true;
        if (value instanceof String) return !StringUtils.hasText((String) value);
        return false;
    }

    private void updateAuthorsIfNeeded(BookMetadata m, BookMetadataEntity e, MetadataClearFlags clear, boolean merge, MetadataReplaceMode replaceMode) {
        if (Boolean.TRUE.equals(e.getAuthorsLocked())) {
            return;
        }
        if (e.getAuthors() == null) {
            e.setAuthors(new HashSet<>());
        }
        if (clear.isAuthors()) {
            e.getAuthors().clear();
            return;
        }
        if (m.getAuthors() == null) {
            if (replaceMode == MetadataReplaceMode.REPLACE_ALL) {
                e.getAuthors().clear();
            }
            return;
        }
        Set<AuthorEntity> newAuthors = m.getAuthors().stream()
                .filter(a -> a != null && !a.isBlank())
                .map(name -> authorRepository.findByName(name)
                        .orElseGet(() -> authorRepository.save(AuthorEntity.builder().name(name).build())))
                .collect(Collectors.toSet());

        if (replaceMode == MetadataReplaceMode.REPLACE_ALL) {
            if (merge) {
                e.getAuthors().addAll(newAuthors);
            } else {
                e.getAuthors().clear();
                e.getAuthors().addAll(newAuthors);
            }
        } else if (replaceMode == MetadataReplaceMode.REPLACE_MISSING) {
            if (e.getAuthors().isEmpty()) {
                e.getAuthors().addAll(newAuthors);
            }
        }
    }

    private void updateCategoriesIfNeeded(BookMetadata m, BookMetadataEntity e, MetadataClearFlags clear, boolean merge, MetadataReplaceMode replaceMode) {
        if (Boolean.TRUE.equals(e.getCategoriesLocked())) {
            return;
        }
        if (e.getCategories() == null) {
            e.setCategories(new HashSet<>());
        }
        if (clear.isCategories()) {
            e.getCategories().clear();
            return;
        }
        if (m.getCategories() == null) {
            if (replaceMode == MetadataReplaceMode.REPLACE_ALL) {
                e.getCategories().clear();
            }
            return;
        }

        Set<CategoryEntity> newCategories = m.getCategories().stream()
                .filter(n -> n != null && !n.isBlank())
                .map(name -> categoryRepository.findByName(name)
                        .orElseGet(() -> categoryRepository.save(CategoryEntity.builder().name(name).build())))
                .collect(Collectors.toSet());

        if (replaceMode == MetadataReplaceMode.REPLACE_ALL) {
            if (merge) {
                e.getCategories().addAll(newCategories);
            } else {
                e.getCategories().clear();
                e.getCategories().addAll(newCategories);
            }
        } else if (replaceMode == MetadataReplaceMode.REPLACE_MISSING) {
            if (e.getCategories().isEmpty()) {
                e.getCategories().addAll(newCategories);
            }
        }
    }

    private void updateMoodsIfNeeded(BookMetadata m, BookMetadataEntity e, MetadataClearFlags clear, boolean merge, MetadataReplaceMode replaceMode) {
        if (Boolean.TRUE.equals(e.getMoodsLocked())) {
            return;
        }
        if (e.getMoods() == null) {
            e.setMoods(new HashSet<>());
        }
        if (clear.isMoods()) {
            e.getMoods().clear();
            return;
        }
        if (m.getMoods() == null) {
            if (replaceMode == MetadataReplaceMode.REPLACE_ALL) {
                e.getMoods().clear();
            }
            return;
        }

        Set<MoodEntity> newMoods = m.getMoods().stream()
                .filter(n -> n != null && !n.isBlank())
                .map(name -> moodRepository.findByName(name)
                        .orElseGet(() -> moodRepository.save(MoodEntity.builder().name(name).build())))
                .collect(Collectors.toSet());

        if (replaceMode == MetadataReplaceMode.REPLACE_ALL) {
            if (merge) {
                e.getMoods().addAll(newMoods);
            } else {
                e.getMoods().clear();
                e.getMoods().addAll(newMoods);
            }
        } else if (replaceMode == MetadataReplaceMode.REPLACE_MISSING) {
            if (e.getMoods().isEmpty()) {
                e.getMoods().addAll(newMoods);
            }
        }
    }

    private void updateTagsIfNeeded(BookMetadata m, BookMetadataEntity e, MetadataClearFlags clear, boolean merge, MetadataReplaceMode replaceMode) {
        if (Boolean.TRUE.equals(e.getTagsLocked())) {
            return;
        }
        if (e.getTags() == null) {
            e.setTags(new HashSet<>());
        }
        if (clear.isTags()) {
            e.getTags().clear();
            return;
        }
        if (m.getTags() == null) {
            if (replaceMode == MetadataReplaceMode.REPLACE_ALL) {
                e.getTags().clear();
            }
            return;
        }

        Set<TagEntity> newTags = m.getTags().stream()
                .filter(n -> n != null && !n.isBlank())
                .map(name -> tagRepository.findByName(name)
                        .orElseGet(() -> tagRepository.save(TagEntity.builder().name(name).build())))
                .collect(Collectors.toSet());

        if (replaceMode == MetadataReplaceMode.REPLACE_ALL) {
            if (merge) {
                e.getTags().addAll(newTags);
            } else {
                e.getTags().clear();
                e.getTags().addAll(newTags);
            }
        } else if (replaceMode == MetadataReplaceMode.REPLACE_MISSING) {
            if (e.getTags().isEmpty()) {
                e.getTags().addAll(newTags);
            }
        }
    }

    private void updateThumbnailIfNeeded(long bookId, BookMetadata m, BookMetadataEntity e, boolean set) {
        if (Boolean.TRUE.equals(e.getCoverLocked())) {
            return; // Locked — do nothing
        }
        if (!set) return;
        if (!StringUtils.hasText(m.getThumbnailUrl()) || isLocalOrPrivateUrl(m.getThumbnailUrl())) return;
        fileService.createThumbnailFromUrl(bookId, m.getThumbnailUrl());
        e.setCoverUpdatedOn(Instant.now());
    }

    private void updateLocks(BookMetadata m, BookMetadataEntity e) {
        List<Pair<Boolean, Consumer<Boolean>>> lockMappings = List.of(
                Pair.of(m.getTitleLocked(), e::setTitleLocked),
                Pair.of(m.getSubtitleLocked(), e::setSubtitleLocked),
                Pair.of(m.getPublisherLocked(), e::setPublisherLocked),
                Pair.of(m.getPublishedDateLocked(), e::setPublishedDateLocked),
                Pair.of(m.getDescriptionLocked(), e::setDescriptionLocked),
                Pair.of(m.getSeriesNameLocked(), e::setSeriesNameLocked),
                Pair.of(m.getSeriesNumberLocked(), e::setSeriesNumberLocked),
                Pair.of(m.getSeriesTotalLocked(), e::setSeriesTotalLocked),
                Pair.of(m.getIsbn13Locked(), e::setIsbn13Locked),
                Pair.of(m.getIsbn10Locked(), e::setIsbn10Locked),
                Pair.of(m.getAsinLocked(), e::setAsinLocked),
                Pair.of(m.getGoodreadsIdLocked(), e::setGoodreadsIdLocked),
                Pair.of(m.getComicvineIdLocked(), e::setComicvineIdLocked),
                Pair.of(m.getHardcoverIdLocked(), e::setHardcoverIdLocked),
                Pair.of(m.getGoogleIdLocked(), e::setGoogleIdLocked),
                Pair.of(m.getPageCountLocked(), e::setPageCountLocked),
                Pair.of(m.getLanguageLocked(), e::setLanguageLocked),
                Pair.of(m.getPersonalRatingLocked(), e::setPersonalRatingLocked),
                Pair.of(m.getAmazonRatingLocked(), e::setAmazonRatingLocked),
                Pair.of(m.getAmazonReviewCountLocked(), e::setAmazonReviewCountLocked),
                Pair.of(m.getGoodreadsRatingLocked(), e::setGoodreadsRatingLocked),
                Pair.of(m.getGoodreadsReviewCountLocked(), e::setGoodreadsReviewCountLocked),
                Pair.of(m.getHardcoverRatingLocked(), e::setHardcoverRatingLocked),
                Pair.of(m.getHardcoverReviewCountLocked(), e::setHardcoverReviewCountLocked),
                Pair.of(m.getCoverLocked(), e::setCoverLocked),
                Pair.of(m.getAuthorsLocked(), e::setAuthorsLocked),
                Pair.of(m.getCategoriesLocked(), e::setCategoriesLocked),
                Pair.of(m.getMoodsLocked(), e::setMoodsLocked),
                Pair.of(m.getTagsLocked(), e::setTagsLocked),
                Pair.of(m.getReviewsLocked(), e::setReviewsLocked)
        );
        lockMappings.forEach(pair -> {
            if (pair.getLeft() != null) pair.getRight().accept(pair.getLeft());
        });
    }

    private String nullIfBlank(String value) {
        return StringUtils.hasText(value) ? value : null;
    }

    private boolean isLocalOrPrivateUrl(String url) {
        try {
            URL parsed = new URL(url);
            String host = parsed.getHost();
            if ("localhost".equalsIgnoreCase(host) || "127.0.0.1".equals(host)) return true;
            InetAddress addr = InetAddress.getByName(host);
            return addr.isLoopbackAddress() || addr.isSiteLocalAddress();
        } catch (Exception e) {
            log.warn("Invalid thumbnail URL '{}': {}", url, e.getMessage());
            return true;
        }
    }
}
