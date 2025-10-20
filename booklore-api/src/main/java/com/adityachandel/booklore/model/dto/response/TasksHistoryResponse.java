package com.adityachandel.booklore.model.dto.response;

import com.adityachandel.booklore.task.TaskStatus;
import com.adityachandel.booklore.model.enums.TaskType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TasksHistoryResponse {
    private List<TaskHistory> taskHistories;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TaskHistory {
        private String id;
        private TaskType type;
        private TaskStatus status;
        private Integer progressPercentage;
        private String message;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;
        private LocalDateTime completedAt;
    }
}
