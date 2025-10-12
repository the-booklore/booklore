package com.adityachandel.booklore.service;

import com.adityachandel.booklore.model.dto.response.TaskStatusResponse;
import com.adityachandel.booklore.model.entity.TaskEntity;
import com.adityachandel.booklore.model.enums.TaskType;
import com.adityachandel.booklore.repository.TaskRepository;
import com.adityachandel.booklore.task.TaskStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.*;

import java.time.LocalDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TaskHistoryServiceTest {

    @Mock
    private TaskRepository taskRepository;
    @Mock
    private TaskMetadataService taskMetadataService;
    @InjectMocks
    private TaskHistoryService taskHistoryService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void testCreateTask() {
        String taskId = "task1";
        TaskType type = TaskType.CLEAR_CBX_CACHE;
        Long userId = 123L;
        Map<String, Object> options = new HashMap<>();
        TaskEntity mockTask = TaskEntity.builder()
                .id(taskId)
                .type(type)
                .status(TaskStatus.ACCEPTED)
                .userId(userId)
                .createdAt(LocalDateTime.now())
                .progressPercentage(0)
                .taskOptions(options)
                .build();
        when(taskRepository.save(any(TaskEntity.class))).thenReturn(mockTask);
        TaskEntity result = taskHistoryService.createTask(taskId, type, userId, options);
        assertNotNull(result);
        assertEquals(taskId, result.getId());
        assertEquals(type, result.getType());
        assertEquals(TaskStatus.ACCEPTED, result.getStatus());
        assertEquals(userId, result.getUserId());
    }

    @Test
    void testUpdateTaskStatus() {
        String taskId = "task2";
        TaskEntity task = TaskEntity.builder()
                .id(taskId)
                .type(TaskType.CLEAR_PDF_CACHE)
                .status(TaskStatus.ACCEPTED)
                .build();
        when(taskRepository.findById(taskId)).thenReturn(Optional.of(task));
        when(taskRepository.save(any(TaskEntity.class))).thenReturn(task);
        taskHistoryService.updateTaskStatus(taskId, TaskStatus.COMPLETED, "Done");
        assertEquals(TaskStatus.COMPLETED, task.getStatus());
        assertEquals("Done", task.getMessage());
        assertEquals(100, task.getProgressPercentage());
        assertNotNull(task.getCompletedAt());
    }

    @Test
    void testUpdateTaskStatus_TaskNotFound() {
        String taskId = "notfound";
        when(taskRepository.findById(taskId)).thenReturn(Optional.empty());
        assertDoesNotThrow(() -> taskHistoryService.updateTaskStatus(taskId, TaskStatus.COMPLETED, "Done"));
        verify(taskRepository, never()).save(any());
    }

    @Test
    void testUpdateTaskError() {
        String taskId = "task3";
        TaskEntity task = TaskEntity.builder()
                .id(taskId)
                .type(TaskType.RE_SCAN_LIBRARY)
                .status(TaskStatus.ACCEPTED)
                .build();
        when(taskRepository.findById(taskId)).thenReturn(Optional.of(task));
        when(taskRepository.save(any(TaskEntity.class))).thenReturn(task);
        taskHistoryService.updateTaskError(taskId, "Some error");
        assertEquals(TaskStatus.FAILED, task.getStatus());
        assertEquals("Some error", task.getErrorDetails());
        assertNotNull(task.getCompletedAt());
    }

    @Test
    void testUpdateTaskError_TaskNotFound() {
        String taskId = "notfound";
        when(taskRepository.findById(taskId)).thenReturn(Optional.empty());
        assertDoesNotThrow(() -> taskHistoryService.updateTaskError(taskId, "error"));
        verify(taskRepository, never()).save(any());
    }

    @Test
    void testGetLatestTasksForEachType() {
        TaskEntity cbxTask = TaskEntity.builder()
                .id("t1")
                .type(TaskType.CLEAR_CBX_CACHE)
                .status(TaskStatus.COMPLETED)
                .build();
        TaskEntity pdfTask = TaskEntity.builder()
                .id("t2")
                .type(TaskType.CLEAR_PDF_CACHE)
                .status(TaskStatus.ACCEPTED)
                .build();
        List<TaskEntity> latestTasks = Arrays.asList(cbxTask, pdfTask);
        when(taskRepository.findLatestTaskForEachType()).thenReturn(latestTasks);
        when(taskMetadataService.getMetadataForTaskType(any(TaskType.class))).thenReturn(Collections.emptyMap());
        TaskStatusResponse response = taskHistoryService.getLatestTasksForEachType();
        assertNotNull(response);
        assertEquals(TaskType.values().length, response.getTasks().size());
        assertTrue(response.getTasks().stream().anyMatch(t -> t.getType() == TaskType.CLEAR_CBX_CACHE));
        assertTrue(response.getTasks().stream().anyMatch(t -> t.getType() == TaskType.CLEAR_PDF_CACHE));
    }

    @Test
    void testGetLatestTasksForEachType_NoTasks() {
        when(taskRepository.findLatestTaskForEachType()).thenReturn(Collections.emptyList());
        when(taskMetadataService.getMetadataForTaskType(any(TaskType.class))).thenReturn(Collections.emptyMap());
        TaskStatusResponse response = taskHistoryService.getLatestTasksForEachType();
        assertNotNull(response);
        assertEquals(TaskType.values().length, response.getTasks().size());
        assertTrue(response.getTasks().stream().allMatch(t -> t.getId() == null));
    }

    @Test
    void testCreateTask_NullOptions() {
        String taskId = "task4";
        TaskType type = TaskType.RE_SCAN_LIBRARY;
        Long userId = 456L;
        TaskEntity mockTask = TaskEntity.builder()
                .id(taskId)
                .type(type)
                .status(TaskStatus.ACCEPTED)
                .userId(userId)
                .createdAt(LocalDateTime.now())
                .progressPercentage(0)
                .taskOptions(null)
                .build();
        when(taskRepository.save(any(TaskEntity.class))).thenReturn(mockTask);
        TaskEntity result = taskHistoryService.createTask(taskId, type, userId, null);
        assertNotNull(result);
        assertEquals(taskId, result.getId());
        assertNull(result.getTaskOptions());
    }
}
