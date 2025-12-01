package com.adityachandel.booklore.model.dto.progress;

import jakarta.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Data;

@Builder
@Data
public class CbxProgress {
    @NotNull
    Integer page;
    @NotNull
    Float percentage;
}
