package org.booklore.model.dto;

import java.time.LocalDateTime;

public interface BookTimelineDto {
    Long getBookId();
    String getTitle();
    Integer getPageCount();
    LocalDateTime getFirstSessionDate();
    LocalDateTime getLastSessionDate();
    Integer getTotalSessions();
    Long getTotalDurationSeconds();
    Double getMaxProgress();
    String getReadStatus();
}
