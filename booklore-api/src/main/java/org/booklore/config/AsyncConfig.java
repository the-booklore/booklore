package com.adityachandel.booklore.config;

import lombok.AllArgsConstructor;
import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.annotation.EnableAsync;

@Configuration
@EnableAsync
@AllArgsConstructor
public class AsyncConfig implements AsyncConfigurer {

    private final CustomAsyncExceptionHandler customAsyncExceptionHandler;

    @Override
    public AsyncUncaughtExceptionHandler getAsyncUncaughtExceptionHandler() {
        return customAsyncExceptionHandler;
    }
}