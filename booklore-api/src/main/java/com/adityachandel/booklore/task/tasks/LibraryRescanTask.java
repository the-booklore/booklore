package com.adityachandel.booklore.task.tasks;

import com.adityachandel.booklore.model.dto.Library;
import com.adityachandel.booklore.model.dto.request.TaskCreateRequest;
import com.adityachandel.booklore.model.dto.response.TaskCreateResponse;
import com.adityachandel.booklore.model.enums.TaskType;
import com.adityachandel.booklore.service.library.LibraryRescanHelper;
import com.adityachandel.booklore.service.library.LibraryService;
import com.adityachandel.booklore.task.options.RescanLibraryContext;
import com.adityachandel.booklore.task.TaskCancellationManager;
import com.adityachandel.booklore.task.options.LibraryRescanOptions;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.InvalidDataAccessApiUsageException;
import org.springframework.stereotype.Component;

import java.util.List;

@AllArgsConstructor
@Component
@Slf4j
public class LibraryRescanTask implements Task {

    private final LibraryService libraryService;
    private final LibraryRescanHelper libraryRescanHelper;
    private final TaskCancellationManager cancellationManager;


    @Override
    public TaskCreateResponse execute(TaskCreateRequest request) {
        LibraryRescanOptions options = request.getOptions(LibraryRescanOptions.class);
        String taskId = request.getTaskId();

        long startTime = System.currentTimeMillis();
        log.info("{}: Task started. TaskId: {}, Options: {}", getTaskType(), taskId, options);

        List<Library> libraries = libraryService.getAllLibraries();

        for (Library library : libraries) {
            if (cancellationManager.isTaskCancelled(taskId)) {
                log.info("{}: Task {} was cancelled, stopping execution", getTaskType(), taskId);
                break;
            }

            Long libraryId = library.getId();
            RescanLibraryContext context = RescanLibraryContext.builder()
                    .libraryId(libraryId)
                    .options(options)
                    .build();
            try {
                libraryRescanHelper.handleRescanOptions(context, taskId);
            } catch (InvalidDataAccessApiUsageException e) {
                log.debug("InvalidDataAccessApiUsageException - Library id: {}", libraryId);
            }
            log.info("{}: Library rescan completed for library: {}", getTaskType(), libraryId);
        }

        long endTime = System.currentTimeMillis();
        log.info("{}: Task completed. Duration: {} ms", getTaskType(), endTime - startTime);

        return null;
    }

    @Override
    public TaskType getTaskType() {
        return TaskType.REFRESH_LIBRARY_METADATA;
    }
}
