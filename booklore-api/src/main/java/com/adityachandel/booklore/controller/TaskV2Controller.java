package com.adityachandel.booklore.controller;

import com.adityachandel.booklore.model.dto.request.TaskCreateRequest;
import com.adityachandel.booklore.model.dto.response.TaskCancelResponse;
import com.adityachandel.booklore.model.dto.response.TaskCreateResponse;
import com.adityachandel.booklore.model.dto.response.TaskStatusResponse;
import com.adityachandel.booklore.service.TaskHistoryService;
import com.adityachandel.booklore.service.TaskService;
import com.adityachandel.booklore.task.*;
import lombok.AllArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@AllArgsConstructor
@RestController
@RequestMapping("/api/v2/tasks")
public class TaskV2Controller {

    private final TaskService service;
    private final TaskHistoryService taskHistoryService;

    @PostMapping
    @PreAuthorize("@securityUtil.isAdmin()")
    public ResponseEntity<TaskCreateResponse> startTask(@RequestBody TaskCreateRequest request) {
        TaskCreateResponse response = service.run(request);
        if (response.getStatus() == TaskStatus.ACCEPTED) {
            return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
        }
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{taskId}")
    @PreAuthorize("@securityUtil.isAdmin()")
    public ResponseEntity<TaskCancelResponse> cancelTask(@PathVariable String taskId) {
        TaskCancelResponse response = service.cancelTask(taskId);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/latest")
    @PreAuthorize("@securityUtil.isAdmin()")
    public ResponseEntity<TaskStatusResponse> getLatestTasksForEachType() {
        TaskStatusResponse response = taskHistoryService.getLatestTasksForEachType();
        return ResponseEntity.ok(response);
    }
}
