package com.adityachandel.booklore.model.dto.request;

import com.adityachandel.booklore.model.dto.progress.AudiobookProgress;
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

    private BookFileProgress fileProgress;

    @Deprecated
    private EpubProgress epubProgress;
    @Deprecated
    private PdfProgress pdfProgress;
    @Deprecated
    private CbxProgress cbxProgress;
    @Deprecated
    private AudiobookProgress audiobookProgress;

    private Instant dateFinished;

    @AssertTrue(message = "At least one progress field must be provided")
    public boolean isProgressValid() {
        return fileProgress != null || epubProgress != null || pdfProgress != null || cbxProgress != null || audiobookProgress != null || dateFinished != null;
    }
}
