package com.adityachandel.booklore.service.metadata;

import com.adityachandel.booklore.model.MetadataClearFlags;
import com.adityachandel.booklore.model.MetadataUpdateContext;
import com.adityachandel.booklore.model.MetadataUpdateWrapper;
import com.adityachandel.booklore.model.dto.BookMetadata;
import com.adityachandel.booklore.model.dto.FileMoveResult;
import com.adityachandel.booklore.model.dto.settings.MetadataPersistenceSettings;
import com.adityachandel.booklore.model.entity.*;
import com.adityachandel.booklore.model.enums.BookFileType;
import com.adityachandel.booklore.model.enums.MetadataReplaceMode;
import com.adityachandel.booklore.repository.*;
import com.adityachandel.booklore.service.appsettings.AppSettingService;
import com.adityachandel.booklore.service.file.FileFingerprint;
import com.adityachandel.booklore.service.file.FileMoveService;
import com.adityachandel.booklore.service.metadata.writer.MetadataWriterFactory;
import com.adityachandel.booklore.util.BookCoverUtils;
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
import java.net.URI;
import java.time.Instant;
import java.util.*;
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
    private final FileMoveService fileMoveService;

    @Transactional
    public void setBookMetadata(MetadataUpdateContext context) {
        BookEntity bookEntity = context.getBookEntity();
        MetadataUpdateWrapper wrapper = context.getMetadataUpdateWrapper();
        boolean mergeCategories = context.isMergeCategories();
        boolean mergeMoods = context.isMergeMoods();
        boolean mergeTags = context.isMergeTags();
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
        boolean hasValueChanges = MetadataChangeDetector.hasValueChanges(newMetadata, metadata, clearFlags);
        if (!thumbnailRequiresUpdate && !hasMetadataChanges) {
            log.info("No changes in metadata for book ID {}. Skipping update.", bookId);
            return;
        }

        if (metadata.areAllFieldsLocked() && hasValueChanges) {
            log.warn("All fields are locked for book ID {}. Skipping update.", bookId);
            return;
        }

        MetadataPersistenceSettings settings = appSettingService.getAppSettings().getMetadataPersistenceSettings();
        MetadataPersistenceSettings.SaveToOriginalFile writeToFile = settings.getSaveToOriginalFile();
        var primaryFile = bookEntity.getPrimaryBookFile();
        BookFileType bookType = primaryFile != null ? primaryFile.getBookType() : null;

        boolean hasValueChangesForFileWrite = MetadataChangeDetector.hasValueChangesForFileWrite(newMetadata, metadata, clearFlags);

        updateBasicFields(newMetadata, metadata, clearFlags, replaceMode);
        updateAuthorsIfNeeded(newMetadata, metadata, clearFlags, mergeCategories, replaceMode);
        updateCategoriesIfNeeded(newMetadata, metadata, clearFlags, mergeCategories, replaceMode);
        updateMoodsIfNeeded(newMetadata, metadata, clearFlags, mergeMoods, replaceMode);
        updateTagsIfNeeded(newMetadata, metadata, clearFlags, mergeTags, replaceMode);
        bookReviewUpdateService.updateBookReviews(newMetadata, metadata, clearFlags, mergeCategories);
        updateThumbnailIfNeeded(bookId, bookEntity, newMetadata, metadata, updateThumbnail);
        updateLocks(newMetadata, metadata);

        bookEntity.setMetadataUpdatedAt(Instant.now());
        bookRepository.save(bookEntity);
        try {
            Float score = metadataMatchService.calculateMatchScore(bookEntity);
            bookEntity.setMetadataMatchScore(score);
        } catch (Exception e) {
            log.warn("Failed to calculate metadata match score for book ID {}: {}", bookId, e.getMessage());
        }

        if (primaryFile != null && bookType != null && ((writeToFile.isAnyFormatEnabled() && hasValueChangesForFileWrite) || thumbnailRequiresUpdate)) {
            metadataWriterFactory.getWriter(bookType).ifPresent(writer -> {
                try {
                    String thumbnailUrl = updateThumbnail ? newMetadata.getThumbnailUrl() : null;
                    if ((StringUtils.hasText(thumbnailUrl) && isLocalOrPrivateUrl(thumbnailUrl) || Boolean.TRUE.equals(metadata.getCoverLocked()))) {
                        log.debug("Blocked local/private thumbnail URL: {}", thumbnailUrl);
                        thumbnailUrl = null;
                    }
                    File file = new File(bookEntity.getFullFilePath().toUri());
                    writer.saveMetadataToFile(file, metadata, thumbnailUrl, clearFlags);
                    String newHash = FileFingerprint.generateHash(bookEntity.getFullFilePath());
                    bookEntity.setMetadataForWriteUpdatedAt(Instant.now());
                    primaryFile.setCurrentHash(newHash);
                    bookRepository.save(bookEntity);
                } catch (Exception e) {
                    log.warn("Failed to write metadata for book ID {}: {}", bookId, e.getMessage());
                }
            });
        }

        boolean moveFilesToLibraryPattern = settings.isMoveFilesToLibraryPattern();
        if (moveFilesToLibraryPattern && primaryFile != null) {
            try {
                BookEntity book = metadata.getBook();
                FileMoveResult result = fileMoveService.moveSingleFile(book);
                if (result.isMoved()) {
                    var bookPrimaryFile = book.getPrimaryBookFile();
                    if (bookPrimaryFile != null) {
                        bookPrimaryFile.setFileName(result.getNewFileName());
                        bookPrimaryFile.setFileSubPath(result.getNewFileSubPath());
                    }
                }
            } catch (Exception e) {
                log.warn("Failed to move files for book ID {} after metadata update: {}", bookId, e.getMessage());
            }
        }
    }

    private void updateBasicFields(BookMetadata m, BookMetadataEntity e, MetadataClearFlags clear, MetadataReplaceMode replaceMode) {
        if (clear == null) {
            clear = new MetadataClearFlags();
        }
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
        handleFieldUpdate(e.getHardcoverBookIdLocked(), clear.isHardcoverBookId(), m.getHardcoverBookId(), e::setHardcoverBookId, e::getHardcoverBookId, replaceMode);
        handleFieldUpdate(e.getGoogleIdLocked(), clear.isGoogleId(), m.getGoogleId(), v -> e.setGoogleId(nullIfBlank(v)), e::getGoogleId, replaceMode);
        handleFieldUpdate(e.getPageCountLocked(), clear.isPageCount(), m.getPageCount(), e::setPageCount, e::getPageCount, replaceMode);
        handleFieldUpdate(e.getLanguageLocked(), clear.isLanguage(), m.getLanguage(), v -> e.setLanguage(nullIfBlank(v)), e::getLanguage, replaceMode);
        handleFieldUpdate(e.getAmazonRatingLocked(), clear.isAmazonRating(), m.getAmazonRating(), e::setAmazonRating, e::getAmazonRating, replaceMode);
        handleFieldUpdate(e.getAmazonReviewCountLocked(), clear.isAmazonReviewCount(), m.getAmazonReviewCount(), e::setAmazonReviewCount, e::getAmazonReviewCount, replaceMode);
        handleFieldUpdate(e.getGoodreadsRatingLocked(), clear.isGoodreadsRating(), m.getGoodreadsRating(), e::setGoodreadsRating, e::getGoodreadsRating, replaceMode);
        handleFieldUpdate(e.getGoodreadsReviewCountLocked(), clear.isGoodreadsReviewCount(), m.getGoodreadsReviewCount(), e::setGoodreadsReviewCount, e::getGoodreadsReviewCount, replaceMode);
        handleFieldUpdate(e.getHardcoverRatingLocked(), clear.isHardcoverRating(), m.getHardcoverRating(), e::setHardcoverRating, e::getHardcoverRating, replaceMode);
        handleFieldUpdate(e.getHardcoverReviewCountLocked(), clear.isHardcoverReviewCount(), m.getHardcoverReviewCount(), e::setHardcoverReviewCount, e::getHardcoverReviewCount, replaceMode);
        handleFieldUpdate(e.getLubimyczytacIdLocked(), clear.isLubimyczytacId(), m.getLubimyczytacId(), v -> e.setLubimyczytacId(nullIfBlank(v)), () -> e.getLubimyczytacId(), replaceMode);
        handleFieldUpdate(e.getLubimyczytacRatingLocked(), clear.isLubimyczytacRating(), m.getLubimyczytacRating(), v -> e.setLubimyczytacRating(v), () -> e.getLubimyczytacRating(), replaceMode);
        handleFieldUpdate(e.getRanobedbIdLocked(), clear.isRanobedbId(), m.getRanobedbId(), v -> e.setRanobedbId(nullIfBlank(v)), e::getRanobedbId, replaceMode);
        handleFieldUpdate(e.getRanobedbRatingLocked(), clear.isRanobedbRating(), m.getRanobedbRating(), e::setRanobedbRating, e::getRanobedbRating, replaceMode);
    }

    private <T> void handleFieldUpdate(Boolean locked, boolean shouldClear, T newValue, Consumer<T> setter, Supplier<T> getter, MetadataReplaceMode mode) {
        if (Boolean.TRUE.equals(locked)) return;
        if (shouldClear) {
            setter.accept(null);
            return;
        }
        if (mode == null) {
            if (newValue != null) setter.accept(newValue);
            return;
        }
        switch (mode) {
            case REPLACE_ALL -> setter.accept(newValue);
            case REPLACE_MISSING -> {
                if (isValueMissing(getter.get())) setter.accept(newValue);
            }
            case REPLACE_WHEN_PROVIDED -> {
                if (!isValueMissing(newValue)) setter.accept(newValue);
            }
        }
    }

    private <T> boolean isValueMissing(T value) {
        if (value == null) return true;
        if (value instanceof String) return !StringUtils.hasText((String) value);
        return false;
    }

    private void updateAuthorsIfNeeded(BookMetadata m, BookMetadataEntity e, MetadataClearFlags clear, boolean merge, MetadataReplaceMode replaceMode) {
        if (Boolean.TRUE.equals(e.getAuthorsLocked())) return;

        e.setAuthors(Optional.ofNullable(e.getAuthors()).orElseGet(HashSet::new));

        if (clear.isAuthors()) {
            e.getAuthors().clear();
            return;
        }

        Set<String> authorNames = Optional.ofNullable(m.getAuthors()).orElse(Collections.emptySet());
        if (authorNames.isEmpty()) {
            if (replaceMode == MetadataReplaceMode.REPLACE_ALL) e.getAuthors().clear();
            return;
        }

        Set<AuthorEntity> newAuthors = authorNames.stream()
                .filter(name -> name != null && !name.isBlank())
                .map(name -> authorRepository.findByName(name)
                        .orElseGet(() -> authorRepository.save(AuthorEntity.builder().name(name).build())))
                .collect(Collectors.toSet());

        if (newAuthors.isEmpty()) return;

        if (replaceMode == MetadataReplaceMode.REPLACE_ALL || replaceMode == MetadataReplaceMode.REPLACE_WHEN_PROVIDED) {
            if (!merge) e.getAuthors().clear();
            e.getAuthors().addAll(newAuthors);
            e.updateSearchText();
        } else if (replaceMode == MetadataReplaceMode.REPLACE_MISSING && e.getAuthors().isEmpty()) {
            e.getAuthors().addAll(newAuthors);
            e.updateSearchText();
        } else if (replaceMode == null) {
            if (!merge) e.getAuthors().clear();
            e.getAuthors().addAll(newAuthors);
            e.updateSearchText();
        }
    }

    private void updateCategoriesIfNeeded(BookMetadata m, BookMetadataEntity e, MetadataClearFlags clear, boolean merge, MetadataReplaceMode replaceMode) {
        if (Boolean.TRUE.equals(e.getCategoriesLocked())) return;

        e.setCategories(Optional.ofNullable(e.getCategories()).orElseGet(HashSet::new));

        if (clear.isCategories()) {
            e.getCategories().clear();
            return;
        }

        Set<String> categoryNames = Optional.ofNullable(m.getCategories()).orElse(Collections.emptySet());
        if (categoryNames.isEmpty()) {
            if (replaceMode == MetadataReplaceMode.REPLACE_ALL) e.getCategories().clear();
            return;
        }

        Set<CategoryEntity> newCategories = categoryNames.stream()
                .filter(name -> name != null && !name.isBlank())
                .map(name -> categoryRepository.findByName(name)
                        .orElseGet(() -> categoryRepository.save(CategoryEntity.builder().name(name).build())))
                .collect(Collectors.toSet());

        if (newCategories.isEmpty()) return;

        if (replaceMode == MetadataReplaceMode.REPLACE_ALL || replaceMode == MetadataReplaceMode.REPLACE_WHEN_PROVIDED) {
            if (!merge) e.getCategories().clear();
            e.getCategories().addAll(newCategories);
        } else if (replaceMode == MetadataReplaceMode.REPLACE_MISSING && e.getCategories().isEmpty()) {
            e.getCategories().addAll(newCategories);
        } else if (replaceMode == null) {
            if (!merge) e.getCategories().clear();
            e.getCategories().addAll(newCategories);
        }
    }

    private void updateMoodsIfNeeded(BookMetadata m, BookMetadataEntity e, MetadataClearFlags clear, boolean merge, MetadataReplaceMode replaceMode) {
        if (Boolean.TRUE.equals(e.getMoodsLocked())) return;

        e.setMoods(Optional.ofNullable(e.getMoods()).orElseGet(HashSet::new));

        if (clear.isMoods()) {
            e.getMoods().clear();
            return;
        }

        Set<String> moodNames = Optional.ofNullable(m.getMoods()).orElse(Collections.emptySet());
        if (moodNames.isEmpty()) {
            if (replaceMode == MetadataReplaceMode.REPLACE_ALL) e.getMoods().clear();
            return;
        }

        Set<MoodEntity> newMoods = moodNames.stream()
                .filter(name -> name != null && !name.isBlank())
                .map(name -> moodRepository.findByName(name)
                        .orElseGet(() -> moodRepository.save(MoodEntity.builder().name(name).build())))
                .collect(Collectors.toSet());

        if (newMoods.isEmpty()) return;

        if (replaceMode == MetadataReplaceMode.REPLACE_ALL || replaceMode == MetadataReplaceMode.REPLACE_WHEN_PROVIDED) {
            if (!merge) e.getMoods().clear();
            e.getMoods().addAll(newMoods);
        } else if (replaceMode == MetadataReplaceMode.REPLACE_MISSING && e.getMoods().isEmpty()) {
            e.getMoods().addAll(newMoods);
        } else if (replaceMode == null) {
            if (!merge) e.getMoods().clear();
            e.getMoods().addAll(newMoods);
        }
    }

    private void updateTagsIfNeeded(BookMetadata m, BookMetadataEntity e, MetadataClearFlags clear, boolean merge, MetadataReplaceMode replaceMode) {
        if (Boolean.TRUE.equals(e.getTagsLocked())) return;

        e.setTags(Optional.ofNullable(e.getTags()).orElseGet(HashSet::new));

        if (clear.isTags()) {
            e.getTags().clear();
            return;
        }

        Set<String> tagNames = Optional.ofNullable(m.getTags()).orElse(Collections.emptySet());
        if (tagNames.isEmpty()) {
            if (replaceMode == MetadataReplaceMode.REPLACE_ALL) e.getTags().clear();
            return;
        }

        Set<TagEntity> newTags = tagNames.stream()
                .filter(name -> name != null && !name.isBlank())
                .map(name -> tagRepository.findByName(name)
                        .orElseGet(() -> tagRepository.save(TagEntity.builder().name(name).build())))
                .collect(Collectors.toSet());

        if (newTags.isEmpty()) return;

        if (replaceMode == MetadataReplaceMode.REPLACE_ALL || replaceMode == MetadataReplaceMode.REPLACE_WHEN_PROVIDED) {
            if (!merge) e.getTags().clear();
            e.getTags().addAll(newTags);
        } else if (replaceMode == MetadataReplaceMode.REPLACE_MISSING && e.getTags().isEmpty()) {
            e.getTags().addAll(newTags);
        } else if (replaceMode == null) {
            if (!merge) e.getTags().clear();
            e.getTags().addAll(newTags);
        }
    }

    private void updateThumbnailIfNeeded(long bookId, BookEntity bookEntity, BookMetadata m, BookMetadataEntity e, boolean set) {
        if (Boolean.TRUE.equals(e.getCoverLocked())) {
            return;
        }
        if (!set) return;
        if (!StringUtils.hasText(m.getThumbnailUrl()) || isLocalOrPrivateUrl(m.getThumbnailUrl())) return;
        try {
            fileService.createThumbnailFromUrl(bookId, m.getThumbnailUrl());
            bookEntity.setBookCoverHash(BookCoverUtils.generateCoverHash());
            bookEntity.getMetadata().setCoverUpdatedOn(Instant.now());
        } catch (Exception ex) {
            log.warn("Failed to download cover for book {}: {}", bookId, ex.getMessage());
        }
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
                Pair.of(m.getHardcoverBookIdLocked(), e::setHardcoverBookIdLocked),
                Pair.of(m.getLubimyczytacIdLocked(), e::setLubimyczytacIdLocked),
                Pair.of(m.getLubimyczytacRatingLocked(), e::setLubimyczytacRatingLocked),
                Pair.of(m.getGoogleIdLocked(), e::setGoogleIdLocked),
                Pair.of(m.getPageCountLocked(), e::setPageCountLocked),
                Pair.of(m.getLanguageLocked(), e::setLanguageLocked),
                Pair.of(m.getAmazonRatingLocked(), e::setAmazonRatingLocked),
                Pair.of(m.getAmazonReviewCountLocked(), e::setAmazonReviewCountLocked),
                Pair.of(m.getGoodreadsRatingLocked(), e::setGoodreadsRatingLocked),
                Pair.of(m.getGoodreadsReviewCountLocked(), e::setGoodreadsReviewCountLocked),
                Pair.of(m.getHardcoverRatingLocked(), e::setHardcoverRatingLocked),
                Pair.of(m.getHardcoverReviewCountLocked(), e::setHardcoverReviewCountLocked),
                Pair.of(m.getRanobedbIdLocked(), e::setRanobedbIdLocked),
                Pair.of(m.getRanobedbRatingLocked(), e::setRanobedbRatingLocked),
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
            URI uri = new URI(url);
            String host = uri.getHost();
            if ("localhost".equalsIgnoreCase(host) || "127.0.0.1".equals(host)) return true;
            InetAddress addr = InetAddress.getByName(host);
            return addr.isLoopbackAddress() || addr.isSiteLocalAddress();
        } catch (Exception e) {
            log.warn("Invalid thumbnail URL '{}': {}", url, e.getMessage());
            return true;
        }
    }
}
