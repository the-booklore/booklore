package org.booklore.model.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReadingHeartbeatResponse {
    private Long bookId;
    private String bookTitle;
    private Integer pageCount;
    private Instant dateFinished;
    private List<String> categories;
    private Long totalSessions;
    private Long totalDurationSeconds;
    private Long daysToRead;
    private Double pagesPerDay;
}
