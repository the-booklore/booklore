package com.adityachandel.booklore.controller;

import com.adityachandel.booklore.model.dto.request.TaskCreateRequest;
import com.adityachandel.booklore.model.dto.response.TaskCancelResponse;
import com.adityachandel.booklore.model.dto.response.TaskCreateResponse;
import com.adityachandel.booklore.model.dto.response.TaskStatusResponse;
import com.adityachandel.booklore.quartz.JobSchedulerService;
import com.adityachandel.booklore.service.TaskHistoryService;
import com.adityachandel.booklore.service.TaskService;
import com.adityachandel.booklore.task.TaskStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/v1/tasks")
@RequiredArgsConstructor
public class TaskController {

    private final JobSchedulerService jobSchedulerService;
    private final TaskService taskService;
    private final TaskHistoryService taskHistoryService;

    @PostMapping
    @PreAuthorize("@securityUtil.isAdmin()")
    public ResponseEntity<TaskCreateResponse> startTask(@RequestBody TaskCreateRequest request) {
        TaskCreateResponse response = taskService.run(request);
        if (response.getStatus() == TaskStatus.ACCEPTED) {
            return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
        }
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{taskId}")
    public ResponseEntity<?> cancelTask(@PathVariable String taskId) {
        // Try the new task system first
        try {
            TaskCancelResponse response = taskService.cancelTask(taskId);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            // Fall back to legacy Quartz system for backwards compatibility
            log.info("Trying legacy task cancellation for task: {}", taskId);
            boolean cancelled = jobSchedulerService.cancelJob(taskId);
            if (cancelled) {
                return ResponseEntity.ok(Map.of("message", "Task cancellation scheduled"));
            } else {
                return ResponseEntity.badRequest().body(Map.of("error", "Failed to cancel task or task not found"));
            }
        }
    }

    @GetMapping("/latest")
    @PreAuthorize("@securityUtil.isAdmin()")
    public ResponseEntity<TaskStatusResponse> getLatestTasksForEachType() {
        TaskStatusResponse response = taskHistoryService.getLatestTasksForEachType();
        return ResponseEntity.ok(response);
    }

    @GetMapping("/history")
    @PreAuthorize("@securityUtil.isAdmin()")
    public ResponseEntity<TaskStatusResponse> getTaskHistory(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String type) {
        TaskStatusResponse response = taskHistoryService.getTaskHistory(page, size, status, type);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/running")
    @PreAuthorize("@securityUtil.isAdmin()")
    public ResponseEntity<TaskStatusResponse> getRunningTasks() {
        TaskStatusResponse response = taskHistoryService.getRunningTasks();
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{taskId}")
    @PreAuthorize("@securityUtil.isAdmin()")
    public ResponseEntity<TaskStatusResponse.TaskInfo> getTaskById(@PathVariable String taskId) {
        TaskStatusResponse.TaskInfo response = taskHistoryService.getTaskById(taskId);
        return ResponseEntity.ok(response);
    }
}

