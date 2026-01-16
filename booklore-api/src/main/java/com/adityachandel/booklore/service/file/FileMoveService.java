package com.adityachandel.booklore.service.file;

import com.adityachandel.booklore.mapper.BookMapper;
import com.adityachandel.booklore.mapper.LibraryMapper;
import com.adityachandel.booklore.model.dto.FileMoveResult;
import com.adityachandel.booklore.model.dto.Library;
import com.adityachandel.booklore.model.dto.request.FileMoveRequest;
import com.adityachandel.booklore.model.entity.BookEntity;
import com.adityachandel.booklore.model.entity.LibraryEntity;
import com.adityachandel.booklore.model.entity.LibraryPathEntity;
import com.adityachandel.booklore.model.websocket.Topic;
import com.adityachandel.booklore.repository.BookAdditionalFileRepository;
import com.adityachandel.booklore.repository.BookRepository;
import com.adityachandel.booklore.repository.LibraryRepository;
import com.adityachandel.booklore.service.NotificationService;
import com.adityachandel.booklore.service.monitoring.MonitoringRegistrationService;
import jakarta.persistence.EntityManager;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

@AllArgsConstructor
@Service
@Slf4j
public class FileMoveService {

    private static final long EVENT_DRAIN_TIMEOUT_MS = 300;

    private final BookRepository bookRepository;
    private final BookAdditionalFileRepository bookFileRepository;
    private final LibraryRepository libraryRepository;
    private final FileMoveHelper fileMoveHelper;
    private final MonitoringRegistrationService monitoringRegistrationService;
    private final LibraryMapper libraryMapper;
    private final BookMapper bookMapper;
    private final NotificationService notificationService;
    private final EntityManager entityManager;


    @Transactional
    public void bulkMoveFiles(FileMoveRequest request) {
        List<FileMoveRequest.Move> moves = request.getMoves();
        
        Set<Long> allAffectedLibraryIds = collectAllAffectedLibraryIds(moves);
        Set<Path> libraryPaths = monitoringRegistrationService.getPathsForLibraries(allAffectedLibraryIds);
        
        log.info("Unregistering {} libraries before bulk file move", allAffectedLibraryIds.size());
        monitoringRegistrationService.unregisterLibraries(allAffectedLibraryIds);
        monitoringRegistrationService.waitForEventsDrainedByPaths(libraryPaths, EVENT_DRAIN_TIMEOUT_MS);

        try {
            for (FileMoveRequest.Move move : moves) {
                processSingleMove(move);
            }
            // Ensure any file system events from the moves are drained/ignored while we are still unregistered
            sleep(EVENT_DRAIN_TIMEOUT_MS);
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

        record PlannedMove(Path source, Path temp, Path target) {}

        Map<Long, PlannedMove> plannedMovesByBookFileId = new HashMap<>();
        Set<Path> sourceParentsToCleanup = new HashSet<>();

        try {
            Optional<BookEntity> optionalBook = bookRepository.findByIdWithBookFiles(bookId);
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

            if (bookEntity.getBookFiles() == null || bookEntity.getBookFiles().isEmpty()) {
                log.warn("Book has no files to move: bookId={}", bookId);
                return;
            }

            Path currentPrimaryFilePath = bookEntity.getFullFilePath();
            String pattern = fileMoveHelper.getFileNamingPattern(targetLibrary);
            Path newFilePath = fileMoveHelper.generateNewFilePath(bookEntity, libraryPathEntity, pattern);

            if (currentPrimaryFilePath.equals(newFilePath)) {
                return;
            }

            String newFileSubPath = fileMoveHelper.extractSubPath(newFilePath, libraryPathEntity);
            Path targetParentDir = newFilePath.getParent();

            if (targetParentDir == null) {
                log.warn("Target parent directory could not be determined for move operation: bookId={}", bookId);
                return;
            }

            for (var bookFile : bookEntity.getBookFiles()) {
                Path sourcePath = bookFile.getFullFilePath();
                Path targetPath;
                if (bookFile.isBook()) {
                    targetPath = fileMoveHelper.generateNewFilePath(bookEntity, bookFile, libraryPathEntity, pattern);
                } else {
                    targetPath = targetParentDir.resolve(bookFile.getFileName());
                }

                if (sourcePath.equals(targetPath)) {
                    continue;
                }

                Path tempPath = fileMoveHelper.moveFileWithBackup(sourcePath);
                plannedMovesByBookFileId.put(bookFile.getId(), new PlannedMove(sourcePath, tempPath, targetPath));
                if (sourcePath.getParent() != null) {
                    sourceParentsToCleanup.add(sourcePath.getParent());
                }
            }

            if (plannedMovesByBookFileId.isEmpty()) {
                return;
            }

            for (var bookFile : bookEntity.getBookFiles()) {
                String newFileName;
                if (bookFile.isBook()) {
                    Path targetPath = fileMoveHelper.generateNewFilePath(bookEntity, bookFile, libraryPathEntity, pattern);
                    newFileName = targetPath.getFileName().toString();
                } else {
                    newFileName = bookFile.getFileName();
                }
                bookFileRepository.updateFileNameAndSubPath(bookFile.getId(), newFileName, newFileSubPath);
            }

            bookRepository.updateLibrary(bookEntity.getId(), targetLibrary.getId(), libraryPathEntity);

            for (PlannedMove planned : plannedMovesByBookFileId.values()) {
                fileMoveHelper.commitMove(planned.temp(), planned.target());
            }
            plannedMovesByBookFileId.clear();

            Path libraryRoot = Paths.get(libraryPathEntity.getPath()).toAbsolutePath().normalize();
            for (Path sourceParent : sourceParentsToCleanup) {
                fileMoveHelper.deleteEmptyParentDirsUpToLibraryFolders(sourceParent, Set.of(libraryRoot));
            }

            entityManager.clear();

            BookEntity fresh = bookRepository.findById(bookId).orElseThrow();

            notificationService.sendMessage(Topic.BOOK_UPDATE, bookMapper.toBookWithDescription(fresh, false));

        } catch (Exception e) {
            log.error("Error moving file for book ID {}: {}", bookId, e.getMessage(), e);
        } finally {
            for (PlannedMove planned : plannedMovesByBookFileId.values()) {
                fileMoveHelper.rollbackMove(planned.temp(), planned.source());
            }
        }
    }

    @Transactional
    public FileMoveResult moveSingleFile(BookEntity bookEntity) {

        Long libraryId = bookEntity.getLibraryPath().getLibrary().getId();
        Path libraryRoot = Paths.get(bookEntity.getLibraryPath().getPath()).toAbsolutePath().normalize();
        boolean isLibraryMonitoredWhenCalled = false;

        try {
            Set<Path> existingPaths = monitoringRegistrationService.getPathsForLibraries(Set.of(libraryId));
            isLibraryMonitoredWhenCalled = monitoringRegistrationService.isLibraryMonitored(libraryId) || !existingPaths.isEmpty();

            String pattern = fileMoveHelper.getFileNamingPattern(bookEntity.getLibraryPath().getLibrary());
            Path currentFilePath = bookEntity.getFullFilePath();
            Path expectedFilePath = fileMoveHelper.generateNewFilePath(bookEntity, bookEntity.getLibraryPath(), pattern);

            if (currentFilePath.equals(expectedFilePath)) {
                return FileMoveResult.builder().moved(false).build();
            }

            log.info("File for book ID {} needs to be moved from {} to {} to match library pattern", bookEntity.getId(), currentFilePath, expectedFilePath);

            if (isLibraryMonitoredWhenCalled) {
                log.debug("Unregistering library {} before moving a single file", libraryId);
                fileMoveHelper.unregisterLibrary(libraryId);
                monitoringRegistrationService.waitForEventsDrainedByPaths(existingPaths, EVENT_DRAIN_TIMEOUT_MS);
            }

            fileMoveHelper.moveFile(currentFilePath, expectedFilePath);

            fileMoveHelper.deleteEmptyParentDirsUpToLibraryFolders(currentFilePath.getParent(), Set.of(libraryRoot));

            if (isLibraryMonitoredWhenCalled) {
                // Ensure any file system events from the move and cleanup are drained/ignored while we are still unregistered
                sleep(EVENT_DRAIN_TIMEOUT_MS);
            }

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
                LibraryEntity libraryEntity = bookEntity.getLibraryPath().getLibrary();
                Library library = libraryMapper.toLibrary(libraryEntity);
                library.setWatch(true);
                monitoringRegistrationService.registerLibrary(library);
            }
        }

        return FileMoveResult.builder().moved(false).build();
    }

    protected void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Sleep interrupted", e);
        }
    }
}
