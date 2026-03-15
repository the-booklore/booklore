package org.booklore.model.dto;

import java.time.LocalDate;

public interface ListeningHeatmapDto {
    LocalDate getDate();
    Long getSessions();
    Long getDurationMinutes();
}
