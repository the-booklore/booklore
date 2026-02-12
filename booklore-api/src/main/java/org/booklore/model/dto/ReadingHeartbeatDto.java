package org.booklore.model.dto;

import java.time.Instant;

public interface ReadingHeartbeatDto {
    Long getBookId();
    String getBookTitle();
    Integer getPageCount();
    Instant getDateFinished();
    Instant getFirstSessionDate();
    Long getTotalSessions();
    Long getTotalDurationSeconds();
}
