package com.adityachandel.booklore.model.dto.request;

import com.adityachandel.booklore.model.enums.BookFileType;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ReadingSessionRequest {
    @NotNull
    private Long bookId;

    private BookFileType bookType;

    @NotNull
    private Instant startTime;

    @NotNull
    private Instant endTime;

    @NotNull
    private Integer durationSeconds;

    @NotNull
    private Float startProgress;

    @NotNull
    private Float endProgress;

    @NotNull
    private Float progressDelta;

    @NotNull
    private String startLocation;

    @NotNull
    private String endLocation;
}
