package com.adityachandel.booklore.service.metadata.extractor;

import com.adityachandel.booklore.model.dto.BookMetadata;
import com.adityachandel.booklore.model.enums.BookFileExtension;
import com.adityachandel.booklore.model.enums.BookFileType;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Component;

import java.io.File;

@Component
@AllArgsConstructor
public class MetadataExtractorFactory {

    private final EpubMetadataExtractor epubMetadataExtractor;
    private final PdfMetadataExtractor pdfMetadataExtractor;
    private final CbxMetadataExtractor cbxMetadataExtractor;

    public BookMetadata extractMetadata(BookFileType bookFileType, File file) {
        return switch (bookFileType) {
            case PDF -> pdfMetadataExtractor.extractMetadata(file);
            case EPUB -> epubMetadataExtractor.extractMetadata(file);
            case CBX -> cbxMetadataExtractor.extractMetadata(file);
        };
    }

    public BookMetadata extractMetadata(BookFileExtension fileExt, File file) {
        return switch (fileExt) {
            case PDF -> pdfMetadataExtractor.extractMetadata(file);
            case EPUB -> epubMetadataExtractor.extractMetadata(file);
            case CBZ, CBR, CB7 -> cbxMetadataExtractor.extractMetadata(file);
        };
    }

    public byte[] extractCover(BookFileExtension fileExt, File file) {
        return switch (fileExt) {
            case EPUB -> epubMetadataExtractor.extractCover(file);
            case PDF -> pdfMetadataExtractor.extractCover(file);
            case CBZ, CBR, CB7 -> cbxMetadataExtractor.extractCover(file);
        };
    }
}
