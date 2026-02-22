package org.booklore.model.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReadingIntensityResponse {
    private Long bookId;
    private String bookTitle;
    private Integer dayOffset;
    private Long totalDurationSeconds;
}
