package com.adityachandel.booklore.service.library;

import com.adityachandel.booklore.exception.ApiError;
import com.adityachandel.booklore.model.MetadataUpdateContext;
import com.adityachandel.booklore.model.MetadataUpdateWrapper;
import com.adityachandel.booklore.model.dto.BookMetadata;
import com.adityachandel.booklore.model.entity.BookEntity;
import com.adityachandel.booklore.model.entity.LibraryEntity;
import com.adityachandel.booklore.model.websocket.TaskProgressPayload;
import com.adityachandel.booklore.model.websocket.Topic;
import com.adityachandel.booklore.repository.LibraryRepository;
import com.adityachandel.booklore.service.NotificationService;
import com.adityachandel.booklore.service.metadata.BookMetadataUpdater;
import com.adityachandel.booklore.service.metadata.extractor.MetadataExtractorFactory;
import com.adityachandel.booklore.task.options.RescanLibraryContext;
import com.adityachandel.booklore.task.TaskCancellationManager;
import com.adityachandel.booklore.task.TaskStatus;
import com.adityachandel.booklore.model.enums.TaskType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
public class LibraryRescanHelper {

    private final LibraryRepository libraryRepository;
    private final MetadataExtractorFactory metadataExtractorFactory;
    private final BookMetadataUpdater bookMetadataUpdater;
    private final NotificationService notificationService;
    private final TaskCancellationManager cancellationManager;

    public LibraryRescanHelper(LibraryRepository libraryRepository, MetadataExtractorFactory metadataExtractorFactory, @Lazy BookMetadataUpdater bookMetadataUpdater, NotificationService notificationService, TaskCancellationManager cancellationManager) {
        this.libraryRepository = libraryRepository;
        this.metadataExtractorFactory = metadataExtractorFactory;
        this.bookMetadataUpdater = bookMetadataUpdater;
        this.notificationService = notificationService;
        this.cancellationManager = cancellationManager;
    }

    @Transactional
    public void handleRescanOptions(RescanLibraryContext context, String taskId) {

        LibraryEntity library = libraryRepository.findById(context.getLibraryId()).orElseThrow(() -> ApiError.LIBRARY_NOT_FOUND.createException(context.getLibraryId()));

        List<BookEntity> bookEntities = library.getBookEntities().stream()
                .filter(b -> b != null && (b.getDeleted() == null || !b.getDeleted()))
                .toList();

        log.info("Found {} book(s) to process in library id={}", bookEntities.size(), library.getId());

        int totalBooks = bookEntities.size();
        int processedBooks = 0;

        sendTaskProgressNotification(taskId, 0, String.format("Starting rescan for library: %s", library.getName()), TaskStatus.IN_PROGRESS);

        for (BookEntity bookEntity : bookEntities) {
            if (taskId != null && cancellationManager.isTaskCancelled(taskId)) {
                log.info("Library rescan for library {} was cancelled", library.getId());
                sendTaskProgressNotification(taskId, (processedBooks * 100) / totalBooks,
                        String.format("Rescan cancelled for library: %s (%d/%d books processed)", library.getName(), processedBooks, totalBooks),
                        TaskStatus.CANCELLED);
                break;
            }

            log.info("Processing book: library={}, bookId={}, fileName={}", library.getName(), bookEntity.getId(), bookEntity.getFileName());

            int progressPercentage = totalBooks > 0 ? (processedBooks * 100) / totalBooks : 0;

            sendTaskProgressNotification(taskId, progressPercentage,
                    String.format("Processing: %s (Library: %s)", bookEntity.getFileName(), library.getName()),
                    TaskStatus.IN_PROGRESS);

            try {
                BookMetadata bookMetadata = metadataExtractorFactory.extractMetadata(bookEntity.getBookType(), bookEntity.getFullFilePath().toFile());
                if (bookMetadata == null) {
                    log.warn("No metadata extracted for book id={} path={}", bookEntity.getId(), bookEntity.getFullFilePath());
                    continue;
                }
                MetadataUpdateContext metadataUpdateContext = MetadataUpdateContext.builder()
                        .bookEntity(bookEntity)
                        .metadataUpdateWrapper(
                                MetadataUpdateWrapper.builder()
                                        .metadata(bookMetadata)
                                        .build()
                        )
                        .replaceMode(context.getOptions().getMetadataReplaceMode())
                        .updateThumbnail(false)
                        .mergeCategories(false)
                        .build();
                bookMetadataUpdater.setBookMetadata(metadataUpdateContext);
            } catch (Exception e) {
                log.error("Failed to update metadata for book id={} path={}: {}", bookEntity.getId(), bookEntity.getFullFilePath(), e.getMessage(), e);
            } finally {
                processedBooks++;
            }
        }

        if (taskId == null || !cancellationManager.isTaskCancelled(taskId)) {
            sendTaskProgressNotification(taskId, 100,
                    String.format("Rescan completed for library: %s (%d books processed)", library.getName(), processedBooks),
                    TaskStatus.COMPLETED);
        }
    }

    private void sendTaskProgressNotification(String taskId, int progress, String message, TaskStatus taskStatus) {
        try {
            TaskProgressPayload payload = TaskProgressPayload.builder()
                    .taskId(taskId)
                    .taskType(TaskType.REFRESH_LIBRARY_METADATA)
                    .message(message)
                    .progress(progress)
                    .taskStatus(taskStatus)
                    .build();

            notificationService.sendMessage(Topic.TASK_PROGRESS, payload);
        } catch (Exception e) {
            log.error("Failed to send task progress notification for taskId={}: {}", taskId, e.getMessage(), e);
        }
    }
}