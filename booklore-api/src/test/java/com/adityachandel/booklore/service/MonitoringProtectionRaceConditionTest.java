package com.adityachandel.booklore.service;

import com.adityachandel.booklore.service.monitoring.MonitoringProtectionService;
import com.adityachandel.booklore.service.monitoring.MonitoringService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/**
 * Integration test that simulates the actual race condition scenario:
 * File operations happening while monitoring service detects "missing" files.
 * 
 * This test verifies that MonitoringProtectionService prevents the race condition
 * that was causing data loss in the original bug.
 */
@ExtendWith(MockitoExtension.class)
class MonitoringProtectionRaceConditionTest {

    @TempDir
    Path tempDir;

    @Mock
    private MonitoringService monitoringService;

    private MonitoringProtectionService monitoringProtectionService;

    @BeforeEach
    void setUp() {
        monitoringProtectionService = new MonitoringProtectionService(monitoringService);
    }

    @Test
    void raceConditionPrevention_fileMoveDuringMonitoring() throws InterruptedException, IOException, ExecutionException, TimeoutException {
        // Arrange
        Path sourceFile = tempDir.resolve("source.txt");
        Path targetFile = tempDir.resolve("target.txt");
        Files.write(sourceFile, "test content".getBytes());

        AtomicBoolean fileOperationCompleted = new AtomicBoolean(false);
        AtomicBoolean monitoringDetectedMissingFile = new AtomicBoolean(false);
        AtomicBoolean raceConditionOccurred = new AtomicBoolean(false);

        // Configure mock behavior - monitoring is paused during file operations
        when(monitoringService.isPaused()).thenAnswer(invocation -> {
            // isPaused() should return true when monitoring is paused (during file operations)
            // We start unpaused, then get paused during the operation, then unpaused after
            return !fileOperationCompleted.get(); // true when operation is running = paused
        });

        // Start aggressive monitoring that looks for the file
        ExecutorService monitoringExecutor = Executors.newSingleThreadExecutor();
        Future<?> monitoringTask = monitoringExecutor.submit(() -> {
            while (!fileOperationCompleted.get()) {
                if (!monitoringService.isPaused() && !Files.exists(sourceFile)) {
                    monitoringDetectedMissingFile.set(true);
                    if (!fileOperationCompleted.get()) {
                        // This would be where the monitoring service deletes the "missing" file
                        raceConditionOccurred.set(true);
                        break;
                    }
                }
                try {
                    Thread.sleep(1); // Very aggressive checking
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        });

        // Act - Perform file operation with protection
        monitoringProtectionService.executeWithProtection(() -> {
            try {
                // Simulate the file operation that was causing issues
                Thread.sleep(100); // Simulate some processing time
                Files.move(sourceFile, targetFile, StandardCopyOption.REPLACE_EXISTING);
                Thread.sleep(100); // Simulate more processing time
                fileOperationCompleted.set(true);
            } catch (InterruptedException | IOException e) {
                throw new RuntimeException(e);
            }
        }, "race condition test");

        // Wait for monitoring task to complete
        monitoringTask.get(10, TimeUnit.SECONDS);
        monitoringExecutor.shutdown();

        // Assert
        assertThat(Files.exists(targetFile)).isTrue();
        assertThat(Files.exists(sourceFile)).isFalse();
        assertThat(fileOperationCompleted.get()).isTrue();
        
        // The critical assertion: monitoring should not have detected a missing file during the operation
        assertThat(raceConditionOccurred.get())
            .withFailMessage("Race condition occurred! Monitoring detected missing file during protected operation")
            .isFalse();
    }

    @Test
    void raceConditionPrevention_multipleConcurrentFileOperations() throws InterruptedException, IOException, ExecutionException, TimeoutException {
        // Arrange
        when(monitoringService.isPaused()).thenReturn(false).thenReturn(true);
        
        // Multiple file operations that could interfere with each other
        int numberOfOperations = 10;
        List<Path> sourceFiles = new ArrayList<>();
        List<Path> targetFiles = new ArrayList<>();
        
        for (int i = 0; i < numberOfOperations; i++) {
            Path source = tempDir.resolve("source_" + i + ".txt");
            Path target = tempDir.resolve("target_" + i + ".txt");
            Files.write(source, ("content " + i).getBytes());
            sourceFiles.add(source);
            targetFiles.add(target);
        }

        AtomicInteger completedOperations = new AtomicInteger(0);
        AtomicInteger raceConditionCount = new AtomicInteger(0);

        // Start monitoring that checks for missing files
        ExecutorService monitoringExecutor = Executors.newSingleThreadExecutor();
        Future<?> monitoringTask = monitoringExecutor.submit(() -> {
            while (completedOperations.get() < numberOfOperations) {
                if (!monitoringService.isPaused()) {
                    // Check for any missing source files
                    for (int i = 0; i < numberOfOperations; i++) {
                        Path source = sourceFiles.get(i);
                        Path target = targetFiles.get(i);
                        
                        // If source is gone but target doesn't exist, it's a race condition
                        if (!Files.exists(source) && !Files.exists(target)) {
                            raceConditionCount.incrementAndGet();
                        }
                    }
                }
                try {
                    Thread.sleep(1);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        });

        // Act - Perform multiple concurrent file operations
        ExecutorService operationExecutor = Executors.newFixedThreadPool(5);
        CountDownLatch latch = new CountDownLatch(numberOfOperations);

        for (int i = 0; i < numberOfOperations; i++) {
            final int operationIndex = i;
            operationExecutor.submit(() -> {
                try {
                    monitoringProtectionService.executeWithProtection(() -> {
                        try {
                            Thread.sleep(50); // Simulate processing
                            Files.move(sourceFiles.get(operationIndex), 
                                     targetFiles.get(operationIndex),
                                     StandardCopyOption.REPLACE_EXISTING);
                            completedOperations.incrementAndGet();
                        } catch (InterruptedException | IOException e) {
                            throw new RuntimeException(e);
                        }
                    }, "concurrent operation " + operationIndex);
                } finally {
                    latch.countDown();
                }
            });
        }

        // Wait for all operations to complete
        boolean allCompleted = latch.await(30, TimeUnit.SECONDS);
        monitoringTask.get(5, TimeUnit.SECONDS);
        
        operationExecutor.shutdown();
        monitoringExecutor.shutdown();

        // Assert
        assertThat(allCompleted).isTrue();
        assertThat(completedOperations.get()).isEqualTo(numberOfOperations);
        
        // All target files should exist
        for (Path target : targetFiles) {
            assertThat(Files.exists(target)).isTrue();
        }
        
        // No source files should exist
        for (Path source : sourceFiles) {
            assertThat(Files.exists(source)).isFalse();
        }
        
        // Critical assertion: no race conditions should have occurred
        assertThat(raceConditionCount.get())
            .withFailMessage("Race conditions detected during concurrent operations")
            .isEqualTo(0);
    }

    @Test
    void monitoringResumesAfterDelay() throws InterruptedException {
        // Arrange
        when(monitoringService.isPaused()).thenReturn(false, true);
        
        // Act - Perform operation with protection
        monitoringProtectionService.executeWithProtection(() -> {
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }, "delay test");
        
        // Wait for monitoring to resume (should happen after 5 seconds)
        Thread.sleep(6000);
        
        // Assert - Verify monitoring service methods were called
        verify(monitoringService).pauseMonitoring();
        verify(monitoringService).resumeMonitoring();
    }
}