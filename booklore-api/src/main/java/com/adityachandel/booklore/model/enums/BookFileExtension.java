package com.adityachandel.booklore.model.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.Arrays;
import java.util.Optional;

@RequiredArgsConstructor
@Getter
public enum BookFileExtension {
    PDF("pdf", BookFileType.PDF),
    EPUB("epub", BookFileType.EPUB),
    CBZ("cbz", BookFileType.CBX),
    CBR("cbr", BookFileType.CBX),
    CB7("cb7", BookFileType.CBX),
    FB2("fb2", BookFileType.FB2);

    private final String extension;
    private final BookFileType type;

    public static Optional<BookFileExtension> fromFileName(String fileName) {
        String lower = fileName.toLowerCase();
        return Arrays.stream(values())
                .filter(e -> lower.endsWith("." + e.extension))
                .findFirst();
    }
}
