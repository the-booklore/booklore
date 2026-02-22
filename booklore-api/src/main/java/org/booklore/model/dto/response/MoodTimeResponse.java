package org.booklore.model.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MoodTimeResponse {
    private String mood;
    private Integer hourOfDay;
    private Long totalDurationSeconds;
    private Long sessionCount;
}
