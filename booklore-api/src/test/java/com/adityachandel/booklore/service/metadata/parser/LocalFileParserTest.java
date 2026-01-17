package com.adityachandel.booklore.service.metadata.parser;

import com.adityachandel.booklore.model.dto.Book;
import com.adityachandel.booklore.model.dto.BookMetadata;
import com.adityachandel.booklore.model.enums.BookFileExtension;
import com.adityachandel.booklore.model.enums.MetadataProvider;
import com.adityachandel.booklore.service.metadata.extractor.MetadataExtractorFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LocalFileParserTest {

    @Mock
    private MetadataExtractorFactory metadataExtractorFactory;

    @InjectMocks
    private LocalFileParser localFileParser;

    @TempDir
    Path tempDir;

    @Test
    void fetchTopMetadata_ShouldExtractAndSetProvider_WhenFileExists() throws IOException {
        File epubFile = tempDir.resolve("test.epub").toFile();
        if (!epubFile.createNewFile()) {
            throw new IOException("Failed to create temp file");
        }

        Book book = Book.builder()
                .id(1L)
                .filePath(epubFile.getAbsolutePath())
                .build();

        BookMetadata extractedMetadata = BookMetadata.builder()
                .title("Extracted Title")
                .build();

        when(metadataExtractorFactory.extractMetadata(eq(BookFileExtension.EPUB), any(File.class)))
                .thenReturn(extractedMetadata);

        BookMetadata result = localFileParser.fetchTopMetadata(book, null);

        assertNotNull(result);
        assertEquals("Extracted Title", result.getTitle());
        assertEquals(MetadataProvider.LocalFile, result.getProvider());
        verify(metadataExtractorFactory).extractMetadata(eq(BookFileExtension.EPUB), any(File.class));
    }

    @Test
    void fetchTopMetadata_ShouldReturnNull_WhenFileDoesNotExist() {
        Book book = Book.builder()
                .id(1L)
                .filePath("/non/existent/path/test.epub")
                .build();

        BookMetadata result = localFileParser.fetchTopMetadata(book, null);

        assertNull(result);
    }

    @Test
    void fetchTopMetadata_ShouldReturnNull_WhenExtensionNotSupported() throws IOException {
        File textFile = tempDir.resolve("test.txt").toFile();
        if (!textFile.createNewFile()) {
            throw new IOException("Failed to create temp file");
        }

        Book book = Book.builder()
                .id(1L)
                .filePath(textFile.getAbsolutePath())
                .build();

        BookMetadata result = localFileParser.fetchTopMetadata(book, null);

        assertNull(result);
    }
}
