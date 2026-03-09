package org.booklore.config;

import jakarta.servlet.MultipartConfigElement;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.unit.DataSize;

@Configuration
public class MultipartConfig {

    private static final long DEFAULT_MAX_UPLOAD_SIZE_MB = 1024;

    /**
     * Provides a MultipartConfigElement with a generous default max upload size.
     * The actual user-configured limit from app_settings is enforced at the service layer.
     * This bean is created during servlet container initialization (before Flyway migrations run),
     * so it must NOT query the database.
     */
    @Bean
    public MultipartConfigElement multipartConfigElement() {
        long maxSizeBytes = DataSize.ofMegabytes(DEFAULT_MAX_UPLOAD_SIZE_MB).toBytes();
        return new MultipartConfigElement("", maxSizeBytes, maxSizeBytes, 0);
    }
}
