package com.adityachandel.booklore.model.dto.response;

import com.adityachandel.booklore.task.TaskStatus;
import com.adityachandel.booklore.model.enums.TaskType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TaskStatusResponse {
    private List<TaskInfo> tasks;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TaskInfo {
        private String id;
        private TaskType type;
        private TaskStatus status;
        private Integer progressPercentage;
        private String message;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;
        private LocalDateTime completedAt;
        private Map<String, Object> metadata;
    }
}
