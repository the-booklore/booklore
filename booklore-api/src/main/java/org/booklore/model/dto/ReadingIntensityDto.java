package org.booklore.model.dto;

public interface ReadingIntensityDto {
    Long getBookId();
    String getBookTitle();
    Integer getDayOffset();
    Long getTotalDurationSeconds();
}
