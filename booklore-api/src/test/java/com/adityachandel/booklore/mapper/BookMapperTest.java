package com.adityachandel.booklore.mapper;

import com.adityachandel.booklore.model.dto.Book;
import com.adityachandel.booklore.model.entity.BookFileEntity;
import com.adityachandel.booklore.model.entity.BookEntity;
import com.adityachandel.booklore.model.entity.LibraryEntity;
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

        BookEntity entity = new BookEntity();
        entity.setId(1L);

        BookFileEntity primaryFile = new BookFileEntity();
        primaryFile.setBook(entity);
        primaryFile.setFileName("Test Book");
        primaryFile.setFileSubPath(".");
        entity.setBookFiles(List.of(primaryFile));
        entity.setLibrary(library);

        Book dto = mapper.toBook(entity);

        assertThat(dto).isNotNull();
        assertThat(dto.getId()).isEqualTo(1L);
        assertThat(dto.getFileName()).isEqualTo("Test Book");
        assertThat(dto.getLibraryId()).isEqualTo(123L);

    }
}