package org.booklore.model.dto;

import java.time.LocalDate;

public interface ReadingSessionCountDto {
    LocalDate getDate();
    Long getCount();
}

