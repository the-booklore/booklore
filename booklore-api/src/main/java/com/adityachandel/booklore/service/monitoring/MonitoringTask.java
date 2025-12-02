package com.adityachandel.booklore.service.monitoring;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.nio.file.*;

@Slf4j
@Service
@AllArgsConstructor
public class MonitoringTask {

    private final WatchService watchService;
    private final ApplicationEventPublisher eventPublisher;

    @Async
    public void monitor() {
        try {
            while (!Thread.currentThread().isInterrupted()) {
                WatchKey key;
                try {
                    key = watchService.take();
                } catch (ClosedWatchServiceException e) {
                    log.warn("WatchService has been closed. Stopping monitoring.");
                    break;
                }

                if (key == null) continue;

                Path directory = (Path) key.watchable();

                for (WatchEvent<?> event : key.pollEvents()) {
                    WatchEvent.Kind<?> kind = event.kind();
                    if (kind == StandardWatchEventKinds.OVERFLOW) continue;

                    Path fileName = (Path) event.context();
                    Path fullPath = directory.resolve(fileName);
                    eventPublisher.publishEvent(new FileChangeEvent(this, fullPath, kind, directory));
                }

                boolean valid = key.reset();
                if (!valid) {
                    log.warn("WatchKey is no longer valid: {}", directory);
                    // Clean up but DO NOT break
                    eventPublisher.publishEvent(new WatchKeyInvalidatedEvent(this, directory));
                }
            }
        } catch (InterruptedException e) {
            log.warn("Monitoring task interrupted", e);
            Thread.currentThread().interrupt();
        }
    }
}