package com.adityachandel.booklore.service.monitoring;

import com.adityachandel.booklore.model.dto.Library;
import com.adityachandel.booklore.model.enums.BookFileExtension;
import com.adityachandel.booklore.service.watcher.LibraryFileEventProcessor;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.dao.InvalidDataAccessApiUsageException;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Slf4j
@Service
public class MonitoringService {

    private final LibraryFileEventProcessor libraryFileEventProcessor;
    private final WatchService watchService;
    private final MonitoringTask monitoringTask;

    private final BlockingQueue<FileChangeEvent> eventQueue = new LinkedBlockingQueue<>();
    private final ExecutorService singleThreadExecutor = Executors.newSingleThreadExecutor();

    private final Set<Path> monitoredPaths = ConcurrentHashMap.newKeySet();
    private final Map<Path, WatchKey> registeredWatchKeys = new ConcurrentHashMap<>();
    private final Map<Path, Long> pathToLibraryIdMap = new ConcurrentHashMap<>();
    private final Map<Long, Boolean> libraryWatchStatusMap = new ConcurrentHashMap<>();
    private final Map<Long, List<Path>> libraryIdToPaths = new ConcurrentHashMap<>();

    public MonitoringService(LibraryFileEventProcessor libraryFileEventProcessor, WatchService watchService, MonitoringTask monitoringTask) {
        this.libraryFileEventProcessor = libraryFileEventProcessor;
        this.watchService = watchService;
        this.monitoringTask = monitoringTask;
    }

    @PostConstruct
    public void initializeMonitoring() {
        monitoringTask.monitor();
        startProcessingThread();
    }

    @PreDestroy
    public void stopMonitoring() {
        log.info("Shutting down monitoring service...");
        singleThreadExecutor.shutdownNow();
        try {
            watchService.close();
        } catch (IOException e) {
            log.error("Failed to close WatchService", e);
        }
    }

    public void registerLibraries(List<Library> libraries) {
        libraries.forEach(lib -> libraryWatchStatusMap.put(lib.getId(), lib.isWatch()));
        libraries.stream().filter(Library::isWatch).forEach(this::registerLibrary);
        log.info("Registered {} libraries for recursive monitoring", libraries.size());
    }

    public void registerLibrary(Library library) {
        libraryWatchStatusMap.put(library.getId(), library.isWatch());
        if (!library.isWatch()) return;

        List<Path> registeredPaths = new ArrayList<>();
        int[] registeredCount = {0};

        library.getPaths().forEach(libraryPath -> {
            Path rootPath = Paths.get(libraryPath.getPath());
            if (Files.isDirectory(rootPath)) {
                try (Stream<Path> pathStream = Files.walk(rootPath)) {
                    pathStream.filter(Files::isDirectory).forEach(path -> {
                        if (registerPath(path, library.getId())) {
                            registeredCount[0]++;
                            registeredPaths.add(path);
                        }
                    });
                } catch (IOException e) {
                    log.error("Failed to register paths for library '{}': {}", library.getName(), e.getMessage(), e);
                }
            }
        });

        libraryIdToPaths.put(library.getId(), registeredPaths);
        log.info("Registered {} folders for library '{}'", registeredCount[0], library.getName());
    }

    public void unregisterLibrary(Long libraryId) {
        Set<Path> pathsToRemove = pathToLibraryIdMap.entrySet().stream()
                .filter(entry -> entry.getValue().equals(libraryId))
                .map(Map.Entry::getKey)
                .collect(Collectors.toSet());

        for (Path path : pathsToRemove) {
            unregisterPath(path);
        }

        libraryWatchStatusMap.put(libraryId, false);
        libraryIdToPaths.remove(libraryId);
        log.debug("Unregistered library {} from monitoring", libraryId);
    }

    public synchronized boolean registerPath(Path path, Long libraryId) {
        try {
            if (monitoredPaths.add(path)) {
                WatchKey key = path.register(watchService,
                        StandardWatchEventKinds.ENTRY_CREATE,
                        StandardWatchEventKinds.ENTRY_MODIFY,
                        StandardWatchEventKinds.ENTRY_DELETE);
                registeredWatchKeys.put(path, key);
                pathToLibraryIdMap.put(path, libraryId);
                return true;
            }
        } catch (IOException e) {
            log.error("Error registering path: {}", path, e);
        }
        return false;
    }

    public synchronized void unregisterPath(Path path) {
        unregisterPath(path, true);
    }

    public synchronized void unregisterPath(Path path, boolean logUnregister) {
        if (monitoredPaths.remove(path)) {
            WatchKey key = registeredWatchKeys.remove(path);
            if (key != null) key.cancel();
            pathToLibraryIdMap.remove(path);
            if (logUnregister) {
                log.debug("Unregistered path: {}", path);
            }
        }
    }

    private void unregisterSubPaths(Path deletedPath) {
        Set<Path> toRemove = monitoredPaths.stream()
                .filter(p -> p.startsWith(deletedPath))
                .collect(Collectors.toSet());

        for (Path path : toRemove) {
            unregisterPath(path);
        }
    }

    @EventListener
    public void handleFileChangeEvent(FileChangeEvent event) {
        Path fullPath = event.getFilePath();
        WatchEvent.Kind<?> kind = event.getEventKind();

        if (kind != StandardWatchEventKinds.ENTRY_CREATE && kind != StandardWatchEventKinds.ENTRY_DELETE) return;

        boolean isDir = kind == StandardWatchEventKinds.ENTRY_CREATE
                ? Files.isDirectory(fullPath)
                : monitoredPaths.contains(fullPath);

        boolean isRelevantFile = isRelevantBookFile(fullPath);
        if (!(isDir || isRelevantFile)) return;

        handleDirectoryEvents(event, fullPath, kind, isDir);
        queueEvent(event, fullPath, kind);
    }

    @EventListener
    public void handleWatchKeyInvalidation(WatchKeyInvalidatedEvent event) {
        Path invalidPath = event.getInvalidPath();
        if (monitoredPaths.remove(invalidPath)) {
            log.warn("Removing invalid path from monitoring: {}", invalidPath);
            pathToLibraryIdMap.remove(invalidPath);
            WatchKey key = registeredWatchKeys.remove(invalidPath);
            if (key != null) key.cancel();
        }
    }

    private void startProcessingThread() {
        log.info("Starting file change processor...");
        singleThreadExecutor.submit(() -> {
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    FileChangeEvent event = eventQueue.take();
                    processFileChangeEvent(event);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                } catch (Exception e) {
                    log.error("Error in processing thread", e);
                }
            }
        });
    }

    private void processFileChangeEvent(FileChangeEvent event) {
        Path filePath = event.getFilePath();
        Path watchedFolder = event.getWatchedFolder();
        Long libraryId = pathToLibraryIdMap.get(watchedFolder);

        if (libraryId != null) {
            try {
                libraryFileEventProcessor.processFile(event.getEventKind(), libraryId, watchedFolder.toString(), filePath.toString());
            } catch (InvalidDataAccessApiUsageException e) {
                log.debug("InvalidDataAccessApiUsageException for libraryId={}", libraryId);
            }
        } else {
            log.warn("No library ID found for folder: {}", watchedFolder);
        }
    }

    private void handleDirectoryEvents(FileChangeEvent event, Path fullPath, WatchEvent.Kind<?> kind, boolean isDir) {
        if (isDir && kind == StandardWatchEventKinds.ENTRY_CREATE) {
            Long parentLibraryId = pathToLibraryIdMap.get(event.getWatchedFolder());
            if (parentLibraryId != null) {
                try (Stream<Path> stream = Files.walk(fullPath)) {
                    stream.filter(Files::isDirectory).forEach(path -> registerPath(path, parentLibraryId));
                } catch (IOException e) {
                    log.warn("Failed to register nested paths: {}", fullPath, e);
                }
            }
        }

        if (isDir && kind == StandardWatchEventKinds.ENTRY_DELETE) {
            unregisterSubPaths(fullPath);
        }
    }

    private void queueEvent(FileChangeEvent event, Path fullPath, WatchEvent.Kind<?> kind) {
        if (!eventQueue.offer(event)) {
            log.warn("Event queue full, dropping: {}", fullPath);
        } else {
            log.debug("Queued: {} [{}]", fullPath, kind.name());
        }
    }

    public boolean isRelevantBookFile(Path path) {
        return BookFileExtension.fromFileName(path.getFileName().toString()).isPresent();
    }

    public boolean isPathMonitored(Path path) {
        return monitoredPaths.contains(path.toAbsolutePath().normalize());
    }
}
