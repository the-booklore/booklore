package org.booklore.service.monitoring;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

import java.nio.file.Path;

@Getter
public class WatchKeyInvalidatedEvent extends ApplicationEvent {
    private final Path invalidPath;

    public WatchKeyInvalidatedEvent(Object source, Path invalidPath) {
        super(source);
        this.invalidPath = invalidPath;
    }
}
