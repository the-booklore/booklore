package com.adityachandel.booklore.model.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PeakReadingHoursResponse {
    private Integer hourOfDay;
    private Long sessionCount;
    private Long totalDurationSeconds;
}

