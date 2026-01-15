package com.adityachandel.booklore.service.bookdrop;

import com.adityachandel.booklore.model.dto.BookMetadata;
import com.adityachandel.booklore.model.dto.request.BookdropFinalizeRequest;
import com.adityachandel.booklore.model.entity.BookdropFileEntity;
import com.adityachandel.booklore.model.entity.LibraryEntity;
import com.adityachandel.booklore.model.entity.LibraryPathEntity;
import com.adityachandel.booklore.model.enums.PermissionType;
import com.adityachandel.booklore.model.websocket.LogNotification;
import com.adityachandel.booklore.model.websocket.Topic;
import com.adityachandel.booklore.repository.BookdropFileRepository;
import com.adityachandel.booklore.repository.LibraryRepository;
import com.adityachandel.booklore.service.NotificationService;
import com.adityachandel.booklore.service.appsettings.AppSettingService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;

/**
 * Service responsible for automatically importing books from the bookdrop folder
 * when metadata is successfully found from external sources.
 *
 * Auto-import is triggered when:
 * 1. The auto-import setting is enabled
 * 2. Fetched metadata contains a valid title and at least one author
 * 3. A default library is available
 *
 * Books that don't meet these criteria remain in PENDING_REVIEW status for manual review.
 */
@Slf4j
@Service
public class BookdropAutoImportService {

    private final AppSettingService appSettingService;
    private final BookDropService bookDropService;
    private final LibraryRepository libraryRepository;
    private final BookdropFileRepository bookdropFileRepository;
    private final NotificationService notificationService;
    private final ObjectMapper objectMapper;

    public BookdropAutoImportService(
            AppSettingService appSettingService,
            @Lazy BookDropService bookDropService,
            LibraryRepository libraryRepository,
            BookdropFileRepository bookdropFileRepository,
            NotificationService notificationService,
            ObjectMapper objectMapper) {
        this.appSettingService = appSettingService;
        this.bookDropService = bookDropService;
        this.libraryRepository = libraryRepository;
        this.bookdropFileRepository = bookdropFileRepository;
        this.notificationService = notificationService;
        this.objectMapper = objectMapper;
    }

    /**
     * Attempts to auto-import a bookdrop file if conditions are met.
     *
     * @param bookdropFileId The ID of the bookdrop file entity to potentially auto-import
     * @return true if the file was auto-imported, false if it remains for manual review
     */
    @Transactional
    public boolean attemptAutoImport(Long bookdropFileId) {
        // Check if auto-import is enabled
        if (!appSettingService.getAppSettings().isAutoImportEnabled()) {
            log.debug("Auto-import is disabled, skipping auto-import for bookdrop file id={}", bookdropFileId);
            return false;
        }

        // Fetch the entity with latest metadata
        BookdropFileEntity entity = bookdropFileRepository.findById(bookdropFileId).orElse(null);
        if (entity == null) {
            log.warn("Bookdrop file not found for auto-import: id={}", bookdropFileId);
            return false;
        }

        // Check if metadata is sufficient for auto-import
        BookMetadata fetchedMetadata = parseFetchedMetadata(entity);
        if (!hasValidMetadata(fetchedMetadata)) {
            log.info("Auto-import skipped for '{}': insufficient metadata (title or authors missing)",
                    entity.getFileName());
            return false;
        }

        // Get default library
        LibraryEntity targetLibrary = getDefaultLibrary();
        if (targetLibrary == null) {
            log.warn("Auto-import skipped for '{}': no library available", entity.getFileName());
            return false;
        }

        // Get default path from the library
        LibraryPathEntity targetPath = getDefaultPath(targetLibrary);
        if (targetPath == null) {
            log.warn("Auto-import skipped for '{}': no library path available in library '{}'",
                    entity.getFileName(), targetLibrary.getName());
            return false;
        }

        // Perform auto-import
        try {
            log.info("Auto-importing '{}' to library '{}' (path: {})",
                    entity.getFileName(), targetLibrary.getName(), targetPath.getPath());

            BookdropFinalizeRequest request = buildAutoImportRequest(
                    entity, targetLibrary.getId(), targetPath.getId(), fetchedMetadata);

            bookDropService.finalizeImport(request);

            notificationService.sendMessageToPermissions(
                    Topic.LOG,
                    LogNotification.info("Auto-imported: " + entity.getFileName() + " to " + targetLibrary.getName()),
                    Set.of(PermissionType.ADMIN, PermissionType.MANAGE_LIBRARY)
            );

            log.info("Successfully auto-imported '{}' to library '{}'",
                    entity.getFileName(), targetLibrary.getName());
            return true;

        } catch (Exception e) {
            log.error("Auto-import failed for '{}': {}", entity.getFileName(), e.getMessage(), e);
            notificationService.sendMessageToPermissions(
                    Topic.LOG,
                    LogNotification.warn("Auto-import failed for " + entity.getFileName() + ": " + e.getMessage()),
                    Set.of(PermissionType.ADMIN, PermissionType.MANAGE_LIBRARY)
            );
            return false;
        }
    }

    /**
     * Parses the fetched metadata JSON from the entity.
     */
    private BookMetadata parseFetchedMetadata(BookdropFileEntity entity) {
        if (entity.getFetchedMetadata() == null || entity.getFetchedMetadata().isBlank()) {
            return null;
        }

        try {
            return objectMapper.readValue(entity.getFetchedMetadata(), BookMetadata.class);
        } catch (Exception e) {
            log.warn("Failed to parse fetched metadata for bookdrop file id={}: {}",
                    entity.getId(), e.getMessage());
            return null;
        }
    }

    /**
     * Checks if the metadata is valid for auto-import.
     * Requires at least a non-blank title and at least one author.
     */
    private boolean hasValidMetadata(BookMetadata metadata) {
        if (metadata == null) {
            return false;
        }

        boolean hasTitle = metadata.getTitle() != null && !metadata.getTitle().isBlank();
        boolean hasAuthors = metadata.getAuthors() != null && !metadata.getAuthors().isEmpty();

        return hasTitle && hasAuthors;
    }

    /**
     * Gets the first available library as the default target for auto-import.
     */
    private LibraryEntity getDefaultLibrary() {
        List<LibraryEntity> libraries = libraryRepository.findAll();
        return libraries.stream()
                .findFirst()
                .orElse(null);
    }

    /**
     * Gets the first path from the library as the default target path.
     */
    private LibraryPathEntity getDefaultPath(LibraryEntity library) {
        if (library.getLibraryPaths() == null || library.getLibraryPaths().isEmpty()) {
            return null;
        }
        return library.getLibraryPaths().stream().findFirst().orElse(null);
    }

    /**
     * Builds a finalize request for auto-import.
     */
    private BookdropFinalizeRequest buildAutoImportRequest(
            BookdropFileEntity entity,
            Long libraryId,
            Long pathId,
            BookMetadata metadata) {

        BookdropFinalizeRequest.BookdropFinalizeFile file = new BookdropFinalizeRequest.BookdropFinalizeFile();
        file.setFileId(entity.getId());
        file.setLibraryId(libraryId);
        file.setPathId(pathId);
        file.setMetadata(metadata);

        BookdropFinalizeRequest request = new BookdropFinalizeRequest();
        request.setSelectAll(false);
        request.setFiles(List.of(file));
        request.setDefaultLibraryId(libraryId);
        request.setDefaultPathId(pathId);

        return request;
    }
}
