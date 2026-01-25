package com.adityachandel.booklore.util;

import com.adityachandel.booklore.model.dto.*;
import com.adityachandel.booklore.model.dto.progress.*;
import com.adityachandel.booklore.model.entity.UserBookFileProgressEntity;
import com.adityachandel.booklore.model.entity.UserBookProgressEntity;
import com.adityachandel.booklore.model.enums.BookFileType;
import com.adityachandel.booklore.model.enums.ReadStatus;

public class BookProgressUtil {

    public static void setBookProgress(Book book, UserBookProgressEntity progress) {
        if (progress.getKoboProgressPercent() != null) {
            book.setKoboProgress(KoboProgress.builder()
                    .percentage(progress.getKoboProgressPercent())
                    .build());
        }

        // Set KoReader progress if available
        if (progress.getKoreaderProgressPercent() != null) {
            book.setKoreaderProgress(KoProgress.builder()
                    .percentage(progress.getKoreaderProgressPercent() * 100)
                    .build());
        }

        // Set ALL available progress types (not just primary file's type)
        // This ensures alternative formats can access their progress
        if (progress.getEpubProgress() != null || progress.getEpubProgressPercent() != null) {
            book.setEpubProgress(EpubProgress.builder()
                    .cfi(progress.getEpubProgress())
                    .href(progress.getEpubProgressHref())
                    .percentage(progress.getEpubProgressPercent())
                    .build());
        }

        if (progress.getPdfProgress() != null || progress.getPdfProgressPercent() != null) {
            book.setPdfProgress(PdfProgress.builder()
                    .page(progress.getPdfProgress())
                    .percentage(progress.getPdfProgressPercent())
                    .build());
        }

        if (progress.getCbxProgress() != null || progress.getCbxProgressPercent() != null) {
            book.setCbxProgress(CbxProgress.builder()
                    .page(progress.getCbxProgress())
                    .percentage(progress.getCbxProgressPercent())
                    .build());
        }
    }

    public static void setBookProgressFromFileProgress(Book book, UserBookFileProgressEntity fileProgress) {
        // Use the book file's type, not the primary file's type
        BookFileType type = fileProgress.getBookFile() != null ? fileProgress.getBookFile().getBookType() : null;
        if (type == null) return;

        switch (type) {
            case EPUB, FB2, MOBI, AZW3 -> book.setEpubProgress(EpubProgress.builder()
                    .cfi(fileProgress.getPositionData())
                    .href(fileProgress.getPositionHref())
                    .percentage(fileProgress.getProgressPercent())
                    .build());
            case PDF -> book.setPdfProgress(PdfProgress.builder()
                    .page(parseIntOrNull(fileProgress.getPositionData()))
                    .percentage(fileProgress.getProgressPercent())
                    .build());
            case CBX -> book.setCbxProgress(CbxProgress.builder()
                    .page(parseIntOrNull(fileProgress.getPositionData()))
                    .percentage(fileProgress.getProgressPercent())
                    .build());
        }
    }

    public static void enrichBookWithProgress(Book book, UserBookProgressEntity progress) {
        enrichBookWithProgress(book, progress, null);
    }

    public static void enrichBookWithProgress(Book book, UserBookProgressEntity progress,
                                               UserBookFileProgressEntity fileProgress) {
        if (progress != null) {
            // Set read status, date finished, and personal rating from book-level progress
            book.setReadStatus(progress.getReadStatus() == null ?
                    String.valueOf(ReadStatus.UNSET) : String.valueOf(progress.getReadStatus()));
            book.setDateFinished(progress.getDateFinished());
            book.setPersonalRating(progress.getPersonalRating());

            // Set ALL progress from book-level (covers all file types)
            setBookProgress(book, progress);
            book.setLastReadTime(progress.getLastReadTime());
        }

        // Overlay file-level progress if available (this is more accurate for the specific file)
        if (fileProgress != null) {
            setBookProgressFromFileProgress(book, fileProgress);
            // Update lastReadTime if file progress is more recent
            if (progress == null || fileProgress.getLastReadTime() != null &&
                    (progress.getLastReadTime() == null ||
                     fileProgress.getLastReadTime().isAfter(progress.getLastReadTime()))) {
                book.setLastReadTime(fileProgress.getLastReadTime());
            }
        }
    }

    private static Integer parseIntOrNull(String value) {
        if (value == null) return null;
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}

