package org.booklore.task.tasks;

import org.booklore.exception.APIException;
import org.booklore.model.dto.BookLoreUser;
import org.booklore.model.dto.request.FileHashVerificationRequest;
import org.booklore.model.dto.request.TaskCreateRequest;
import org.booklore.model.dto.response.TaskCreateResponse;
import org.booklore.model.enums.TaskType;
import org.booklore.service.NotificationService;
import org.booklore.service.file.FileHashVerificationService;
import org.booklore.task.TaskStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.Collections;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class VerifyFileHashesTaskTest {

    @Mock
    private FileHashVerificationService fileHashVerificationService;

    @Mock
    private NotificationService notificationService;

    @InjectMocks
    private VerifyFileHashesTask verifyFileHashesTask;

    private BookLoreUser user;
    private BookLoreUser adminUser;

    @BeforeEach
    void setUp() {
        user = mock(BookLoreUser.class);
        BookLoreUser.UserPermissions userPerms = mock(BookLoreUser.UserPermissions.class);
        when(userPerms.isAdmin()).thenReturn(false);
        when(user.getPermissions()).thenReturn(userPerms);

        adminUser = mock(BookLoreUser.class);
        BookLoreUser.UserPermissions adminPerms = mock(BookLoreUser.UserPermissions.class);
        when(adminPerms.isAdmin()).thenReturn(true);
        when(adminUser.getPermissions()).thenReturn(adminPerms);
    }

    @Test
    void validatePermissions_shouldThrowException_whenNotAdmin() {
        TaskCreateRequest taskCreateRequest = mock(TaskCreateRequest.class);
        assertThrows(APIException.class, () -> 
            verifyFileHashesTask.validatePermissions(user, taskCreateRequest));
    }

    @Test
    void validatePermissions_shouldPass_whenAdmin() {
        TaskCreateRequest taskCreateRequest = mock(TaskCreateRequest.class);
        assertDoesNotThrow(() -> 
            verifyFileHashesTask.validatePermissions(adminUser, taskCreateRequest));
    }

    @Test
    void execute_shouldCallServiceAndReturnCompleted() {
        FileHashVerificationRequest verificationRequest = FileHashVerificationRequest.builder()
                .verificationType(FileHashVerificationRequest.VerificationType.LIBRARY)
                .libraryId(1L)
                .build();
        
        TaskCreateRequest taskCreateRequest = mock(TaskCreateRequest.class);
        when(taskCreateRequest.getOptionsAs(FileHashVerificationRequest.class))
                .thenReturn(verificationRequest);
        when(taskCreateRequest.getTaskId()).thenReturn("task-1");
        
        FileHashVerificationService.VerificationSummary summary = 
                FileHashVerificationService.VerificationSummary.builder()
                        .totalBooks(100)
                        .mismatchCount(5)
                        .errorCount(0)
                        .isDryRun(false)
                        .build();
        
        when(fileHashVerificationService.verifyFileHashes(verificationRequest, "task-1"))
                .thenReturn(summary);
        
        TaskCreateResponse response = verifyFileHashesTask.execute(taskCreateRequest);

        assertEquals(TaskStatus.COMPLETED, response.getStatus());
        assertEquals("task-1", response.getTaskId());
        assertEquals(TaskType.VERIFY_FILE_HASHES, response.getTaskType());
        
        ArgumentCaptor<FileHashVerificationRequest> requestCaptor = 
                ArgumentCaptor.forClass(FileHashVerificationRequest.class);
        ArgumentCaptor<String> taskIdCaptor = ArgumentCaptor.forClass(String.class);
        
        verify(fileHashVerificationService).verifyFileHashes(requestCaptor.capture(), taskIdCaptor.capture());
        assertEquals(verificationRequest, requestCaptor.getValue());
        assertEquals("task-1", taskIdCaptor.getValue());
        verify(notificationService).sendMessage(any(), any());
    }

    @Test
    void execute_shouldPropagateException_whenServiceThrows() {
        FileHashVerificationRequest verificationRequest = FileHashVerificationRequest.builder()
                .verificationType(FileHashVerificationRequest.VerificationType.LIBRARY)
                .libraryId(1L)
                .build();
        
        TaskCreateRequest taskCreateRequest = mock(TaskCreateRequest.class);
        when(taskCreateRequest.getOptionsAs(FileHashVerificationRequest.class))
                .thenReturn(verificationRequest);
        when(taskCreateRequest.getTaskId()).thenReturn("task-1");
        doThrow(new RuntimeException("Service error"))
                .when(fileHashVerificationService)
                .verifyFileHashes(verificationRequest, "task-1");

        assertThrows(RuntimeException.class, () -> verifyFileHashesTask.execute(taskCreateRequest));
        verify(notificationService, never()).sendMessage(any(), any());
    }

    @Test
    void getTaskType_shouldReturnCorrectType() {
        assertEquals(TaskType.VERIFY_FILE_HASHES, verifyFileHashesTask.getTaskType());
    }

    @Test
    void execute_withBookIdsRequest_shouldWork() {
        TaskCreateRequest taskCreateRequest = mock(TaskCreateRequest.class);
        FileHashVerificationRequest booksRequest = FileHashVerificationRequest.builder()
                .verificationType(FileHashVerificationRequest.VerificationType.BOOKS)
                .bookIds(Set.of(1L, 2L, 3L))
                .build();
        
        FileHashVerificationService.VerificationSummary summary = 
                FileHashVerificationService.VerificationSummary.builder()
                        .totalBooks(3)
                        .mismatchCount(0)
                        .errorCount(0)
                        .isDryRun(true)
                        .build();
        
        when(taskCreateRequest.getOptionsAs(FileHashVerificationRequest.class))
                .thenReturn(booksRequest);
        when(taskCreateRequest.getTaskId()).thenReturn("task-2");
        when(fileHashVerificationService.verifyFileHashes(booksRequest, "task-2"))
                .thenReturn(summary);
        
        TaskCreateResponse response = verifyFileHashesTask.execute(taskCreateRequest);

        assertEquals(TaskStatus.COMPLETED, response.getStatus());
        verify(fileHashVerificationService).verifyFileHashes(booksRequest, "task-2");
    }

    @Test
    void execute_shouldThrowException_whenRequestIsNull() {
        TaskCreateRequest taskCreateRequest = mock(TaskCreateRequest.class);
        when(taskCreateRequest.getOptionsAs(FileHashVerificationRequest.class))
                .thenReturn(null);
        when(taskCreateRequest.getTaskId()).thenReturn("task-3");

        assertThrows(IllegalArgumentException.class, () -> 
            verifyFileHashesTask.execute(taskCreateRequest));
    }
}
