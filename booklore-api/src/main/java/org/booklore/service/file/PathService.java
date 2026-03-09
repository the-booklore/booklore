package org.booklore.service.file;

import lombok.extern.slf4j.Slf4j;
import org.booklore.exception.ApiError;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Slf4j
@Service
public class PathService {

    private static final Set<String> BLOCKED_PATHS = Set.of(
            "/proc", "/sys", "/dev", "/run", "/var/run"
    );

    public List<String> getFoldersAtPath(String path) {
        Path directory = Paths.get(path).toAbsolutePath().normalize();
        String normalized = directory.toString();

        if (BLOCKED_PATHS.stream().anyMatch(blocked -> normalized.equals(blocked) || normalized.startsWith(blocked + "/"))) {
            log.warn("Blocked path browsing attempt to restricted directory: {}", normalized);
            throw ApiError.GENERIC_BAD_REQUEST.createException("Access to this directory is not allowed");
        }

        if (!Files.exists(directory) || !Files.isDirectory(directory)) {
            log.warn("Invalid path or not a directory: {}", path);
            return Collections.emptyList();
        }
        try (Stream<Path> paths = Files.list(directory)) {
            return paths
                    .filter(Files::isDirectory)
                    .map(p -> directory.resolve(p.getFileName()).toString())
                    .sorted()
                    .collect(Collectors.toList());
        } catch (IOException e) {
            log.error("Error accessing path {}: {}", path, e.getMessage(), e);
            return Collections.emptyList();
        }
    }
}
