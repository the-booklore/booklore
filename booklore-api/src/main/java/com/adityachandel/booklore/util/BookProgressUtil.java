package com.adityachandel.booklore.util;

import com.adityachandel.booklore.model.dto.*;
import com.adityachandel.booklore.model.dto.progress.*;
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

        BookFileType type = book.getBookType();
        if (type == null) return;

        switch (type) {
            case EPUB -> {
                book.setEpubProgress(EpubProgress.builder()
                        .cfi(progress.getEpubProgress())
                        .href(progress.getEpubProgressHref())
                        .percentage(progress.getEpubProgressPercent())
                        .build());
                book.setKoreaderProgress(KoProgress.builder()
                        .percentage(progress.getKoreaderProgressPercent() != null ? progress.getKoreaderProgressPercent() * 100 : null)
                        .build());
            }
            case FB2, MOBI, AZW3 -> book.setEpubProgress(EpubProgress.builder()
                    .cfi(progress.getEpubProgress())
                    .href(progress.getEpubProgressHref())
                    .percentage(progress.getEpubProgressPercent())
                    .build());
            case PDF -> book.setPdfProgress(PdfProgress.builder()
                    .page(progress.getPdfProgress())
                    .percentage(progress.getPdfProgressPercent())
                    .build());
            case CBX -> book.setCbxProgress(CbxProgress.builder()
                    .page(progress.getCbxProgress())
                    .percentage(progress.getCbxProgressPercent())
                    .build());
        }
    }

    public static void enrichBookWithProgress(Book book, UserBookProgressEntity progress) {
        if (progress != null) {
            setBookProgress(book, progress);
            book.setLastReadTime(progress.getLastReadTime());
            book.setReadStatus(progress.getReadStatus() == null ? String.valueOf(ReadStatus.UNSET) : String.valueOf(progress.getReadStatus()));
            book.setDateFinished(progress.getDateFinished());
            book.setPersonalRating(progress.getPersonalRating());
        }
    }
}

