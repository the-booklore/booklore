package com.adityachandel.booklore.service.monitoring;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;

@Slf4j
@Service
@AllArgsConstructor
public class MonitoringRegistrationService {

    private final MonitoringService monitoringService;


    /**
     * Checks if a specific path is currently being monitored.
     *
     * @param path the path to check
     * @return true if the path is monitored
     */
    public boolean isPathMonitored(Path path) {
        return monitoringService.isPathMonitored(path);
    }

    /**
     * Unregisters a specific path from monitoring without affecting other paths.
     * This is more efficient than pausing all monitoring for single path operations.
     *
     * @param path the path to unregister
     */
    public void unregisterSpecificPath(Path path) {
        monitoringService.unregisterPath(path);
    }

    /**
     * Registers a specific path for monitoring.
     *
     * @param path      the path to register
     * @param libraryId the library ID associated with this path
     */
    public void registerSpecificPath(Path path, Long libraryId) {
        monitoringService.registerPath(path, libraryId);
    }

    /**
     * Unregisters an entire library from monitoring.
     * This is more efficient for batch operations than unregistering individual paths.
     *
     * @param libraryId the library ID to unregister
     */
    public void unregisterLibrary(Long libraryId) {
        monitoringService.unregisterLibrary(libraryId);
    }

    /**
     * Re-registers an entire library for monitoring after batch operations.
     * Since MonitoringService.registerLibrary() requires a Library object,
     * this method will register individual paths under the library instead.
     *
     * @param libraryId   the library ID to register
     * @param libraryRoot the root path of the library
     */
    public void registerLibraryPaths(Long libraryId, Path libraryRoot) {
        if (!Files.exists(libraryRoot) || !Files.isDirectory(libraryRoot)) {
            return;
        }
        try {
            monitoringService.registerPath(libraryRoot, libraryId);
            try (var stream = Files.walk(libraryRoot)) {
                stream.filter(Files::isDirectory)
                        .filter(path -> !path.equals(libraryRoot))
                        .forEach(path -> monitoringService.registerPath(path, libraryId));
            }
        } catch (Exception e) {
            log.error("Failed to register library paths for libraryId {} at {}", libraryId, libraryRoot, e);
        }
    }
}