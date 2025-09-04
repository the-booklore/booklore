package com.adityachandel.booklore.service;

import com.adityachandel.booklore.service.monitoring.MonitoringProtectionService;
import com.adityachandel.booklore.service.monitoring.MonitoringService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MonitoringProtectionConcurrentTest {

    @Mock
    private MonitoringService monitoringService;

    private MonitoringProtectionService monitoringProtectionService;

    @BeforeEach
    void setUp() {
        monitoringProtectionService = new MonitoringProtectionService(monitoringService);
    }

    @Test
    void concurrentOperations_properSynchronization() throws InterruptedException {
        // Arrange
        when(monitoringService.isPaused()).thenReturn(false, true, true, true, true);
        
        AtomicInteger operationCount = new AtomicInteger(0);
        AtomicInteger pauseCount = new AtomicInteger(0);
        AtomicInteger resumeCount = new AtomicInteger(0);
        
        // Track pause/resume calls
        doAnswer(invocation -> {
            pauseCount.incrementAndGet();
            return null;
        }).when(monitoringService).pauseMonitoring();
        
        doAnswer(invocation -> {
            resumeCount.incrementAndGet();
            return null;
        }).when(monitoringService).resumeMonitoring();

        int numberOfThreads = 10;
        ExecutorService executor = Executors.newFixedThreadPool(numberOfThreads);
        CountDownLatch latch = new CountDownLatch(numberOfThreads);

        // Act - Run multiple concurrent operations
        List<Future<?>> futures = new ArrayList<>();
        for (int i = 0; i < numberOfThreads; i++) {
            final int operationId = i;
            Future<?> future = executor.submit(() -> {
                try {
                    monitoringProtectionService.executeWithProtection(() -> {
                        operationCount.incrementAndGet();
                        // Simulate some work
                        try {
                            Thread.sleep(10);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        }
                    }, "concurrent test operation " + operationId);
                } finally {
                    latch.countDown();
                }
            });
            futures.add(future);
        }

        // Wait for all operations to complete
        boolean completed = latch.await(30, TimeUnit.SECONDS);
        executor.shutdown();

        // Assert
        assertThat(completed).isTrue();
        assertThat(operationCount.get()).isEqualTo(numberOfThreads);
        
        // Verify monitoring was paused at least once (may be fewer due to synchronization)
        assertThat(pauseCount.get()).isGreaterThan(0);
        
        // Wait a bit for resume calls (they happen with delay in virtual threads)
        Thread.sleep(6000);
        assertThat(resumeCount.get()).isGreaterThan(0);

        // All futures should complete successfully
        for (Future<?> future : futures) {
            assertThat(future.isDone()).isTrue();
        }
    }

    @Test
    void concurrentOperations_onlyOnePausesMonitoring() throws InterruptedException {
        // Arrange - First call returns false (not paused), subsequent return true (already paused)
        when(monitoringService.isPaused()).thenReturn(false).thenReturn(true);
        
        AtomicInteger pauseCallCount = new AtomicInteger(0);
        doAnswer(invocation -> {
            pauseCallCount.incrementAndGet();
            return null;
        }).when(monitoringService).pauseMonitoring();

        int numberOfThreads = 5;
        ExecutorService executor = Executors.newFixedThreadPool(numberOfThreads);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch finishLatch = new CountDownLatch(numberOfThreads);

        // Act - Start all threads at the same time to maximize contention
        for (int i = 0; i < numberOfThreads; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await(); // Wait for signal to start
                    monitoringProtectionService.executeWithProtection(() -> {
                        // Simulate work
                        try {
                            Thread.sleep(100);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        }
                    }, "sync test");
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    finishLatch.countDown();
                }
            });
        }

        startLatch.countDown(); // Start all threads
        boolean completed = finishLatch.await(10, TimeUnit.SECONDS);
        executor.shutdown();

        // Assert
        assertThat(completed).isTrue();
        
        // Only one thread should have actually called pauseMonitoring due to synchronization
        assertThat(pauseCallCount.get()).isEqualTo(1);
    }

    @Test
    void concurrentOperations_withExceptions() throws InterruptedException {
        // Arrange
        when(monitoringService.isPaused()).thenReturn(false, true, true);
        
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger exceptionCount = new AtomicInteger(0);
        AtomicInteger resumeCount = new AtomicInteger(0);

        doAnswer(invocation -> {
            resumeCount.incrementAndGet();
            return null;
        }).when(monitoringService).resumeMonitoring();

        int numberOfThreads = 8;
        ExecutorService executor = Executors.newFixedThreadPool(numberOfThreads);
        CountDownLatch latch = new CountDownLatch(numberOfThreads);

        // Act - Half the operations throw exceptions
        for (int i = 0; i < numberOfThreads; i++) {
            final int operationId = i;
            executor.submit(() -> {
                try {
                    monitoringProtectionService.executeWithProtection(() -> {
                        if (operationId % 2 == 0) {
                            successCount.incrementAndGet();
                        } else {
                            exceptionCount.incrementAndGet();
                            throw new RuntimeException("Test exception " + operationId);
                        }
                    }, "exception test " + operationId);
                } catch (RuntimeException e) {
                    // Expected for half the operations
                } finally {
                    latch.countDown();
                }
            });
        }

        boolean completed = latch.await(10, TimeUnit.SECONDS);
        executor.shutdown();

        // Assert
        assertThat(completed).isTrue();
        assertThat(successCount.get()).isEqualTo(numberOfThreads / 2);
        assertThat(exceptionCount.get()).isEqualTo(numberOfThreads / 2);
        
        // Wait for resume calls (delayed)
        Thread.sleep(6000);
        
        // Even with exceptions, monitoring should still be resumed
        assertThat(resumeCount.get()).isGreaterThan(0);
    }

    @Test
    void concurrentOperations_supplierReturnValues() throws InterruptedException, ExecutionException, TimeoutException {
        // Arrange
        when(monitoringService.isPaused()).thenReturn(false, true, true, true);
        
        int numberOfThreads = 6;
        ExecutorService executor = Executors.newFixedThreadPool(numberOfThreads);
        
        // Act - Use Supplier version to test return values
        List<Future<String>> futures = new ArrayList<>();
        for (int i = 0; i < numberOfThreads; i++) {
            final int operationId = i;
            Future<String> future = executor.submit(() -> 
                monitoringProtectionService.executeWithProtection(() -> {
                    try {
                        Thread.sleep(50); // Simulate work
                        return "Result-" + operationId;
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        return "Interrupted-" + operationId;
                    }
                }, "supplier test " + operationId)
            );
            futures.add(future);
        }

        // Assert
        List<String> results = new ArrayList<>();
        for (Future<String> future : futures) {
            String result = future.get(10, TimeUnit.SECONDS);
            results.add(result);
        }
        
        executor.shutdown();
        
        assertThat(results).hasSize(numberOfThreads);
        for (int i = 0; i < numberOfThreads; i++) {
            assertThat(results).contains("Result-" + i);
        }
    }

    @Test
    void stressTest_manyQuickOperations() throws InterruptedException {
        // Arrange
        when(monitoringService.isPaused()).thenReturn(false).thenReturn(true);
        
        AtomicInteger completedOperations = new AtomicInteger(0);
        int numberOfOperations = 100;
        ExecutorService executor = Executors.newFixedThreadPool(20);
        CountDownLatch latch = new CountDownLatch(numberOfOperations);

        // Act - Many quick operations to test lock contention
        for (int i = 0; i < numberOfOperations; i++) {
            executor.submit(() -> {
                try {
                    monitoringProtectionService.executeWithProtection(() -> {
                        completedOperations.incrementAndGet();
                        // Very quick operation
                    }, "stress test");
                } finally {
                    latch.countDown();
                }
            });
        }

        boolean completed = latch.await(30, TimeUnit.SECONDS);
        executor.shutdown();

        // Assert
        assertThat(completed).isTrue();
        assertThat(completedOperations.get()).isEqualTo(numberOfOperations);
        
        // Should only pause once due to synchronization, even with many operations
        verify(monitoringService, times(1)).pauseMonitoring();
    }
}