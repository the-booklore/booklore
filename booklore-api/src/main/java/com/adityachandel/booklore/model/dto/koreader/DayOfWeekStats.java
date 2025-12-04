package com.adityachandel.booklore.model.dto.koreader;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DayOfWeekStats {
    private String dayName;
    private int dayIndex; // 0 = Monday, 6 = Sunday
    private long totalDurationSeconds;
    private long totalPagesRead;
    private int sessionCount;
}
