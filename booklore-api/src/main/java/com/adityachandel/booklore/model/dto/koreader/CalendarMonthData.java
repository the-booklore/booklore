package com.adityachandel.booklore.model.dto.koreader;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CalendarMonthData {
    private int year;
    private int month;
    private List<CalendarDayData> days;
    private List<BookReadingSpan> bookSpans;
}
