package com.adityachandel.booklore.task.tasks;

import com.adityachandel.booklore.model.dto.Library;
import com.adityachandel.booklore.model.dto.request.TaskCreateRequest;
import com.adityachandel.booklore.model.dto.response.TaskCreateResponse;
import com.adityachandel.booklore.model.enums.TaskType;
import com.adityachandel.booklore.service.library.LibraryService;
import com.adityachandel.booklore.task.TaskStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class LibraryScanTask implements Task {

    private final LibraryService libraryService;

    @Override
    public TaskCreateResponse execute(TaskCreateRequest request) {
        TaskCreateResponse.TaskCreateResponseBuilder builder = TaskCreateResponse.builder()
                .taskId(UUID.randomUUID().toString())
                .taskType(getTaskType());

        long startTime = System.currentTimeMillis();
        log.info("{}: Task started", getTaskType());

        try {
            for (Library library : libraryService.getAllLibraries()) {
                try {
                    libraryService.rescanLibrary(library.getId());
                    log.info("{}: Rescanned library '{}'", getTaskType(), library.getName());
                } catch (Exception e) {
                    log.error("{}: Failed to rescan library '{}': {}", getTaskType(), library.getName(), e.getMessage(), e);
                }
            }

            builder.status(TaskStatus.COMPLETED);
        } catch (Exception e) {
            log.error("{}: Error scanning libraries", getTaskType(), e);
            builder.status(TaskStatus.FAILED);
        }

        long endTime = System.currentTimeMillis();
        log.info("{}: Task completed. Duration: {} ms", getTaskType(), endTime - startTime);

        return builder.build();
    }

    @Override
    public TaskType getTaskType() {
        return TaskType.SYNC_LIBRARY_FILES;
    }
}