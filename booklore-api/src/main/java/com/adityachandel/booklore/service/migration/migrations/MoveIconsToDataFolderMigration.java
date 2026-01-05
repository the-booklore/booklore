package com.adityachandel.booklore.service.migration.migrations;

import com.adityachandel.booklore.service.migration.Migration;
import com.adityachandel.booklore.util.FileService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

@Slf4j
@Component
@RequiredArgsConstructor
public class MoveIconsToDataFolderMigration implements Migration {

    private final FileService fileService;

    @Override
    public String getKey() {
        return "moveIconsToDataFolder";
    }

    @Override
    public String getDescription() {
        return "Move SVG icons from resources/static/images/icons/svg to data/icons/svg";
    }

    @Override
    public void execute() {
        long start = System.nanoTime();
        log.info("Starting migration: {}", getKey());

        try {
            String targetFolder = fileService.getIconsSvgFolder();
            Path targetDir = Paths.get(targetFolder);
            Files.createDirectories(targetDir);

            PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
            Resource[] resources = resolver.getResources("classpath:static/images/icons/svg/*.svg");

            int copiedCount = 0;
            for (Resource resource : resources) {
                String filename = resource.getFilename();
                if (filename == null) continue;

                Path targetFile = targetDir.resolve(filename);

                try (var inputStream = resource.getInputStream()) {
                    Files.copy(inputStream, targetFile, StandardCopyOption.REPLACE_EXISTING);
                    copiedCount++;
                    log.debug("Copied icon: {} to {}", filename, targetFile);
                } catch (IOException e) {
                    log.error("Failed to copy icon: {}", filename, e);
                }
            }

            log.info("Copied {} SVG icons from resources to data folder", copiedCount);

            long elapsedMs = (System.nanoTime() - start) / 1_000_000;
            log.info("Completed migration: {} in {} ms", getKey(), elapsedMs);
        } catch (IOException e) {
            log.error("Error during migration {}", getKey(), e);
            throw new UncheckedIOException(e);
        }
    }
}

