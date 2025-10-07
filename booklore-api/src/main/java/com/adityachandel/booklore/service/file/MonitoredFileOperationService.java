package com.adityachandel.booklore.service.file;

import com.adityachandel.booklore.service.monitoring.MonitoringRegistrationService;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Stream;

/**
 * Service responsible for executing file operations while temporarily disabling monitoring
 * on specific paths to prevent event loops and race conditions during file system operations.
 *
 * This service provides targeted path protection by unregistering only the directories
 * involved in the operation rather than pausing all monitoring, ensuring minimal
 * disruption to the monitoring system.
 */
@Slf4j
@Component
@AllArgsConstructor
public class MonitoredFileOperationService {

    private final MonitoringRegistrationService monitoringRegistrationService;

    /**
     * Executes a file operation with targeted path protection to prevent monitoring conflicts.
     *
     * This method temporarily unregisters monitoring for only the specific directories involved
     * in the operation (source and target) rather than pausing all monitoring. This approach
     * minimizes the impact on the monitoring system while preventing event loops that could
     * occur when the monitoring service detects changes from our own file operations.
     *
     * The process follows these steps:
     * 1. Identify source and target directories
     * 2. Temporarily unregister monitoring for these specific paths
     * 3. Execute the file operation
     * 4. Wait for filesystem operations to settle
     * 5. Re-register paths and scan for new directory structures
     *
     * @param sourcePath the source file path for the operation
     * @param targetPath the target file path for the operation
     * @param libraryId  the library ID used for re-registration of monitoring
     * @param operation  the file operation to execute (supplied as a lambda)
     * @param <T>        the return type of the operation
     */
    public <T> void executeWithMonitoringSuspended(Path sourcePath, Path targetPath, Long libraryId, Supplier<T> operation) {
        // Extract parent directories since we monitor directories, not individual files
        Path sourceDir = sourcePath.getParent();
        Path targetDir = targetPath.getParent();

        // Track which paths we unregister so we can restore them later
        Set<Path> unregisteredPaths = new HashSet<>();

        try {
            // Unregister source directory to prevent detection of file removal events
            if (monitoringRegistrationService.isPathMonitored(sourceDir)) {
                monitoringRegistrationService.unregisterSpecificPath(sourceDir);
                unregisteredPaths.add(sourceDir);
                log.debug("Temporarily unregistered source directory to prevent monitoring conflicts: {}", sourceDir);
            }

            // Unregister target directory if it's different from source and already exists
            // This prevents detection of file creation events during the operation
            if (!sourceDir.equals(targetDir) && Files.exists(targetDir) && monitoringRegistrationService.isPathMonitored(targetDir)) {
                monitoringRegistrationService.unregisterSpecificPath(targetDir);
                unregisteredPaths.add(targetDir);
                log.debug("Temporarily unregistered target directory to prevent monitoring conflicts: {}", targetDir);
            }

            log.debug("Protected {} directory paths from monitoring during file operation", unregisteredPaths.size());

            // Execute the actual file operation (move, copy, delete, etc.)
            T result = operation.get();

            // Allow filesystem operations to complete and settle before re-enabling monitoring
            // This prevents race conditions where monitoring might restart before the operation is fully complete
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.warn("Thread interrupted while waiting for filesystem operations to settle");
            }

        } finally {
            // Always restore monitoring, even if the operation failed
            reregisterPathsAfterMove(unregisteredPaths, libraryId, targetDir);
        }
    }

    /**
     * Restores monitoring registration for paths after a file operation and discovers new directories.
     *
     * This method handles the cleanup phase by:
     * 1. Re-registering previously monitored paths that still exist
     * 2. Registering new directory structures created by the operation
     * 3. Ensuring comprehensive monitoring coverage after the operation
     *
     * @param unregisteredPaths the set of paths that were temporarily unregistered
     * @param libraryId the library ID to use for new registrations
     * @param targetDir the target directory where new structures might have been created
     */
    private void reregisterPathsAfterMove(Set<Path> unregisteredPaths, Long libraryId, Path targetDir) {
        // Restore monitoring for original paths that still exist after the operation
        for (Path path : unregisteredPaths) {
            if (Files.exists(path) && Files.isDirectory(path)) {
                monitoringRegistrationService.registerSpecificPath(path, libraryId);
                log.debug("Restored monitoring for existing path: {}", path);
            } else {
                log.info("Path no longer exists after operation, skipping re-registration: {}", path);
            }
        }

        // Register monitoring for new directory structures created at the target location
        if (Files.exists(targetDir) && Files.isDirectory(targetDir)) {
            // Register the target directory itself if it wasn't previously monitored
            if (!monitoringRegistrationService.isPathMonitored(targetDir)) {
                monitoringRegistrationService.registerSpecificPath(targetDir, libraryId);
                log.debug("Registered new target directory for monitoring: {}", targetDir);
            }

            // Discover and register any new subdirectories created during the operation
            // This ensures complete monitoring coverage for complex directory structures
            try (Stream<Path> stream = Files.walk(targetDir)) {
                stream.filter(Files::isDirectory)
                        .filter(Files::exists)
                        .filter(path -> !path.equals(targetDir)) // Skip the parent directory itself
                        .forEach(path -> {
                            if (!monitoringRegistrationService.isPathMonitored(path)) {
                                monitoringRegistrationService.registerSpecificPath(path, libraryId);
                                log.info("Registered new subdirectory for monitoring: {}", path);
                            }
                        });
            } catch (IOException e) {
                log.warn("Failed to scan and register new subdirectories at: {}", targetDir, e);
            }
        }

        log.debug("Completed restoration of monitoring after file operation");
    }
}

