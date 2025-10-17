package com.adityachandel.booklore.task.tasks;

import com.adityachandel.booklore.model.dto.request.TaskCreateRequest;
import com.adityachandel.booklore.model.dto.response.TaskCreateResponse;
import com.adityachandel.booklore.model.enums.TaskType;

public interface Task {

    TaskCreateResponse execute(TaskCreateRequest request);

    TaskType getTaskType();
}

