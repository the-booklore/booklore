package com.adityachandel.booklore.config;

import com.adityachandel.booklore.service.task.TaskService;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@EnableScheduling
@AllArgsConstructor
@Slf4j
public class TaskSchedulerConfig {

    private final TaskService taskService;

    @EventListener(ApplicationReadyEvent.class)
    public void initializeScheduledTasks() {
        log.info("Application ready, initializing scheduled tasks");
        taskService.initializeScheduledTasks();
    }
}
