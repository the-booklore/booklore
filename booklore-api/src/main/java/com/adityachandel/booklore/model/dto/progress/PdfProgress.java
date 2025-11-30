package com.adityachandel.booklore.model.dto.progress;

import jakarta.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Data;

@Builder
@Data
public class PdfProgress {
    @NotNull
    Integer page;
    @NotNull
    Float percentage;
}
