package org.booklore.model.dto;

public interface ReadingRhythmDto {
    Integer getMonth();
    Integer getHourOfDay();
    Long getSessionCount();
    Long getTotalDurationSeconds();
    String getTopGenre();
}
