package com.adityachandel.booklore.task.tasks;

import com.adityachandel.booklore.model.dto.request.TaskCreateRequest;
import com.adityachandel.booklore.model.dto.response.TaskCreateResponse;
import com.adityachandel.booklore.task.TaskStatus;
import com.adityachandel.booklore.model.enums.TaskType;
import com.adityachandel.booklore.util.FileService;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.UUID;
import java.util.stream.Stream;

@AllArgsConstructor
@Component
@Slf4j
public class ClearCbxCacheTask implements Task {

    private FileService fileService;

    @Override
    public TaskCreateResponse execute(TaskCreateRequest request) {
        log.info("Starting CBX cache clearing task");

        TaskCreateResponse.TaskCreateResponseBuilder builder = TaskCreateResponse.builder()
                .taskId(UUID.randomUUID().toString())
                .taskType(TaskType.CLEAR_CBX_CACHE);

        try {
            String cbxCachePath = fileService.getCbxCachePath();
            Path cachePath = Paths.get(cbxCachePath);

            if (Files.exists(cachePath) && Files.isDirectory(cachePath)) {
                try (Stream<Path> walk = Files.walk(cachePath)) {
                    walk.sorted(Comparator.reverseOrder())
                            .forEach(path -> {
                                try {
                                    Files.delete(path);
                                } catch (IOException e) {
                                    log.error("Failed to delete file: {} - {}", path, e.getMessage());
                                }
                            });
                }

                Files.createDirectories(cachePath);
                log.info("CBX cache cleared and directory recreated");
            } else {
                log.warn("CBX cache path does not exist or is not a directory: {}", cbxCachePath);
            }

            builder.status(TaskStatus.COMPLETED);
            log.info("CBX cache clearing task completed successfully");
        } catch (Exception e) {
            log.error("Error clearing CBX cache", e);
            builder.status(TaskStatus.FAILED);
            throw new RuntimeException("Failed to clear CBX cache", e);
        }

        return builder.build();
    }

    @Override
    public TaskType getTaskType() {
        return TaskType.CLEAR_CBX_CACHE;
    }
}
