package com.adityachandel.booklore.task;

import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class TaskCancellationManager {

    private final Set<String> cancelledTasks = ConcurrentHashMap.newKeySet();

    public void cancelTask(String taskId) {
        cancelledTasks.add(taskId);
    }

    public boolean isTaskCancelled(String taskId) {
        return cancelledTasks.contains(taskId);
    }

    public void clearCancellation(String taskId) {
        cancelledTasks.remove(taskId);
    }
}

