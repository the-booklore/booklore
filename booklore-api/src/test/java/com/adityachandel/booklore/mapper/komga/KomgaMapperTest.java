package com.adityachandel.booklore.mapper.komga;

import com.adityachandel.booklore.model.dto.komga.KomgaBookDto;
import com.adityachandel.booklore.model.entity.BookEntity;
import com.adityachandel.booklore.model.entity.BookMetadataEntity;
import com.adityachandel.booklore.model.entity.LibraryEntity;
import com.adityachandel.booklore.model.enums.BookFileType;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class KomgaMapperTest {

    private final KomgaMapper mapper = new KomgaMapper();

    @Test
    void shouldHandleNullPageCountInMetadata() {
        // Given: A book with metadata that has null pageCount
        LibraryEntity library = new LibraryEntity();
        library.setId(1L);

        BookMetadataEntity metadata = BookMetadataEntity.builder()
                .title("Test Book")
                .seriesName("Test Series")
                .pageCount(null)  // Explicitly null
                .build();

        BookEntity book = new BookEntity();
        book.setId(100L);
        book.setFileName("test-book.pdf");
        book.setLibrary(library);
        book.setMetadata(metadata);
        book.setBookType(BookFileType.PDF);
        book.setAddedOn(Instant.now());

        // When: Converting to DTO
        KomgaBookDto dto = mapper.toKomgaBookDto(book);

        // Then: Should not throw NPE and pageCount should default to 0
        assertThat(dto).isNotNull();
        assertThat(dto.getMedia()).isNotNull();
        assertThat(dto.getMedia().getPagesCount()).isEqualTo(0);
    }

    @Test
    void shouldHandleNullMetadata() {
        // Given: A book with null metadata
        LibraryEntity library = new LibraryEntity();
        library.setId(1L);

        BookEntity book = new BookEntity();
        book.setId(100L);
        book.setFileName("test-book.pdf");
        book.setLibrary(library);
        book.setMetadata(null);  // Null metadata
        book.setBookType(BookFileType.PDF);
        book.setAddedOn(Instant.now());

        // When: Converting to DTO
        KomgaBookDto dto = mapper.toKomgaBookDto(book);

        // Then: Should not throw NPE and pageCount should default to 0
        assertThat(dto).isNotNull();
        assertThat(dto.getMedia()).isNotNull();
        assertThat(dto.getMedia().getPagesCount()).isEqualTo(0);
    }

    @Test
    void shouldHandleValidPageCount() {
        // Given: A book with metadata that has valid pageCount
        LibraryEntity library = new LibraryEntity();
        library.setId(1L);

        BookMetadataEntity metadata = BookMetadataEntity.builder()
                .title("Test Book")
                .seriesName("Test Series")
                .pageCount(250)
                .build();

        BookEntity book = new BookEntity();
        book.setId(100L);
        book.setFileName("test-book.pdf");
        book.setLibrary(library);
        book.setMetadata(metadata);
        book.setBookType(BookFileType.PDF);
        book.setAddedOn(Instant.now());

        // When: Converting to DTO
        KomgaBookDto dto = mapper.toKomgaBookDto(book);

        // Then: Should use the actual pageCount
        assertThat(dto).isNotNull();
        assertThat(dto.getMedia()).isNotNull();
        assertThat(dto.getMedia().getPagesCount()).isEqualTo(250);
    }
}