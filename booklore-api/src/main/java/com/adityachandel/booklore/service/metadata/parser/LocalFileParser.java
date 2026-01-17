package com.adityachandel.booklore.service.metadata.parser;

import com.adityachandel.booklore.model.dto.Book;
import com.adityachandel.booklore.model.dto.BookMetadata;
import com.adityachandel.booklore.model.dto.request.FetchMetadataRequest;
import com.adityachandel.booklore.model.enums.BookFileExtension;
import com.adityachandel.booklore.model.enums.MetadataProvider;
import com.adityachandel.booklore.service.metadata.extractor.MetadataExtractorFactory;
import com.adityachandel.booklore.util.FileUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.File;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class LocalFileParser implements BookParser {

    private final MetadataExtractorFactory metadataExtractorFactory;

    @Override
    public List<BookMetadata> fetchMetadata(Book book, FetchMetadataRequest fetchMetadataRequest) {
        BookMetadata metadata = fetchTopMetadata(book, fetchMetadataRequest);
        return metadata != null ? List.of(metadata) : Collections.emptyList();
    }

    @Override
    public BookMetadata fetchTopMetadata(Book book, FetchMetadataRequest fetchMetadataRequest) {
        
        if (book.getFilePath() == null) {
            log.debug("No file path available for book ID: {}, cannot extract local metadata", book.getId());
            return null;
        }

        File file = new File(book.getFilePath());
        if (!file.exists()) {
            log.warn("File not found at path: {}", book.getFilePath());
            return null;
        }

        Optional<BookFileExtension> extensionOpt = BookFileExtension.fromFileName(file.getName());
        if (extensionOpt.isEmpty()) {
            return null;
        }

        try {
            BookMetadata metadata = metadataExtractorFactory.extractMetadata(extensionOpt.get(), file);
            if (metadata != null) {
                metadata.setProvider(MetadataProvider.LocalFile);
                return metadata;
            }
        } catch (Exception e) {
            log.error("Failed to extract metadata from local file: {}", file.getAbsolutePath(), e);
        }
        return null;
    }
}
