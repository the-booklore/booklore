package com.adityachandel.booklore.model.dto.response;

import com.adityachandel.booklore.task.TaskStatus;
import com.adityachandel.booklore.model.enums.TaskType;
import lombok.Builder;
import lombok.Data;

@Builder
@Data
public class TaskCreateResponse {
    private String taskId;
    private TaskType taskType;
    private TaskStatus status;
}
