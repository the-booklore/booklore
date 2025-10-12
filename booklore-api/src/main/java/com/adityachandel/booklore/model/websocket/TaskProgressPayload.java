package com.adityachandel.booklore.model.websocket;

import com.adityachandel.booklore.task.TaskStatus;
import com.adityachandel.booklore.model.enums.TaskType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TaskProgressPayload {
    private String taskId;
    private TaskType taskType;
    private String message;
    private int progress; // 0-100 percentage
    private TaskStatus taskStatus;
}

