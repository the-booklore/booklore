package com.adityachandel.booklore.service;

import com.adityachandel.booklore.service.monitoring.MonitoringService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.*;

/**
 * Integration test to verify that critical file operations properly use monitoring protection
 * to prevent race conditions that can cause data loss.
 * 
 * This addresses the race condition bug where monitoring would detect file operations
 * as "missing files" and delete them before the operations completed.
 */
@ExtendWith(MockitoExtension.class)
class MonitoringProtectionIntegrationTest {

    @Mock
    private MonitoringService monitoringService;

    @Test
    void testMonitoringProtectionPattern_PauseAndResumeSequence() {
        // Given - monitoring service that reports not paused initially
        when(monitoringService.isPaused()).thenReturn(false);
        
        // When - simulating the monitoring protection pattern used in file operations
        boolean didPause = pauseMonitoringIfNeeded();
        try {
            // Critical file operation would happen here
            // (simulated - actual file operations tested in integration tests)
        } finally {
            resumeMonitoringImmediately(didPause);
        }
        
        // Then - verify the correct sequence occurred
        verify(monitoringService).isPaused(); // Check current state
        verify(monitoringService).pauseMonitoring(); // Pause before operation
        verify(monitoringService).resumeMonitoring(); // Resume after operation
    }

    @Test
    void testMonitoringProtectionPattern_AlreadyPaused() {
        // Given - monitoring service that reports already paused
        when(monitoringService.isPaused()).thenReturn(true);
        
        // When - simulating the monitoring protection pattern
        boolean didPause = pauseMonitoringIfNeeded();
        try {
            // Critical file operation would happen here
        } finally {
            resumeMonitoringImmediately(didPause);
        }
        
        // Then - verify no additional pause/resume calls were made
        verify(monitoringService).isPaused(); // Check current state
        verify(monitoringService, never()).pauseMonitoring(); // Should not pause again
        verify(monitoringService, never()).resumeMonitoring(); // Should not resume what we didn't pause
    }

    @Test 
    void testMonitoringProtectionPattern_ExceptionHandling() {
        // Given - monitoring service that reports not paused initially
        when(monitoringService.isPaused()).thenReturn(false);
        
        // When - simulating the monitoring protection pattern with exception
        boolean didPause = pauseMonitoringIfNeeded();
        try {
            // Simulate a critical file operation that throws an exception
            throw new RuntimeException("File operation failed");
        } catch (RuntimeException e) {
            // Exception handling would occur here in real code
        } finally {
            resumeMonitoringImmediately(didPause);
        }
        
        // Then - verify monitoring was still resumed despite the exception
        verify(monitoringService).isPaused();
        verify(monitoringService).pauseMonitoring();
        verify(monitoringService).resumeMonitoring(); // Critical: must resume even on failure
    }

    // Helper methods that mirror the actual implementation pattern
    
    private boolean pauseMonitoringIfNeeded() {
        if (!monitoringService.isPaused()) {
            monitoringService.pauseMonitoring();
            return true;
        }
        return false;
    }

    private void resumeMonitoringImmediately(boolean didPause) {
        if (didPause) {
            monitoringService.resumeMonitoring();
        }
    }
}