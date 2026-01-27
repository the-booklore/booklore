package com.adityachandel.booklore.mapper;

import com.adityachandel.booklore.model.dto.Book;
import com.adityachandel.booklore.model.entity.BookFileEntity;
import com.adityachandel.booklore.model.entity.BookEntity;
import com.adityachandel.booklore.model.entity.LibraryEntity;
import com.adityachandel.booklore.model.entity.LibraryPathEntity;
import com.adityachandel.booklore.model.enums.BookFileType;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class BookMapperTest {

    private final BookMapper mapper = Mappers.getMapper(BookMapper.class);

    @Test
    void shouldMapExistingFieldsCorrectly() {
        LibraryEntity library = new LibraryEntity();
        library.setId(123L);
        library.setName("Test Library");

        LibraryPathEntity libraryPath = new LibraryPathEntity();
        libraryPath.setId(1L);
        libraryPath.setPath("/tmp");

        BookEntity entity = new BookEntity();
        entity.setId(1L);

        BookFileEntity primaryFile = new BookFileEntity();
        primaryFile.setBook(entity);
        primaryFile.setFileName("Test Book");
        primaryFile.setFileSubPath(".");
        primaryFile.setBookFormat(true);
        primaryFile.setBookType(BookFileType.EPUB);
        entity.setBookFiles(List.of(primaryFile));
        entity.setLibrary(library);
        entity.setLibraryPath(libraryPath);

        Book dto = mapper.toBook(entity);

        assertThat(dto).isNotNull();
        assertThat(dto.getId()).isEqualTo(1L);
        assertThat(dto.getLibraryId()).isEqualTo(123L);
        assertThat(dto.getLibraryName()).isEqualTo("Test Library");
        assertThat(dto.getLibraryPath()).isNotNull();
        assertThat(dto.getLibraryPath().getId()).isEqualTo(1L);
        assertThat(dto.getPrimaryFile().getBookType()).isEqualTo(BookFileType.EPUB);
        assertThat(dto.getAlternativeFormats()).isEmpty();
        assertThat(dto.getSupplementaryFiles()).isEmpty();

    }
}