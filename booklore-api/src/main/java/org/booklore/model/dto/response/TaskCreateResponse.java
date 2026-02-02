package org.booklore.model.dto.response;

import org.booklore.task.TaskStatus;
import org.booklore.model.enums.TaskType;
import lombok.Builder;
import lombok.Data;

@Builder
@Data
public class TaskCreateResponse {
    private String taskId;
    private TaskType taskType;
    private TaskStatus status;
}
