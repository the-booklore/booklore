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
public class DailyReadingStats {
    private LocalDate date;
    private long durationSeconds;
    private long pagesRead;
}
