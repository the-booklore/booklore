package com.adityachandel.booklore.repository;

import com.adityachandel.booklore.model.entity.BookEntity;
import com.adityachandel.booklore.model.entity.BookMetadataEntity;
import com.adityachandel.booklore.model.entity.LibraryEntity;
import com.adityachandel.booklore.model.entity.LibraryPathEntity;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.context.TestPropertySource;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@TestPropertySource(properties = {
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
class BookOpdsRepositoryDataJpaTest {

    @Autowired
    private BookOpdsRepository bookOpdsRepository;

    @Autowired
    private TestEntityManager entityManager;

    @Test
    void contextLoads() {
        assertThat(bookOpdsRepository).isNotNull();
    }

    @Test
    void findAllWithMetadataByIds_executesAgainstJpaMetamodel() {
        LibraryEntity library = LibraryEntity.builder()
                .name("Test Library")
                .icon("book")
                .watch(false)
                .build();
        library = entityManager.persistAndFlush(library);

        LibraryPathEntity libraryPath = LibraryPathEntity.builder()
                .library(library)
                .path("/test/path")
                .build();
        libraryPath = entityManager.persistAndFlush(libraryPath);

        BookEntity book = BookEntity.builder()
                .library(library)
                .libraryPath(libraryPath)
                .addedOn(Instant.now())
                .deleted(false)
                .build();
        book = entityManager.persistAndFlush(book);

        BookMetadataEntity metadata = BookMetadataEntity.builder()
                .book(book)
                .bookId(book.getId())
                .title("Test Title")
                .build();
        entityManager.persistAndFlush(metadata);

        List<BookEntity> result = bookOpdsRepository.findAllWithMetadataByIds(List.of(book.getId()));
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getId()).isEqualTo(book.getId());
    }
}
