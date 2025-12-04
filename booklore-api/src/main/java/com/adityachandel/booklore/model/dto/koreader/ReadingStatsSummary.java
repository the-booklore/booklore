package com.adityachandel.booklore.model.dto.koreader;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReadingStatsSummary {
    private long totalReadingTimeSeconds;
    private long totalPagesRead;
    private long longestDaySeconds;
    private LocalDate longestDayDate;
    private long mostPagesInDay;
    private LocalDate mostPagesDate;
}
