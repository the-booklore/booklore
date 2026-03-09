package org.booklore.service.monitoring;

import java.nio.file.Path;
import java.nio.file.WatchEvent;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

@Getter
public class FileChangeEvent extends ApplicationEvent {

    private final Path filePath;
    private final WatchEvent.Kind<?> eventKind;
    private final Path watchedFolder;

    public FileChangeEvent(Object source, Path filePath, WatchEvent.Kind<?> eventKind, Path watchedFolder) {
        super(source);
        this.filePath = filePath;
        this.eventKind = eventKind;
        this.watchedFolder = watchedFolder;
    }
}