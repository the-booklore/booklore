package com.adityachandel.booklore.controller;

import com.adityachandel.booklore.model.dto.TaskInfo;
import com.adityachandel.booklore.model.dto.request.TaskCreateRequest;
import com.adityachandel.booklore.model.dto.request.TaskCronConfigRequest;
import com.adityachandel.booklore.model.dto.response.CronConfig;
import com.adityachandel.booklore.model.dto.response.TaskCancelResponse;
import com.adityachandel.booklore.model.dto.response.TaskCreateResponse;
import com.adityachandel.booklore.model.dto.response.TasksHistoryResponse;
import com.adityachandel.booklore.model.enums.TaskType;
import com.adityachandel.booklore.service.task.TaskCronService;
import com.adityachandel.booklore.service.task.TaskHistoryService;
import com.adityachandel.booklore.service.task.TaskService;
import com.adityachandel.booklore.task.TaskStatus;
import lombok.AllArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@AllArgsConstructor
@RestController
@RequestMapping("/api/v1/tasks")
public class TaskController {

    private final TaskService service;
    private final TaskHistoryService taskHistoryService;
    private final TaskCronService taskCronService;

    @GetMapping
    @PreAuthorize("@securityUtil.isAdmin()")
    public ResponseEntity<List<TaskInfo>> getAvailableTasks() {
        List<TaskInfo> taskInfos = service.getAvailableTasks();
        return ResponseEntity.ok(taskInfos);
    }

    @PostMapping("/start")
    @PreAuthorize("@securityUtil.isAdmin()")
    public ResponseEntity<TaskCreateResponse> startTask(@RequestBody TaskCreateRequest request) {
        TaskCreateResponse response = service.runAsUser(request);
        if (response.getStatus() == TaskStatus.ACCEPTED) {
            return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
        }
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{taskId}/cancel")
    @PreAuthorize("@securityUtil.isAdmin()")
    public ResponseEntity<TaskCancelResponse> cancelTask(@PathVariable String taskId) {
        TaskCancelResponse response = service.cancelTask(taskId);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/last")
    @PreAuthorize("@securityUtil.isAdmin()")
    public ResponseEntity<TasksHistoryResponse> getLatestTasksForEachType() {
        TasksHistoryResponse response = taskHistoryService.getLatestTasksForEachType();
        return ResponseEntity.ok(response);
    }

    @PatchMapping("/{taskType}/cron")
    @PreAuthorize("@securityUtil.isAdmin()")
    public ResponseEntity<CronConfig> patchCronConfig(@PathVariable TaskType taskType, @RequestBody TaskCronConfigRequest request) {
        CronConfig response = taskCronService.patchCronConfig(taskType, request);
        service.rescheduleTask(taskType);
        return ResponseEntity.ok(response);
    }
}
