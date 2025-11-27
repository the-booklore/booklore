package com.adityachandel.booklore.service.task;

import com.adityachandel.booklore.repository.TaskHistoryRepository;
import com.adityachandel.booklore.model.entity.TaskHistoryEntity;
import com.adityachandel.booklore.task.TaskStatus;
import com.adityachandel.booklore.model.dto.response.TasksHistoryResponse;
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

    private final TaskHistoryRepository taskHistoryRepository;

    @Transactional
    public void createTask(String taskId, TaskType type, Long userId, Map<String, Object> options) {
        TaskHistoryEntity task = TaskHistoryEntity.builder()
                .id(taskId)
                .type(type)
                .status(TaskStatus.ACCEPTED)
                .userId(userId)
                .createdAt(LocalDateTime.now())
                .progressPercentage(0)
                .taskOptions(options)
                .build();
        taskHistoryRepository.save(task);
    }

    @Transactional
    public void updateTaskStatus(String taskId, TaskStatus status, String message) {
        taskHistoryRepository.findById(taskId).ifPresent(task -> {
            task.setStatus(status);
            task.setMessage(message);
            task.setUpdatedAt(LocalDateTime.now());

            if (status == TaskStatus.COMPLETED || status == TaskStatus.FAILED) {
                task.setCompletedAt(LocalDateTime.now());
                task.setProgressPercentage(100);
            }

            taskHistoryRepository.save(task);
        });
    }

    @Transactional
    public void updateTaskError(String taskId, String errorDetails) {
        taskHistoryRepository.findById(taskId).ifPresent(task -> {
            task.setStatus(TaskStatus.FAILED);
            task.setErrorDetails(errorDetails);
            task.setCompletedAt(LocalDateTime.now());
            task.setUpdatedAt(LocalDateTime.now());
            taskHistoryRepository.save(task);
            log.error("Task failed: id={}", taskId);
        });
    }

    @Transactional(readOnly = true)
    public TasksHistoryResponse getLatestTasksForEachType() {
        List<TaskHistoryEntity> latestTasks;
        try {
            latestTasks = taskHistoryRepository.findLatestTaskForEachType();
        } catch (Exception e) {
            log.warn("Error fetching latest tasks, possibly due to removed enum values: {}", e.getMessage());
            latestTasks = Collections.emptyList();
        }

        Map<TaskType, TaskHistoryEntity> taskHistoryMap = latestTasks.stream()
                .filter(task -> {
                    try {
                        return task.getType() != null;
                    } catch (Exception e) {
                        log.warn("Skipping task with invalid type: taskId={}", task.getId());
                        return false;
                    }
                })
                .collect(Collectors.toMap(TaskHistoryEntity::getType, task -> task, (existing, replacement) -> existing));

        List<TasksHistoryResponse.TaskHistory> allTasks = new ArrayList<>();

        for (TaskType taskType : TaskType.values()) {
            if (taskType.isHiddenFromUI()) {
                continue;
            }

            TaskHistoryEntity existingTask = taskHistoryMap.get(taskType);

            if (existingTask != null) {
                allTasks.add(mapToTaskInfo(existingTask));
            } else {
                allTasks.add(createMetadataOnlyTaskInfo(taskType));
            }
        }

        return TasksHistoryResponse.builder()
                .taskHistories(allTasks)
                .build();
    }

    private TasksHistoryResponse.TaskHistory mapToTaskInfo(TaskHistoryEntity task) {
        return TasksHistoryResponse.TaskHistory.builder()
                .id(task.getId())
                .type(task.getType())
                .status(task.getStatus())
                .progressPercentage(task.getProgressPercentage())
                .message(task.getMessage())
                .createdAt(task.getCreatedAt())
                .updatedAt(task.getUpdatedAt())
                .completedAt(task.getCompletedAt())
                .build();
    }

    private TasksHistoryResponse.TaskHistory createMetadataOnlyTaskInfo(TaskType taskType) {
        return TasksHistoryResponse.TaskHistory.builder()
                .id(null)
                .type(taskType)
                .status(null)
                .progressPercentage(null)
                .message(null)
                .createdAt(null)
                .updatedAt(null)
                .completedAt(null)
                .build();
    }
}
