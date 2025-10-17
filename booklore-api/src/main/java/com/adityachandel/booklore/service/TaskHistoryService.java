package com.adityachandel.booklore.service;

import com.adityachandel.booklore.repository.TaskRepository;
import com.adityachandel.booklore.model.entity.TaskEntity;
import com.adityachandel.booklore.task.TaskStatus;
import com.adityachandel.booklore.model.dto.response.TaskStatusResponse;
import com.adityachandel.booklore.model.enums.TaskType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class TaskHistoryService {

    private final TaskRepository taskRepository;
    private final TaskMetadataService taskMetadataService;

    @Transactional
    public TaskEntity createTask(String taskId, TaskType type, Long userId, Map<String, Object> options) {
        TaskEntity task = TaskEntity.builder()
                .id(taskId)
                .type(type)
                .status(TaskStatus.ACCEPTED)
                .userId(userId)
                .createdAt(LocalDateTime.now())
                .progressPercentage(0)
                .taskOptions(options)
                .build();

        log.info("Creating task history: id={}, type={}, userId={}", taskId, type, userId);
        return taskRepository.save(task);
    }

    @Transactional
    public void updateTaskStatus(String taskId, TaskStatus status, String message) {
        taskRepository.findById(taskId).ifPresent(task -> {
            task.setStatus(status);
            task.setMessage(message);
            task.setUpdatedAt(LocalDateTime.now());

            if (status == TaskStatus.COMPLETED || status == TaskStatus.FAILED) {
                task.setCompletedAt(LocalDateTime.now());
                task.setProgressPercentage(100);
            }

            taskRepository.save(task);
            log.info("Updated task status: id={}, status={}", taskId, status);
        });
    }

    @Transactional
    public void updateTaskError(String taskId, String errorDetails) {
        taskRepository.findById(taskId).ifPresent(task -> {
            task.setStatus(TaskStatus.FAILED);
            task.setErrorDetails(errorDetails);
            task.setCompletedAt(LocalDateTime.now());
            task.setUpdatedAt(LocalDateTime.now());
            taskRepository.save(task);
            log.error("Task failed: id={}", taskId);
        });
    }

    @Transactional(readOnly = true)
    public TaskStatusResponse getLatestTasksForEachType() {
        List<TaskEntity> latestTasks = taskRepository.findLatestTaskForEachType();

        Map<TaskType, TaskEntity> taskHistoryMap = latestTasks.stream()
                .collect(Collectors.toMap(TaskEntity::getType, task -> task));

        List<TaskStatusResponse.TaskInfo> allTasks = new ArrayList<>();

        for (TaskType taskType : TaskType.values()) {
            TaskEntity existingTask = taskHistoryMap.get(taskType);

            if (existingTask != null) {
                allTasks.add(mapToTaskInfo(existingTask));
            } else {
                allTasks.add(createMetadataOnlyTaskInfo(taskType));
            }
        }

        return TaskStatusResponse.builder()
                .tasks(allTasks)
                .build();
    }

    private TaskStatusResponse.TaskInfo mapToTaskInfo(TaskEntity task) {
        Map<String, Object> metadata = taskMetadataService.getMetadataForTaskType(task.getType());

        return TaskStatusResponse.TaskInfo.builder()
                .id(task.getId())
                .type(task.getType())
                .status(task.getStatus())
                .progressPercentage(task.getProgressPercentage())
                .message(task.getMessage())
                .createdAt(task.getCreatedAt())
                .updatedAt(task.getUpdatedAt())
                .completedAt(task.getCompletedAt())
                .metadata(metadata)
                .build();
    }

    private TaskStatusResponse.TaskInfo createMetadataOnlyTaskInfo(TaskType taskType) {
        Map<String, Object> metadata = taskMetadataService.getMetadataForTaskType(taskType);

        return TaskStatusResponse.TaskInfo.builder()
                .id(null)
                .type(taskType)
                .status(null)
                .progressPercentage(null)
                .message(null)
                .createdAt(null)
                .updatedAt(null)
                .completedAt(null)
                .metadata(metadata)
                .build();
    }
}
