package com.adityachandel.booklore.task.tasks;

import com.adityachandel.booklore.model.dto.request.MetadataRefreshRequest;
import com.adityachandel.booklore.model.dto.request.TaskCreateRequest;
import com.adityachandel.booklore.model.dto.response.TaskCreateResponse;
import com.adityachandel.booklore.model.enums.TaskType;
import com.adityachandel.booklore.service.metadata.MetadataRefreshService;
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
        log.info("Starting RefreshMetadataTask. TaskId: {}, Options: {}", taskId, null);

        metadataRefreshService.refreshMetadata(refreshRequest, taskId);

        return null;
    }

    @Override
    public TaskType getTaskType() {
        return TaskType.REFRESH_METADATA;
    }
}
