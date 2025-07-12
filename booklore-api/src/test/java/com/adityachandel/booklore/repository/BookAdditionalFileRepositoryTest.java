package com.adityachandel.booklore.repository;

import com.adityachandel.booklore.model.entity.*;
import com.adityachandel.booklore.model.enums.AdditionalFileType;
import com.adityachandel.booklore.model.enums.BookFileType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MariaDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.io.File;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test for BookAdditionalFileRepository using TestContainers with MariaDB.
 * This provides real database testing with the same database engine used in production.
 */
@DataJpaTest
@Testcontainers
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class BookAdditionalFileRepositoryTest {

    @Container
    static MariaDBContainer<?> mariaDB = new MariaDBContainer<>("mariadb:11.4.5")
            .withDatabaseName("booklore_test")
            .withUsername("test")
            .withPassword("test");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", mariaDB::getJdbcUrl);
        registry.add("spring.datasource.username", mariaDB::getUsername);
        registry.add("spring.datasource.password", mariaDB::getPassword);
        registry.add("spring.datasource.driver-class-name", mariaDB::getDriverClassName);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "none");
        registry.add("spring.flyway.enabled", () -> "true");
        registry.add("spring.flyway.clean-disabled", () -> "false");
        registry.add("spring.flyway.baseline-on-migrate", () -> "true");
    }

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private BookAdditionalFileRepository additionalFileRepository;

    private BookEntity testBook;
    private LibraryEntity testLibrary;
    private LibraryPathEntity testLibraryPath;

    @BeforeEach
    void setUp() {
        // Create test library
        testLibrary = LibraryEntity.builder()
                .name("Test Library")
                .icon("book")
                .watch(false)
                .build();
        testLibrary = entityManager.persistAndFlush(testLibrary);

        // Create test library path
        testLibraryPath = LibraryPathEntity.builder()
                .path("/test/path")
                .library(testLibrary)
                .build();
        testLibraryPath = entityManager.persistAndFlush(testLibraryPath);

        // Create test book
        testBook = BookEntity.builder()
                .fileName("test-book.pdf")
                .fileSubPath("subfolder")
                .bookType(BookFileType.PDF)
                .fileSizeKb(1024L)
                .library(testLibrary)
                .libraryPath(testLibraryPath)
                .addedOn(Instant.now())
                .initialHash("initial-hash")
                .currentHash("current-hash")
                .deleted(false)
                .build();
        testBook = entityManager.persistAndFlush(testBook);
    }

    @Test
    void shouldSaveAndFindAdditionalFile() {
        // Given
        BookAdditionalFileEntity additionalFile = BookAdditionalFileEntity.builder()
                .book(testBook)
                .fileName("source-code.zip")
                .fileSubPath("subfolder")
                .additionalFileType(AdditionalFileType.SUPPLEMENTARY)
                .fileSizeKb(512L)
                .initialHash("zip-initial-hash")
                .currentHash("zip-current-hash")
                .description("Source code archive")
                .addedOn(Instant.now())
                .build();

        // When
        BookAdditionalFileEntity saved = additionalFileRepository.saveAndFlush(additionalFile);
        Optional<BookAdditionalFileEntity> found = additionalFileRepository.findById(saved.getId());

        // Then
        assertThat(found).isPresent();
        assertThat(found.get().getFileName()).isEqualTo("source-code.zip");
        assertThat(found.get().getAdditionalFileType()).isEqualTo(AdditionalFileType.SUPPLEMENTARY);
        assertThat(found.get().getBook().getId()).isEqualTo(testBook.getId());
        assertThat(found.get().getFileSizeKb()).isEqualTo(512L);
        assertThat(found.get().getDescription()).isEqualTo("Source code archive");
    }

    @Test
    void shouldFindAdditionalFilesByBookId() {
        // Given
        BookAdditionalFileEntity file1 = createAdditionalFile("file1.zip", AdditionalFileType.SUPPLEMENTARY);
        BookAdditionalFileEntity file2 = createAdditionalFile("file2.epub", AdditionalFileType.ALTERNATIVE_FORMAT);
        entityManager.persistAndFlush(file1);
        entityManager.persistAndFlush(file2);

        // When
        List<BookAdditionalFileEntity> files = additionalFileRepository.findByBookId(testBook.getId());

        // Then
        assertThat(files).hasSize(2);
        assertThat(files).extracting(BookAdditionalFileEntity::getFileName)
                .containsExactlyInAnyOrder("file1.zip", "file2.epub");
    }

    @Test
    void shouldFindAdditionalFilesByBookIdAndType() {
        // Given
        BookAdditionalFileEntity supplementaryFile = createAdditionalFile("source.zip", AdditionalFileType.SUPPLEMENTARY);
        BookAdditionalFileEntity alternativeFile = createAdditionalFile("book.epub", AdditionalFileType.ALTERNATIVE_FORMAT);
        entityManager.persistAndFlush(supplementaryFile);
        entityManager.persistAndFlush(alternativeFile);

        // When - Find supplementary files
        List<BookAdditionalFileEntity> supplementaryFiles = additionalFileRepository
                .findByBookIdAndAdditionalFileType(testBook.getId(), AdditionalFileType.SUPPLEMENTARY);

        // When - Find alternative format files
        List<BookAdditionalFileEntity> alternativeFiles = additionalFileRepository
                .findByBookIdAndAdditionalFileType(testBook.getId(), AdditionalFileType.ALTERNATIVE_FORMAT);

        // Then
        assertThat(supplementaryFiles).hasSize(1);
        assertThat(supplementaryFiles.get(0).getFileName()).isEqualTo("source.zip");

        assertThat(alternativeFiles).hasSize(1);
        assertThat(alternativeFiles.get(0).getFileName()).isEqualTo("book.epub");
    }

    @Test
    void shouldFindAdditionalFileByCurrentHash() {
        // Given
        String uniqueHash = "unique-hash-123";
        BookAdditionalFileEntity file = createAdditionalFile("test.pdf", AdditionalFileType.ALTERNATIVE_FORMAT);
        file.setCurrentHash(uniqueHash);
        entityManager.persistAndFlush(file);

        // When
        Optional<BookAdditionalFileEntity> found = additionalFileRepository.findByAltFormatCurrentHash(uniqueHash);

        // Then
        assertThat(found).isPresent();
        assertThat(found.get().getCurrentHash()).isEqualTo(uniqueHash);
        assertThat(found.get().getFileName()).isEqualTo("test.pdf");
    }

    @Test
    void shouldNotFindAdditionalFileByNonExistentHash() {
        // When
        Optional<BookAdditionalFileEntity> found = additionalFileRepository.findByAltFormatCurrentHash("non-existent-hash");

        // Then
        assertThat(found).isEmpty();
    }

    @Test
    void shouldFindByLibraryPathAndFileSubPathAndFileName() {
        // Given
        BookAdditionalFileEntity file = createAdditionalFile("specific-file.zip", AdditionalFileType.SUPPLEMENTARY);
        entityManager.persistAndFlush(file);

        // When
        Optional<BookAdditionalFileEntity> found = additionalFileRepository
                .findByLibraryPath_IdAndFileSubPathAndFileName(
                        testLibraryPath.getId(),
                        "subfolder",
                        "specific-file.zip"
                );

        // Then
        assertThat(found).isPresent();
        assertThat(found.get().getFileName()).isEqualTo("specific-file.zip");
    }

    @Test
    void shouldCountAdditionalFilesByBookIdAndType() {
        // Given
        BookAdditionalFileEntity supplementary1 = createAdditionalFile("supp1.zip", AdditionalFileType.SUPPLEMENTARY);
        BookAdditionalFileEntity supplementary2 = createAdditionalFile("supp2.tar", AdditionalFileType.SUPPLEMENTARY);
        BookAdditionalFileEntity alternative1 = createAdditionalFile("alt1.epub", AdditionalFileType.ALTERNATIVE_FORMAT);
        entityManager.persistAndFlush(supplementary1);
        entityManager.persistAndFlush(supplementary2);
        entityManager.persistAndFlush(alternative1);

        // When
        long supplementaryCount = additionalFileRepository
                .countByBookIdAndAdditionalFileType(testBook.getId(), AdditionalFileType.SUPPLEMENTARY);
        long alternativeCount = additionalFileRepository
                .countByBookIdAndAdditionalFileType(testBook.getId(), AdditionalFileType.ALTERNATIVE_FORMAT);

        // Then
        assertThat(supplementaryCount).isEqualTo(2);
        assertThat(alternativeCount).isEqualTo(1);
    }

    @Test
    void shouldFindAdditionalFilesByType() {
        // Given
        BookAdditionalFileEntity supplementaryFile = createAdditionalFile("supp.zip", AdditionalFileType.SUPPLEMENTARY);
        BookAdditionalFileEntity alternativeFile = createAdditionalFile("alt.epub", AdditionalFileType.ALTERNATIVE_FORMAT);
        entityManager.persistAndFlush(supplementaryFile);
        entityManager.persistAndFlush(alternativeFile);

        // When
        List<BookAdditionalFileEntity> supplementaryFiles = additionalFileRepository
                .findByAdditionalFileType(AdditionalFileType.SUPPLEMENTARY);

        // Then
        assertThat(supplementaryFiles).hasSize(1);
        assertThat(supplementaryFiles.get(0).getAdditionalFileType()).isEqualTo(AdditionalFileType.SUPPLEMENTARY);
    }

    @Test
    void shouldDeleteAdditionalFile() {
        // Given
        BookAdditionalFileEntity file = createAdditionalFile("to-delete.zip", AdditionalFileType.SUPPLEMENTARY);
        BookAdditionalFileEntity saved = entityManager.persistAndFlush(file);
        Long fileId = saved.getId();

        // When
        additionalFileRepository.delete(saved);
        entityManager.flush();

        // Then
        Optional<BookAdditionalFileEntity> found = additionalFileRepository.findById(fileId);
        assertThat(found).isEmpty();
    }

    @Test
    void shouldCascadeDeleteWhenBookIsDeleted() {
        // Given - Create and persist an additional file first
        BookAdditionalFileEntity file = createAdditionalFile("cascade-test.zip", AdditionalFileType.SUPPLEMENTARY);
        entityManager.persistAndFlush(file);
        Long fileId = file.getId();
        Long bookId = testBook.getId();

        // Clear the persistence context to ensure clean state
        entityManager.clear();

        // When - Delete the book (should cascade to additional files)
        BookEntity bookToDelete = entityManager.find(BookEntity.class, bookId);
        entityManager.remove(bookToDelete);
        entityManager.flush();

        // Then
        Optional<BookAdditionalFileEntity> found = additionalFileRepository.findById(fileId);
        assertThat(found).isEmpty();

        // Also verify the book is deleted
        BookEntity deletedBook = entityManager.find(BookEntity.class, bookId);
        assertThat(deletedBook).isNull();
    }

    @Test
    void shouldReturnEmptyListForNonExistentBookId() {
        // When
        List<BookAdditionalFileEntity> files = additionalFileRepository.findByBookId(99999L);

        // Then
        assertThat(files).isEmpty();
    }

    @Test
    void shouldHandleMultipleFilesWithSameTypeForOneBook() {
        // Given - Multiple supplementary files for the same book
        BookAdditionalFileEntity file1 = createAdditionalFile("code.zip", AdditionalFileType.SUPPLEMENTARY);
        BookAdditionalFileEntity file2 = createAdditionalFile("docs.tar.gz", AdditionalFileType.SUPPLEMENTARY);
        BookAdditionalFileEntity file3 = createAdditionalFile("samples.rar", AdditionalFileType.SUPPLEMENTARY);
        entityManager.persistAndFlush(file1);
        entityManager.persistAndFlush(file2);
        entityManager.persistAndFlush(file3);

        // When
        List<BookAdditionalFileEntity> supplementaryFiles = additionalFileRepository
                .findByBookIdAndAdditionalFileType(testBook.getId(), AdditionalFileType.SUPPLEMENTARY);

        // Then
        assertThat(supplementaryFiles).hasSize(3);
        assertThat(supplementaryFiles).extracting(BookAdditionalFileEntity::getFileName)
                .containsExactlyInAnyOrder("code.zip", "docs.tar.gz", "samples.rar");
    }

    @Test
    void shouldTestUniqueConstraintsAndDuplicateHashes() {
        // Given - Two files with same hash (simulating duplicate content)
        BookAdditionalFileEntity file1 = createAdditionalFile("original.zip", AdditionalFileType.SUPPLEMENTARY);
        file1.setCurrentHash("duplicate-hash");
        entityManager.persistAndFlush(file1);

        BookAdditionalFileEntity file2 = createAdditionalFile("copy.zip", AdditionalFileType.SUPPLEMENTARY);
        file2.setCurrentHash("duplicate-hash");
        entityManager.persistAndFlush(file2);

        // When
        List<BookAdditionalFileEntity> filesWithSameHash = additionalFileRepository
                .findByBookId(testBook.getId()).stream()
                .filter(f -> "duplicate-hash".equals(f.getCurrentHash()))
                .toList();

        // Then - Both files exist (no unique constraint on hash at DB level)
        assertThat(filesWithSameHash).hasSize(2);
        assertThat(filesWithSameHash).extracting(BookAdditionalFileEntity::getFileName)
                .containsExactlyInAnyOrder("original.zip", "copy.zip");
    }

    @Test
    void shouldTestFilePathConstruction() {
        // Given
        BookAdditionalFileEntity file = createAdditionalFile("path-test.zip", AdditionalFileType.SUPPLEMENTARY);
        entityManager.persistAndFlush(file);

        // When
        String fullPath = file.getFullFilePath().toString();

        // Then
        // Use File.separator to handle both Windows and Unix paths
        assertThat(fullPath).contains("test" + File.separator + "path");
        assertThat(fullPath).contains("subfolder");
        assertThat(fullPath).contains("path-test.zip");
    }

    @Test
    void shouldPersistAndRetrieveTimestamps() {
        // Given
        Instant beforeSave = Instant.now().minusSeconds(1);
        BookAdditionalFileEntity file = createAdditionalFile("timestamp-test.zip", AdditionalFileType.SUPPLEMENTARY);
        Instant afterSave = Instant.now().plusSeconds(1);

        // When
        BookAdditionalFileEntity saved = entityManager.persistAndFlush(file);

        // Then
        assertThat(saved.getAddedOn()).isNotNull();
        assertThat(saved.getAddedOn()).isBetween(beforeSave, afterSave);
    }

    private BookAdditionalFileEntity createAdditionalFile(String fileName, AdditionalFileType type) {
        return BookAdditionalFileEntity.builder()
                .book(testBook)
                .fileName(fileName)
                .fileSubPath("subfolder")
                .additionalFileType(type)
                .fileSizeKb(256L)
                .initialHash("initial-" + fileName)
                .currentHash("current-" + fileName)
                .description("Test file: " + fileName)
                .addedOn(Instant.now())
                .build();
    }
}
