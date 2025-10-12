package com.adityachandel.booklore.service;

import com.adityachandel.booklore.model.enums.TaskType;
import com.adityachandel.booklore.util.FileService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;

@Service
@RequiredArgsConstructor
@Slf4j
public class TaskMetadataService {

    private final FileService fileService;

    public Map<String, Object> getMetadataForTaskType(TaskType taskType) {
        Map<String, Object> metadata = new HashMap<>();

        switch (taskType) {
            case CLEAR_CBX_CACHE:
                metadata.putAll(getCacheMetadata(fileService.getCbxCachePath(), "CBX"));
                break;
            case CLEAR_PDF_CACHE:
                metadata.putAll(getCacheMetadata(fileService.getPdfCachePath(), "PDF"));
                break;
            default:
                break;
        }

        return metadata;
    }

    private Map<String, Object> getCacheMetadata(String cachePathStr, String cacheType) {
        Map<String, Object> metadata = new HashMap<>();

        try {
            Path cachePath = Paths.get(cachePathStr);

            if (Files.exists(cachePath) && Files.isDirectory(cachePath)) {
                long sizeInBytes = calculateDirectorySize(cachePath);
                String formattedSize = formatBytes(sizeInBytes);

                metadata.put("currentCacheSize", formattedSize);
                metadata.put("currentCacheSizeBytes", sizeInBytes);
            } else {
                metadata.put("currentCacheSize", "0 B");
                metadata.put("currentCacheSizeBytes", 0L);
            }
        } catch (Exception e) {
            log.error("Error calculating {} cache size", cacheType, e);
            metadata.put("currentCacheSize", "Unknown");
            metadata.put("currentCacheSizeBytes", -1L);
        }

        return metadata;
    }

    private long calculateDirectorySize(Path path) throws IOException {
        try (Stream<Path> walk = Files.walk(path)) {
            return walk
                    .filter(Files::isRegularFile)
                    .mapToLong(p -> {
                        try {
                            return Files.size(p);
                        } catch (IOException e) {
                            log.warn("Could not get size of file: {}", p, e);
                            return 0L;
                        }
                    })
                    .sum();
        }
    }

    private String formatBytes(long bytes) {
        if (bytes < 1024) return bytes + " B";
        int exp = (int) (Math.log(bytes) / Math.log(1024));
        String pre = "KMGTPE".charAt(exp - 1) + "";
        return String.format("%.1f %sB", bytes / Math.pow(1024, exp), pre);
    }
}
