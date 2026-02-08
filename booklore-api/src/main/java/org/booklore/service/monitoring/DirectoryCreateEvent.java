package org.booklore.service.monitoring;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

import java.nio.file.Path;

@Getter
public class DirectoryCreateEvent extends ApplicationEvent {
    private final Path newDirectory;

    public DirectoryCreateEvent(Object source, Path newDirectory) {
        super(source);
        this.newDirectory = newDirectory;
    }
}
