package org.booklore.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.WatchService;

@Slf4j
@Configuration
public class MonitoringConfig {

    @Bean
    public WatchService watchService() {
        WatchService watchService = null;
        try {
            watchService = FileSystems.getDefault().newWatchService();
        } catch (IOException e) {
            log.error("Error creating WatchService:", e);
        }
        return watchService;
    }
}
