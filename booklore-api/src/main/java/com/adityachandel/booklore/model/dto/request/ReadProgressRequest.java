package com.adityachandel.booklore.model.dto.request;

import com.adityachandel.booklore.model.dto.progress.CbxProgress;
import com.adityachandel.booklore.model.dto.progress.EpubProgress;
import com.adityachandel.booklore.model.dto.progress.PdfProgress;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import java.time.Instant;

@Data
public class ReadProgressRequest {
    @NotNull
    private Long bookId;
    private EpubProgress epubProgress;
    private PdfProgress pdfProgress;
    private CbxProgress cbxProgress;
    private Instant dateFinished;

    @AssertTrue(message = "At least one progress field must be provided")
    public boolean isProgressValid() {
        return epubProgress != null || pdfProgress != null || cbxProgress != null || dateFinished != null;
    }
}
