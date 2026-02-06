package org.booklore.config;

import lombok.RequiredArgsConstructor;
import org.booklore.interceptor.KomgaCleanInterceptor;
import org.booklore.interceptor.KomgaEnabledInterceptor;
import org.booklore.interceptor.OpdsEnabledInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.VirtualThreadTaskExecutor;
import org.springframework.data.web.config.EnableSpringDataWebSupport;
import org.springframework.web.servlet.config.annotation.AsyncSupportConfigurer;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import static org.springframework.data.web.config.EnableSpringDataWebSupport.PageSerializationMode.VIA_DTO;

@Configuration
@EnableSpringDataWebSupport(pageSerializationMode = VIA_DTO)
@RequiredArgsConstructor
public class WebMvcConfig implements WebMvcConfigurer {

    private final OpdsEnabledInterceptor opdsEnabledInterceptor;
    private final KomgaEnabledInterceptor komgaEnabledInterceptor;
    private final KomgaCleanInterceptor komgaCleanInterceptor;

    @Override
    public void configureAsyncSupport(AsyncSupportConfigurer configurer) {
        configurer.setTaskExecutor(new VirtualThreadTaskExecutor("mvc-async-"));
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(opdsEnabledInterceptor)
                .addPathPatterns("/api/v1/opds/**", "/api/v2/opds/**");
        registry.addInterceptor(komgaEnabledInterceptor)
                .addPathPatterns("/komga/api/**");
        registry.addInterceptor(komgaCleanInterceptor)
                .addPathPatterns("/komga/api/**");
    }
}
