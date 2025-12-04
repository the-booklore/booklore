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
public class BookReadingSpan {
    private Long bookId;
    private String title;
    private LocalDate startDate;
    private LocalDate endDate;
    private int row;
    private long totalDurationSeconds;
    private long totalPagesRead;
}
