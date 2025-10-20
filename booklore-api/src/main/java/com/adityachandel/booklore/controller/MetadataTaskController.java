package com.adityachandel.booklore.controller;

import com.adityachandel.booklore.model.dto.MetadataBatchProgressNotification;
import com.adityachandel.booklore.model.dto.response.MetadataTaskDetailsResponse;
import com.adityachandel.booklore.service.metadata.MetadataTaskService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/metadata/tasks")
@RequiredArgsConstructor
public class MetadataTaskController {

    private final MetadataTaskService metadataTaskService;

    @GetMapping("/{taskId}")
    @PreAuthorize("@securityUtil.canEditMetadata() or @securityUtil.isAdmin()")
    public ResponseEntity<MetadataTaskDetailsResponse> getTaskWithProposals(@PathVariable String taskId) {
        return metadataTaskService.getTaskWithProposals(taskId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/active")
    @PreAuthorize("@securityUtil.canEditMetadata() or @securityUtil.isAdmin()")
    public ResponseEntity<List<MetadataBatchProgressNotification>> getActiveTasks() {
        return ResponseEntity.ok(metadataTaskService.getActiveTasks());
    }

    @DeleteMapping("/{taskId}")
    @PreAuthorize("@securityUtil.canEditMetadata() or @securityUtil.isAdmin()")
    public ResponseEntity<Void> deleteTask(@PathVariable String taskId) {
        boolean deleted = metadataTaskService.deleteTaskAndProposals(taskId);
        return deleted ? ResponseEntity.noContent().build() : ResponseEntity.notFound().build();
    }

    @PostMapping("/{taskId}/proposals/{proposalId}/status")
    @PreAuthorize("@securityUtil.canEditMetadata() or @securityUtil.isAdmin()")
    public ResponseEntity<Void> updateProposalStatus(@PathVariable String taskId, @PathVariable Long proposalId, @RequestParam String status) {
        boolean updated = metadataTaskService.updateProposalStatus(taskId, proposalId, status);
        return updated ? ResponseEntity.ok().build() : ResponseEntity.notFound().build();
    }
}
