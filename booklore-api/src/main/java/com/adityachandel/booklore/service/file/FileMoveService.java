package com.adityachandel.booklore.service.file;

import com.adityachandel.booklore.mapper.BookMapper;
import com.adityachandel.booklore.mapper.LibraryMapper;
import com.adityachandel.booklore.model.dto.FileMoveResult;
import com.adityachandel.booklore.model.dto.request.FileMoveRequest;
import com.adityachandel.booklore.model.entity.BookEntity;
import com.adityachandel.booklore.model.entity.LibraryEntity;
import com.adityachandel.booklore.model.entity.LibraryPathEntity;
import com.adityachandel.booklore.model.entity.BookMetadataEntity;
import com.adityachandel.booklore.model.enums.FileOperationMode;
import com.adityachandel.booklore.model.websocket.Topic;
import com.adityachandel.booklore.repository.BookRepository;
import com.adityachandel.booklore.repository.LibraryRepository;
import com.adityachandel.booklore.service.NotificationService;
import com.adityachandel.booklore.service.monitoring.MonitoringRegistrationService;
import com.adityachandel.booklore.util.FileService;
import jakarta.persistence.EntityManager;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@AllArgsConstructor
@Service
@Slf4j
public class FileMoveService {

    private static final long EVENT_DRAIN_TIMEOUT_MS = 300;

    private final BookRepository bookRepository;
    private final LibraryRepository libraryRepository;
    private final FileMoveHelper fileMoveHelper;
    private final MonitoringRegistrationService monitoringRegistrationService;
    private final LibraryMapper libraryMapper;
    private final BookMapper bookMapper;
    private final NotificationService notificationService;
    private final EntityManager entityManager;
    private final FileService fileService;


    @Transactional
    public void bulkMoveFiles(FileMoveRequest request) {
        List<FileMoveRequest.Move> moves = request.getMoves();
        FileOperationMode mode = request.getMode() == null ? FileOperationMode.MOVE : request.getMode();
        
        Set<Long> allAffectedLibraryIds = collectAllAffectedLibraryIds(moves);
        Set<Path> libraryPaths = monitoringRegistrationService.getPathsForLibraries(allAffectedLibraryIds);
        
        log.info("Unregistering {} libraries before bulk file move", allAffectedLibraryIds.size());
        monitoringRegistrationService.unregisterLibraries(allAffectedLibraryIds);
        monitoringRegistrationService.waitForEventsDrainedByPaths(libraryPaths, EVENT_DRAIN_TIMEOUT_MS);

        try {
            for (FileMoveRequest.Move move : moves) {
                if (mode == FileOperationMode.COPY) {
                    processSingleCopy(move);
                } else {
                    processSingleMove(move);
                }
            }
        } finally {
            for (Long libraryId : allAffectedLibraryIds) {
                libraryRepository.findById(libraryId)
                        .ifPresent(library -> monitoringRegistrationService.registerLibrary(libraryMapper.toLibrary(library)));
            }
        }
    }

    private Set<Long> collectAllAffectedLibraryIds(List<FileMoveRequest.Move> moves) {
        Set<Long> libraryIds = new HashSet<>();
        
        for (FileMoveRequest.Move move : moves) {
            libraryIds.add(move.getTargetLibraryId());
            bookRepository.findById(move.getBookId())
                    .ifPresent(book -> libraryIds.add(book.getLibrary().getId()));
        }
        
        return libraryIds;
    }

    private void processSingleMove(FileMoveRequest.Move move) {
        Long bookId = move.getBookId();
        Long targetLibraryId = move.getTargetLibraryId();
        Long targetLibraryPathId = move.getTargetLibraryPathId();

        Path tempPath = null;
        Path currentFilePath = null;

        try {
            Optional<BookEntity> optionalBook = bookRepository.findById(bookId);
            Optional<LibraryEntity> optionalLibrary = libraryRepository.findById(targetLibraryId);
            if (optionalBook.isEmpty()) {
                log.warn("Book not found for move operation: bookId={}", bookId);
                return;
            }
            if (optionalLibrary.isEmpty()) {
                log.warn("Target library not found for move operation: libraryId={}", targetLibraryId);
                return;
            }
            BookEntity bookEntity = optionalBook.get();
            LibraryEntity targetLibrary = optionalLibrary.get();

            Optional<LibraryPathEntity> optionalLibraryPathEntity = targetLibrary.getLibraryPaths().stream()
                    .filter(libraryPath -> Objects.equals(libraryPath.getId(), targetLibraryPathId))
                    .findFirst();
            if (optionalLibraryPathEntity.isEmpty()) {
                log.warn("Target library path not found for move operation: libraryId={}, pathId={}", targetLibraryId, targetLibraryPathId);
                return;
            }
            LibraryPathEntity libraryPathEntity = optionalLibraryPathEntity.get();

            currentFilePath = bookEntity.getFullFilePath();
            String pattern = fileMoveHelper.getFileNamingPattern(targetLibrary);
            Path newFilePath = fileMoveHelper.generateNewFilePath(bookEntity, libraryPathEntity, pattern);
            if (currentFilePath.equals(newFilePath)) {
                return;
            }

            tempPath = fileMoveHelper.moveFileWithBackup(currentFilePath);

            String newFileName = newFilePath.getFileName().toString();
            String newFileSubPath = fileMoveHelper.extractSubPath(newFilePath, libraryPathEntity);
            bookRepository.updateFileAndLibrary(bookEntity.getId(), newFileSubPath, newFileName, targetLibrary.getId(), libraryPathEntity);

            fileMoveHelper.commitMove(tempPath, newFilePath);
            tempPath = null;

            Path libraryRoot = Paths.get(bookEntity.getLibraryPath().getPath()).toAbsolutePath().normalize();
            fileMoveHelper.deleteEmptyParentDirsUpToLibraryFolders(currentFilePath.getParent(), Set.of(libraryRoot));

            entityManager.clear();

            BookEntity fresh = bookRepository.findById(bookId).orElseThrow();

            notificationService.sendMessage(Topic.BOOK_UPDATE, bookMapper.toBookWithDescription(fresh, false));

        } catch (Exception e) {
            log.error("Error moving file for book ID {}: {}", bookId, e.getMessage(), e);
        } finally {
            if (tempPath != null && currentFilePath != null) {
                fileMoveHelper.rollbackMove(tempPath, currentFilePath);
            }
        }
    }

    private void processSingleCopy(FileMoveRequest.Move move) {
        Long bookId = move.getBookId();
        Long targetLibraryId = move.getTargetLibraryId();
        Long targetLibraryPathId = move.getTargetLibraryPathId();

        try {
            Optional<BookEntity> optionalBook = bookRepository.findById(bookId);
            Optional<LibraryEntity> optionalLibrary = libraryRepository.findById(targetLibraryId);
            if (optionalBook.isEmpty()) {
                log.warn("Book not found for copy operation: bookId={}", bookId);
                return;
            }
            if (optionalLibrary.isEmpty()) {
                log.warn("Target library not found for copy operation: libraryId={}", targetLibraryId);
                return;
            }
            BookEntity sourceBook = optionalBook.get();
            LibraryEntity targetLibrary = optionalLibrary.get();

            Optional<LibraryPathEntity> optionalLibraryPathEntity = targetLibrary.getLibraryPaths().stream()
                    .filter(libraryPath -> Objects.equals(libraryPath.getId(), targetLibraryPathId))
                    .findFirst();
            if (optionalLibraryPathEntity.isEmpty()) {
                log.warn("Target library path not found for copy operation: libraryId={}, pathId={}", targetLibraryId, targetLibraryPathId);
                return;
            }
            LibraryPathEntity libraryPathEntity = optionalLibraryPathEntity.get();

            String pattern = fileMoveHelper.getFileNamingPattern(targetLibrary);
            Path newFilePath = fileMoveHelper.generateNewFilePath(sourceBook, libraryPathEntity, pattern);
            String newFileName = newFilePath.getFileName().toString();
            String newFileSubPath = fileMoveHelper.extractSubPath(newFilePath, libraryPathEntity);

            fileMoveHelper.copyFile(sourceBook.getFullFilePath(), newFilePath);

            BookEntity newBook = BookEntity.builder()
                    .fileName(newFileName)
                    .fileSubPath(newFileSubPath)
                    .bookType(sourceBook.getBookType())
                    .fileSizeKb(sourceBook.getFileSizeKb())
                    .metadataMatchScore(sourceBook.getMetadataMatchScore())
                    .library(targetLibrary)
                    .libraryPath(libraryPathEntity)
                    .initialHash(sourceBook.getInitialHash())
                    .currentHash(sourceBook.getCurrentHash())
                    .addedOn(Instant.now())
                    .deleted(Boolean.FALSE)
                    .build();

            if (sourceBook.getMetadata() != null) {
                BookMetadataEntity sourceMetadata = sourceBook.getMetadata();
                BookMetadataEntity newMetadata = BookMetadataEntity.builder()
                        .title(sourceMetadata.getTitle())
                        .subtitle(sourceMetadata.getSubtitle())
                        .publisher(sourceMetadata.getPublisher())
                        .publishedDate(sourceMetadata.getPublishedDate())
                        .description(sourceMetadata.getDescription())
                        .seriesName(sourceMetadata.getSeriesName())
                        .seriesNumber(sourceMetadata.getSeriesNumber())
                        .seriesTotal(sourceMetadata.getSeriesTotal())
                        .isbn13(sourceMetadata.getIsbn13())
                        .isbn10(sourceMetadata.getIsbn10())
                        .pageCount(sourceMetadata.getPageCount())
                        .language(sourceMetadata.getLanguage())
                        .rating(sourceMetadata.getRating())
                        .reviewCount(sourceMetadata.getReviewCount())
                        .coverUpdatedOn(sourceMetadata.getCoverUpdatedOn())
                        .amazonRating(sourceMetadata.getAmazonRating())
                        .amazonReviewCount(sourceMetadata.getAmazonReviewCount())
                        .goodreadsRating(sourceMetadata.getGoodreadsRating())
                        .goodreadsReviewCount(sourceMetadata.getGoodreadsReviewCount())
                        .hardcoverRating(sourceMetadata.getHardcoverRating())
                        .hardcoverReviewCount(sourceMetadata.getHardcoverReviewCount())
                        .asin(sourceMetadata.getAsin())
                        .goodreadsId(sourceMetadata.getGoodreadsId())
                        .hardcoverId(sourceMetadata.getHardcoverId())
                        .googleId(sourceMetadata.getGoogleId())
                        .comicvineId(sourceMetadata.getComicvineId())
                        .titleLocked(sourceMetadata.getTitleLocked())
                        .subtitleLocked(sourceMetadata.getSubtitleLocked())
                        .publisherLocked(sourceMetadata.getPublisherLocked())
                        .publishedDateLocked(sourceMetadata.getPublishedDateLocked())
                        .descriptionLocked(sourceMetadata.getDescriptionLocked())
                        .isbn13Locked(sourceMetadata.getIsbn13Locked())
                        .isbn10Locked(sourceMetadata.getIsbn10Locked())
                        .asinLocked(sourceMetadata.getAsinLocked())
                        .pageCountLocked(sourceMetadata.getPageCountLocked())
                        .languageLocked(sourceMetadata.getLanguageLocked())
                        .amazonRatingLocked(sourceMetadata.getAmazonRatingLocked())
                        .amazonReviewCountLocked(sourceMetadata.getAmazonReviewCountLocked())
                        .goodreadsRatingLocked(sourceMetadata.getGoodreadsRatingLocked())
                        .goodreadsReviewCountLocked(sourceMetadata.getGoodreadsReviewCountLocked())
                        .hardcoverRatingLocked(sourceMetadata.getHardcoverRatingLocked())
                        .hardcoverReviewCountLocked(sourceMetadata.getHardcoverReviewCountLocked())
                        .coverLocked(sourceMetadata.getCoverLocked())
                        .seriesNameLocked(sourceMetadata.getSeriesNameLocked())
                        .seriesNumberLocked(sourceMetadata.getSeriesNumberLocked())
                        .seriesTotalLocked(sourceMetadata.getSeriesTotalLocked())
                        .authorsLocked(sourceMetadata.getAuthorsLocked())
                        .categoriesLocked(sourceMetadata.getCategoriesLocked())
                        .moodsLocked(sourceMetadata.getMoodsLocked())
                        .tagsLocked(sourceMetadata.getTagsLocked())
                        .goodreadsIdLocked(sourceMetadata.getGoodreadsIdLocked())
                        .hardcoverIdLocked(sourceMetadata.getHardcoverIdLocked())
                        .googleIdLocked(sourceMetadata.getGoogleIdLocked())
                        .comicvineIdLocked(sourceMetadata.getComicvineIdLocked())
                        .reviewsLocked(sourceMetadata.getReviewsLocked())
                        .build();

                newMetadata.setAuthors(sourceMetadata.getAuthors() == null ? new HashSet<>() : new HashSet<>(sourceMetadata.getAuthors()));
                newMetadata.setCategories(sourceMetadata.getCategories() == null ? new HashSet<>() : new HashSet<>(sourceMetadata.getCategories()));
                newMetadata.setMoods(sourceMetadata.getMoods() == null ? new HashSet<>() : new HashSet<>(sourceMetadata.getMoods()));
                newMetadata.setTags(sourceMetadata.getTags() == null ? new HashSet<>() : new HashSet<>(sourceMetadata.getTags()));
                newMetadata.setBook(newBook);
                newBook.setMetadata(newMetadata);
            }

            BookEntity saved = bookRepository.save(newBook);
            BookEntity fresh = bookRepository.findById(saved.getId()).orElse(saved);
            copyBookImages(sourceBook.getId(), fresh.getId());
            notificationService.sendMessage(Topic.BOOK_ADD, bookMapper.toBookWithDescription(fresh, false));

        } catch (Exception e) {
            log.error("Error copying file for book ID {}: {}", bookId, e.getMessage(), e);
        }
    }

    private void copyBookImages(Long sourceBookId, Long targetBookId) {
        try {
            Path source = Paths.get(fileService.getImagesFolder(sourceBookId));
            if (!Files.exists(source) || !Files.isDirectory(source)) {
                log.info("No images to copy for book {}", sourceBookId);
                return;
            }
            Path target = Paths.get(fileService.getImagesFolder(targetBookId));
            Files.createDirectories(target);

            try (Stream<Path> walk = Files.walk(source)) {
                walk.forEach(path -> {
                    try {
                        Path relative = source.relativize(path);
                        Path dest = target.resolve(relative);
                        if (Files.isDirectory(path)) {
                            Files.createDirectories(dest);
                        } else {
                            Files.copy(path, dest, StandardCopyOption.REPLACE_EXISTING);
                        }
                    } catch (IOException e) {
                        throw new UncheckedIOException(e);
                    }
                });
            }
            log.info("Copied images for book {} to {}", sourceBookId, targetBookId);
        } catch (UncheckedIOException | IOException e) {
            log.warn("Failed to copy images from book {} to {}: {}", sourceBookId, targetBookId, e.getMessage());
        }
    }

    @Transactional
    public FileMoveResult moveSingleFile(BookEntity bookEntity) {

        Long libraryId = bookEntity.getLibraryPath().getLibrary().getId();
        Path libraryRoot = Paths.get(bookEntity.getLibraryPath().getPath()).toAbsolutePath().normalize();
        boolean isLibraryMonitoredWhenCalled = false;

        try {
            isLibraryMonitoredWhenCalled = monitoringRegistrationService.isLibraryMonitored(libraryId);
            String pattern = fileMoveHelper.getFileNamingPattern(bookEntity.getLibraryPath().getLibrary());
            Path currentFilePath = bookEntity.getFullFilePath();
            Path expectedFilePath = fileMoveHelper.generateNewFilePath(bookEntity, bookEntity.getLibraryPath(), pattern);

            if (currentFilePath.equals(expectedFilePath)) {
                return FileMoveResult.builder().moved(false).build();
            }

            log.info("File for book ID {} needs to be moved from {} to {} to match library pattern", bookEntity.getId(), currentFilePath, expectedFilePath);

            if (isLibraryMonitoredWhenCalled) {
                log.debug("Unregistering library {} before moving a single file", libraryId);
                Set<Path> libraryPaths = monitoringRegistrationService.getPathsForLibraries(Set.of(libraryId));
                fileMoveHelper.unregisterLibrary(libraryId);
                monitoringRegistrationService.waitForEventsDrainedByPaths(libraryPaths, EVENT_DRAIN_TIMEOUT_MS);
            }

            fileMoveHelper.moveFile(currentFilePath, expectedFilePath);

            fileMoveHelper.deleteEmptyParentDirsUpToLibraryFolders(currentFilePath.getParent(), Set.of(libraryRoot));

            String newFileName = expectedFilePath.getFileName().toString();
            String newFileSubPath = fileMoveHelper.extractSubPath(expectedFilePath, bookEntity.getLibraryPath());

            return FileMoveResult.builder()
                    .moved(true)
                    .newFileName(newFileName)
                    .newFileSubPath(newFileSubPath)
                    .build();
        } catch (Exception e) {
            log.error("Failed to move file for book ID {}: {}", bookEntity.getId(), e.getMessage(), e);
        } finally {
            if (isLibraryMonitoredWhenCalled) {
                log.debug("Registering library paths for library {} with root {}", libraryId, libraryRoot);
                fileMoveHelper.registerLibraryPaths(libraryId, libraryRoot);
            }
        }

        return FileMoveResult.builder().moved(false).build();
    }
}
