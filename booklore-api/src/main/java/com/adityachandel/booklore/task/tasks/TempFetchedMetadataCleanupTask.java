package com.adityachandel.booklore.task.tasks;

import com.adityachandel.booklore.model.dto.request.TaskCreateRequest;
import com.adityachandel.booklore.model.dto.response.TaskCreateResponse;
import com.adityachandel.booklore.model.enums.TaskType;
import com.adityachandel.booklore.repository.MetadataFetchJobRepository;
import com.adityachandel.booklore.task.TaskStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class TempFetchedMetadataCleanupTask implements Task {

    private final MetadataFetchJobRepository metadataFetchJobRepository;

    @Override
    public TaskCreateResponse execute(TaskCreateRequest request) {
        TaskCreateResponse.TaskCreateResponseBuilder builder = TaskCreateResponse.builder()
                .taskId(UUID.randomUUID().toString())
                .taskType(getTaskType());

        long startTime = System.currentTimeMillis();
        log.info("{}: Task started", getTaskType());

        try {
            Instant cutoff = Instant.now().minus(5, ChronoUnit.DAYS);
            int deleted = metadataFetchJobRepository.deleteAllByCompletedAtBefore(cutoff);
            log.info("{}: Removed {} metadata fetch jobs older than {}", getTaskType(), deleted, cutoff);

            builder.status(TaskStatus.COMPLETED);
        } catch (Exception e) {
            log.error("{}: Error cleaning up temp metadata", getTaskType(), e);
            builder.status(TaskStatus.FAILED);
        }

        long endTime = System.currentTimeMillis();
        log.info("{}: Task completed. Duration: {} ms", getTaskType(), endTime - startTime);

        return builder.build();
    }

    @Override
    public TaskType getTaskType() {
        return TaskType.CLEANUP_TEMP_METADATA;
    }

    @Override
    public String getMetadata() {
        long count = metadataFetchJobRepository.countAll();
        return "Metadata " + (count != 1 ? "rows" : "row") + " pending cleanup: " + count;
    }
}