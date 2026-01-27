package com.adityachandel.booklore.model.dto.progress;

import jakarta.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Data;

@Builder
@Data
public class AudiobookProgress {
    @NotNull
    Long positionMs;
    Integer trackIndex;
    Long trackPositionMs;
    @NotNull
    Float percentage;
}
