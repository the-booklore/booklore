package org.booklore.config;

import jakarta.servlet.MultipartConfigElement;
import org.booklore.service.appsettings.AppSettingService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;
import org.springframework.util.unit.DataSize;

@Configuration
@DependsOn("flyway")
public class MultipartConfig {

    @Bean
    public MultipartConfigElement multipartConfigElement(AppSettingService appSettingService) {
        long maxSizeMb = appSettingService.getAppSettings().getMaxFileUploadSizeInMb();
        long maxSizeBytes = DataSize.ofMegabytes(maxSizeMb).toBytes();
        return new MultipartConfigElement("", maxSizeBytes, maxSizeBytes, 0);
    }
}
