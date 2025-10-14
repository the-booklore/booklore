package com.adityachandel.booklore.service;

import com.adityachandel.booklore.config.security.service.AuthenticationService;
import com.adityachandel.booklore.exception.APIException;
import com.adityachandel.booklore.model.dto.BookLoreUser;
import com.adityachandel.booklore.model.dto.request.TaskCreateRequest;
import com.adityachandel.booklore.model.dto.response.TaskCancelResponse;
import com.adityachandel.booklore.model.dto.response.TaskCreateResponse;
import com.adityachandel.booklore.model.enums.TaskType;
import com.adityachandel.booklore.task.TaskCancellationManager;
import com.adityachandel.booklore.task.TaskStatus;
import com.adityachandel.booklore.task.tasks.Task;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.*;
import org.springframework.http.HttpStatus;

import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TaskServiceTest {

    @Mock AuthenticationService authenticationService;
    @Mock TaskHistoryService taskHistoryService;
    @Mock TaskCancellationManager cancellationManager;
    @Mock Executor taskExecutor;
    @Mock ObjectMapper objectMapper;
    @Mock Task task;

    TaskService taskService;

    BookLoreUser user;
    TaskCreateRequest request;

    @BeforeEach
    void setUp() throws Exception {
        MockitoAnnotations.openMocks(this);
        user = new BookLoreUser();
        user.setId(1L);
        user.setUsername("testuser");
        request = new TaskCreateRequest();
        request.setTaskType(TaskType.CLEAR_CBX_CACHE);

        when(authenticationService.getAuthenticatedUser()).thenReturn(user);
        when(task.getTaskType()).thenReturn(TaskType.CLEAR_CBX_CACHE);
        when(task.execute(any())).thenReturn(TaskCreateResponse.builder()
                .taskType(TaskType.CLEAR_CBX_CACHE)
                .status(TaskStatus.COMPLETED)
                .build());

        taskService = new TaskService(authenticationService, taskHistoryService, List.of(task), cancellationManager, taskExecutor, objectMapper);
    }

    @Test
    void testRunSyncTaskSuccess() {
        TaskCreateResponse response = taskService.run(request);
        assertNotNull(response);
        assertEquals(TaskType.CLEAR_CBX_CACHE, response.getTaskType());
        assertEquals(TaskStatus.COMPLETED, response.getStatus());
        verify(taskHistoryService).createTask(anyString(), eq(TaskType.CLEAR_CBX_CACHE), eq(1L), any());
        verify(taskHistoryService).updateTaskStatus(anyString(), eq(TaskStatus.IN_PROGRESS), anyString());
        verify(taskHistoryService).updateTaskStatus(anyString(), eq(TaskStatus.COMPLETED), anyString());
    }

    @Test
    void testRunWithNullRequestThrowsException() {
        APIException ex = assertThrows(APIException.class, () -> taskService.run(null));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
    }

    @Test
    void testRunWithNullTaskTypeThrowsException() {
        request.setTaskType(null);
        APIException ex = assertThrows(APIException.class, () -> taskService.run(request));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
    }

    @Test
    void testCancelTaskNotFoundThrowsException() {
        when(authenticationService.getAuthenticatedUser()).thenReturn(user);
        APIException ex = assertThrows(APIException.class, () -> taskService.cancelTask("notfound"));
        assertEquals(HttpStatus.NOT_FOUND, ex.getStatus());
    }

    @Test
    void testCancelTaskSuccess() throws Exception {
        String taskId = "task123";
        var field = TaskService.class.getDeclaredField("runningTasks");
        field.setAccessible(true);
        @SuppressWarnings("unchecked")
        Map<TaskType, String> runningTasks = (Map<TaskType, String>) field.get(taskService);
        runningTasks.put(TaskType.RE_SCAN_LIBRARY, taskId);

        when(authenticationService.getAuthenticatedUser()).thenReturn(user);

        TaskCancelResponse response = taskService.cancelTask(taskId);
        assertTrue(response.isCancelled());
        assertEquals(taskId, response.getTaskId());
        verify(cancellationManager).cancelTask(taskId);
        verify(taskHistoryService).updateTaskStatus(taskId, TaskStatus.CANCELLED, "Task cancellation requested by user");
    }
}
