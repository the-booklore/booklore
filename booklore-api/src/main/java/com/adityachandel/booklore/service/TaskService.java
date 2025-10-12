package com.adityachandel.booklore.service;

import com.adityachandel.booklore.config.security.service.AuthenticationService;
import com.adityachandel.booklore.exception.APIException;
import com.adityachandel.booklore.model.dto.BookLoreUser;
import com.adityachandel.booklore.model.dto.request.TaskCreateRequest;
import com.adityachandel.booklore.model.dto.response.TaskCancelResponse;
import com.adityachandel.booklore.model.dto.response.TaskCreateResponse;
import com.adityachandel.booklore.model.enums.TaskType;
import com.adityachandel.booklore.task.*;
import com.adityachandel.booklore.task.tasks.Task;
import com.adityachandel.booklore.util.SecurityContextVirtualThread;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Executor;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@Slf4j
public class TaskService {

    private final AuthenticationService authenticationService;
    private final TaskHistoryService taskHistoryService;
    private final Map<TaskType, Task> taskRegistry;
    private final ConcurrentMap<TaskType, String> runningTasks = new ConcurrentHashMap<>();
    private final TaskCancellationManager cancellationManager;
    private final Executor taskExecutor;
    private final ObjectMapper objectMapper;

    public TaskService(AuthenticationService authenticationService, TaskHistoryService taskHistoryService, List<Task> tasks, TaskCancellationManager cancellationManager, Executor taskExecutor, ObjectMapper objectMapper) {
        this.authenticationService = authenticationService;
        this.taskHistoryService = taskHistoryService;
        this.taskRegistry = tasks.stream().collect(Collectors.toMap(Task::getTaskType, Function.identity()));
        this.cancellationManager = cancellationManager;
        this.taskExecutor = taskExecutor;
        this.objectMapper = objectMapper;
    }

    public TaskCreateResponse run(TaskCreateRequest request) {
        if (request == null || request.getTaskType() == null) {
            throw new APIException("Task request and task type cannot be null", HttpStatus.BAD_REQUEST);
        }
        BookLoreUser user = authenticationService.getAuthenticatedUser();
        TaskType taskType = request.getTaskType();
        if (taskType.isAsync()) {
            return runAsync(request, user, taskType);
        } else {
            return runSync(request, user, taskType);
        }
    }

    private TaskCreateResponse runAsync(TaskCreateRequest request, BookLoreUser user, TaskType taskType) {
        String taskId = initializeTask(request, user, taskType);
        TaskCreateResponse response = TaskCreateResponse.builder()
                .taskId(taskId)
                .taskType(taskType)
                .status(TaskStatus.ACCEPTED)
                .build();
        SecurityContext securityContext = SecurityContextHolder.getContext();
        taskExecutor.execute(() ->
                SecurityContextVirtualThread.runWithSecurityContext(securityContext, () ->
                        executeAsyncTask(taskId, request, taskType)
                )
        );
        return response;
    }

    public TaskCancelResponse cancelTask(String taskId) {
        BookLoreUser user = authenticationService.getAuthenticatedUser();
        boolean isRunning = runningTasks.containsValue(taskId);
        if (!isRunning) {
            throw new APIException("Task not found or not running: " + taskId, HttpStatus.NOT_FOUND);
        }
        cancellationManager.cancelTask(taskId);
        taskHistoryService.updateTaskStatus(taskId, TaskStatus.CANCELLED, "Task cancellation requested by user");
        log.info("Task {} cancellation requested by user {}", taskId, user.getUsername());
        return TaskCancelResponse.builder()
                .taskId(taskId)
                .cancelled(true)
                .message("Task cancellation requested. The task will stop at the next checkpoint.")
                .build();
    }

    private void executeAsyncTask(String taskId, TaskCreateRequest request, TaskType taskType) {
        try {
            taskHistoryService.updateTaskStatus(taskId, TaskStatus.IN_PROGRESS, "Task execution started");
            request.setTaskId(taskId);
            if (cancellationManager.isTaskCancelled(taskId)) {
                log.info("Task {} was cancelled before execution", taskId);
                taskHistoryService.updateTaskStatus(taskId, TaskStatus.CANCELLED, "Task was cancelled");
                return;
            }
            executeTask(request);
            if (cancellationManager.isTaskCancelled(taskId)) {
                log.info("Task {} was cancelled during execution", taskId);
                taskHistoryService.updateTaskStatus(taskId, TaskStatus.CANCELLED, "Task was cancelled");
            } else {
                taskHistoryService.updateTaskStatus(taskId, TaskStatus.COMPLETED, "Task completed successfully");
                log.info("Async task {} of type {} completed successfully", taskId, taskType);
            }
        } catch (Exception e) {
            log.error("Async task {} of type {} failed", taskId, taskType, e);
            taskHistoryService.updateTaskError(taskId, e.getMessage());
        } finally {
            if (!taskType.isParallel()) {
                runningTasks.remove(taskType);
            }
            cancellationManager.clearCancellation(taskId);
        }
    }

    private TaskCreateResponse runSync(TaskCreateRequest request, BookLoreUser user, TaskType taskType) {
        String taskId = initializeTask(request, user, taskType);
        try {
            taskHistoryService.updateTaskStatus(taskId, TaskStatus.IN_PROGRESS, "Task execution started");
            request.setTaskId(taskId);
            TaskCreateResponse response = executeTask(request);
            response.setTaskId(taskId);
            taskHistoryService.updateTaskStatus(taskId, TaskStatus.COMPLETED, "Task completed successfully");
            log.info("Sync task {} of type {} completed successfully", taskId, taskType);
            return response;
        } catch (Exception e) {
            log.error("Sync task {} of type {} failed", taskId, taskType, e);
            taskHistoryService.updateTaskError(taskId, e.getMessage());
            throw e;
        } finally {
            if (!taskType.isParallel()) {
                runningTasks.remove(taskType);
                log.info("Released lock for task type: {}", taskType);
            }
        }
    }

    private String initializeTask(TaskCreateRequest request, BookLoreUser user, TaskType taskType) {
        if (!taskType.isParallel()) {
            String existingTaskId = runningTasks.putIfAbsent(taskType, "");
            if (existingTaskId != null) {
                log.warn("Task of type {} is already running, rejecting new request", taskType);
                throw new APIException("A task of type " + taskType + " is already running. Please wait for it to complete.", HttpStatus.CONFLICT);
            }
        }
        String taskId = UUID.randomUUID().toString();
        if (!taskType.isParallel()) {
            runningTasks.put(taskType, taskId);
        }
        Map<String, Object> options = convertOptionsToMap(request.getOptions());
        taskHistoryService.createTask(taskId, taskType, user.getId(), options);
        log.info("Initialized task: id={}, type={}, userId={}", taskId, taskType, user.getId());
        return taskId;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> convertOptionsToMap(Object options) {
        if (options == null) {
            return Map.of();
        }
        if (options instanceof Map) {
            return (Map<String, Object>) options;
        }
        try {
            return objectMapper.convertValue(options, Map.class);
        } catch (IllegalArgumentException e) {
            log.warn("Failed to convert options to map, using empty map", e);
            return Map.of();
        }
    }

    private TaskCreateResponse executeTask(TaskCreateRequest request) {
        TaskType taskType = request.getTaskType();
        log.info("Executing task of type: {}", taskType);
        Task task = taskRegistry.get(taskType);
        if (task == null) {
            throw new UnsupportedOperationException("Task type not implemented: " + taskType);
        }
        return task.execute(request);
    }
}
