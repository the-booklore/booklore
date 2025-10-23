package com.adityachandel.booklore.controller;

import com.adityachandel.booklore.model.dto.request.TaskCreateRequest;
import com.adityachandel.booklore.model.dto.response.TaskCancelResponse;
import com.adityachandel.booklore.model.dto.response.TaskCreateResponse;
import com.adityachandel.booklore.model.dto.response.TaskStatusResponse;
import com.adityachandel.booklore.service.TaskHistoryService;
import com.adityachandel.booklore.service.TaskService;
import com.adityachandel.booklore.task.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.AllArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@AllArgsConstructor
@RestController
@RequestMapping("/api/v2/tasks")
@Tag(name = "Tasks", description = "Endpoints for managing background tasks")
public class TaskV2Controller {

    private final TaskService service;
    private final TaskHistoryService taskHistoryService;

    @Operation(summary = "Start a new task", description = "Start a new background task. Requires admin.")
    @ApiResponse(responseCode = "202", description = "Task accepted")
    @PostMapping
    @PreAuthorize("@securityUtil.isAdmin()")
    public ResponseEntity<TaskCreateResponse> startTask(
            @Parameter(description = "Task creation request") @RequestBody TaskCreateRequest request) {
        TaskCreateResponse response = service.run(request);
        if (response.getStatus() == TaskStatus.ACCEPTED) {
            return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
        }
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Cancel a task", description = "Cancel a running task by its ID. Requires admin.")
    @ApiResponse(responseCode = "200", description = "Task cancelled successfully")
    @DeleteMapping("/{taskId}")
    @PreAuthorize("@securityUtil.isAdmin()")
    public ResponseEntity<TaskCancelResponse> cancelTask(
            @Parameter(description = "ID of the task to cancel") @PathVariable String taskId) {
        TaskCancelResponse response = service.cancelTask(taskId);
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Get latest tasks for each type", description = "Retrieve the latest tasks for each type. Requires admin.")
    @ApiResponse(responseCode = "200", description = "Latest tasks returned successfully")
    @GetMapping("/latest")
    @PreAuthorize("@securityUtil.isAdmin()")
    public ResponseEntity<TaskStatusResponse> getLatestTasksForEachType() {
        TaskStatusResponse response = taskHistoryService.getLatestTasksForEachType();
        return ResponseEntity.ok(response);
    }
}
