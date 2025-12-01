package com.adityachandel.booklore.config;

import com.adityachandel.booklore.service.appsettings.AppSettingService;
import jakarta.servlet.MultipartConfigElement;
import org.springframework.boot.web.servlet.MultipartConfigFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.unit.DataSize;

@Configuration
public class MultipartConfig {

    @Bean
    public MultipartConfigElement multipartConfigElement(AppSettingService appSettingService) {
        MultipartConfigFactory factory = new MultipartConfigFactory();
        long maxSizeMb = appSettingService.getAppSettings().getMaxFileUploadSizeInMb();

        factory.setMaxFileSize(DataSize.ofMegabytes(maxSizeMb));
        factory.setMaxRequestSize(DataSize.ofMegabytes(maxSizeMb));
        return factory.createMultipartConfig();
    }
}
