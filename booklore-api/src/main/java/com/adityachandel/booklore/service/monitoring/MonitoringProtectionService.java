package com.adityachandel.booklore.service.monitoring;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.function.Supplier;

/**
 * Thread-safe service for managing monitoring protection during file operations.
 * 
 * This service prevents race conditions where file operations are detected as 
 * "missing files" by the monitoring system, which could lead to data loss.
 * 
 * The service ensures:
 * - Thread-safe pause/resume operations
 * - Monitoring always resumes even on exceptions
 * - Protection against concurrent operations interfering with each other
 */
@Slf4j
@Service
@AllArgsConstructor
public class MonitoringProtectionService {

    private final MonitoringService monitoringService;
    
    // Synchronization lock to prevent race conditions in pause/resume logic
    private static final Object monitoringLock = new Object();

    /**
     * Executes an operation with monitoring protection.
     * 
     * @param operation The operation to execute while monitoring is paused
     * @param operationName Name for logging purposes
     * @param <T> Return type of the operation
     * @return Result of the operation
     */
    public <T> T executeWithProtection(Supplier<T> operation, String operationName) {
        boolean didPause = pauseMonitoringSafely();
        
        try {
            log.debug("Executing {} with monitoring protection (paused: {})", operationName, didPause);
            return operation.get();
        } finally {
            resumeMonitoringSafely(didPause, operationName);
        }
    }

    /**
     * Executes a void operation with monitoring protection.
     * 
     * @param operation The operation to execute while monitoring is paused
     * @param operationName Name for logging purposes
     */
    public void executeWithProtection(Runnable operation, String operationName) {
        executeWithProtection(() -> {
            operation.run();
            return null;
        }, operationName);
    }

    /**
     * Thread-safe pause of monitoring service.
     * 
     * @return true if monitoring was paused by this call, false if already paused
     */
    private boolean pauseMonitoringSafely() {
        synchronized (monitoringLock) {
            if (!monitoringService.isPaused()) {
                monitoringService.pauseMonitoring();
                log.debug("Monitoring paused for file operations");
                return true;
            }
            log.debug("Monitoring already paused by another operation");
            return false;
        }
    }

    /**
     * Thread-safe resume of monitoring service with a 5-second delay.
     * The delay is critical to prevent race conditions where file operations
     * are still settling when monitoring resumes.
     * 
     * @param didPause true if this operation paused monitoring
     * @param operationName name of the operation for logging
     */
    private void resumeMonitoringSafely(boolean didPause, String operationName) {
        if (!didPause) {
            log.debug("Monitoring was not paused by {} - no resume needed", operationName);
            return;
        }
        
        // Use virtual thread for delayed resume to avoid blocking
        Thread.startVirtualThread(() -> {
            try {
                Thread.sleep(5_000); // Critical 5-second delay for filesystem operations to settle
                
                synchronized (monitoringLock) {
                    // Double-check that monitoring is still paused before resuming
                    if (monitoringService.isPaused()) {
                        monitoringService.resumeMonitoring();
                        log.debug("Monitoring resumed after {} completed with 5s delay", operationName);
                    } else {
                        log.warn("Monitoring was already resumed by another thread during {}", operationName);
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.warn("Interrupted while delaying resume of monitoring after {}", operationName);
            }
        });
    }

    /**
     * Checks if monitoring is currently paused.
     * 
     * @return true if monitoring is paused
     */
    public boolean isMonitoringPaused() {
        synchronized (monitoringLock) {
            return monitoringService.isPaused();
        }
    }
}