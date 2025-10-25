package com.adityachandel.booklore.task.tasks;

import com.adityachandel.booklore.model.dto.request.MetadataRefreshRequest;
import com.adityachandel.booklore.model.dto.request.TaskCreateRequest;
import com.adityachandel.booklore.model.dto.response.TaskCreateResponse;
import com.adityachandel.booklore.model.enums.TaskType;
import com.adityachandel.booklore.service.metadata.MetadataRefreshService;
import com.adityachandel.booklore.task.TaskStatus;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@AllArgsConstructor
@Component
@Slf4j
public class RefreshMetadataTask implements Task {

    private final MetadataRefreshService metadataRefreshService;

    @Override
    public TaskCreateResponse execute(TaskCreateRequest request) {
        MetadataRefreshRequest refreshRequest = request.getOptions(MetadataRefreshRequest.class);
        String taskId = request.getTaskId();

        long startTime = System.currentTimeMillis();
        log.info("{}: Task started. TaskId: {}, Options: {}", getTaskType(), taskId, refreshRequest);

        metadataRefreshService.refreshMetadata(refreshRequest, taskId);

        long endTime = System.currentTimeMillis();
        log.info("{}: Task completed. Duration: {} ms", getTaskType(), endTime - startTime);

        return TaskCreateResponse
                .builder()
                .taskType(TaskType.REFRESH_METADATA_MANUAL)
                .taskId(taskId)
                .status(TaskStatus.COMPLETED)
                .build();
    }

    @Override
    public TaskType getTaskType() {
        return TaskType.REFRESH_METADATA_MANUAL;
    }
}
