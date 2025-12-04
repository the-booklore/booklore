package com.adityachandel.booklore.model.dto.koreader;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CalendarDayData {
    private LocalDate date;
    private long totalDurationSeconds;
    private long totalPagesRead;
    private List<BookDayReading> books;
}
