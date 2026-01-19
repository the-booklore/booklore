package com.adityachandel.booklore.util.builder;

import com.adityachandel.booklore.model.entity.BookEntity;
import com.adityachandel.booklore.model.entity.BookFileEntity;
import com.adityachandel.booklore.model.enums.BookFileType;
import org.assertj.core.api.AbstractAssert;
import org.assertj.core.api.Assertions;

import java.util.List;
import java.util.stream.Collectors;

public class LibraryTestBuilderAssert extends AbstractAssert<LibraryTestBuilderAssert, LibraryTestBuilder> {

    private List<BookFileEntity> getAllBookFiles(BookEntity book) {
        // Get all book files from the entity
        List<BookFileEntity> allBookFiles = new java.util.ArrayList<>(book.getBookFiles());
        // Add additional files from repository, but only if not already present
        // (Hibernate bytecode enhancement adds them to book.getBookFiles() when created)
        actual.getBookAdditionalFiles().stream()
                .filter(f -> f.getBook().getId().equals(book.getId()))
                .filter(f -> !allBookFiles.contains(f))
                .forEach(allBookFiles::add);
        return allBookFiles;
    }

    private BookFileEntity getPrimaryBookFile(BookEntity book) {
        List<BookFileEntity> allBookFiles = getAllBookFiles(book);
        if (allBookFiles.isEmpty()) {
            return null;
        }

        List<BookFileEntity> availableBookFiles = allBookFiles.stream()
                .filter(BookFileEntity::isBook)
                .toList();
        if (availableBookFiles.isEmpty()) {
            return null;
        }

        // Use the same default order as PreferredBookFileResolver
        List<BookFileType> defaultOrder = List.of(
                BookFileType.EPUB,
                BookFileType.PDF,
                BookFileType.CBX,
                BookFileType.FB2,
                BookFileType.MOBI,
                BookFileType.AZW3
        );

        for (BookFileType type : defaultOrder) {
            for (BookFileEntity file : availableBookFiles) {
                if (file.getBookType() == type) {
                    return file;
                }
            }
        }

        return availableBookFiles.stream()
                .min(java.util.Comparator.comparing(BookFileEntity::getId, java.util.Comparator.nullsLast(Long::compareTo)))
                .orElse(availableBookFiles.get(0));
    }

    protected LibraryTestBuilderAssert(LibraryTestBuilder libraryTestBuilder) {
        super(libraryTestBuilder, LibraryTestBuilderAssert.class);
    }

    public static LibraryTestBuilderAssert assertThat(LibraryTestBuilder actual) {
        return new LibraryTestBuilderAssert(actual);
    }

    public LibraryTestBuilderAssert hasBooks(String ...expectedBookTitles) {
        Assertions.assertThat(actual.getBookEntities())
                .extracting(bookEntity -> bookEntity.getMetadata().getTitle())
                .containsExactlyInAnyOrder(expectedBookTitles);

        return this;
    }

    public LibraryTestBuilderAssert hasNoAdditionalFiles() {
        var additionalFiles = actual.getBookAdditionalFiles();
        Assertions.assertThat(additionalFiles)
                .isEmpty();

        return this;
    }

    public LibraryTestBuilderAssert bookHasAdditionalFormats(String bookTitle, BookFileType ...additionalFormatTypes) {
        var book = actual.getBookEntity(bookTitle);
        Assertions.assertThat(book)
                .describedAs("Book with title '%s' should exist", bookTitle)
                .isNotNull();

        BookFileEntity primaryFile = getPrimaryBookFile(book);
        List<BookFileEntity> allBookFiles = getAllBookFiles(book);

        List<BookFileType> additionalFormatTypesActual = allBookFiles.stream()
                .filter(BookFileEntity::isBookFormat)
                .filter(a -> !a.equals(primaryFile))
                .map(BookFileEntity::getBookType)
                .filter(a -> a != null)
                .collect(Collectors.toList());

        Assertions.assertThat(additionalFormatTypesActual)
                .describedAs("Book '%s' should have additional formats: %s", bookTitle, additionalFormatTypes)
                .containsExactlyInAnyOrder(additionalFormatTypes);

        return this;
    }

    public LibraryTestBuilderAssert bookHasSupplementaryFiles(String bookTitle, String ...supplementaryFiles) {
        var book = actual.getBookEntity(bookTitle);
        Assertions.assertThat(book)
                .describedAs("Book with title '%s' should exist", bookTitle)
                .isNotNull();

        List<BookFileEntity> allBookFiles = getAllBookFiles(book);
        Assertions.assertThat(allBookFiles.stream()
                    .filter(a -> !a.isBookFormat())
                    .map(BookFileEntity::getFileName))
                .describedAs("Book '%s' should have supplementary files", bookTitle)
                .containsExactlyInAnyOrder(supplementaryFiles);

        return this;
    }

    public LibraryTestBuilderAssert bookHasNoAdditionalFormats(String bookTitle) {
        var book = actual.getBookEntity(bookTitle);
        Assertions.assertThat(book)
                .describedAs("Book with title '%s' should exist", bookTitle)
                .isNotNull();

        BookFileEntity primaryFile = getPrimaryBookFile(book);
        List<BookFileEntity> allBookFiles = getAllBookFiles(book);
        List<BookFileType> additionalFormatTypesActual = allBookFiles.stream()
                .filter(BookFileEntity::isBookFormat)
                .filter(a -> !a.equals(primaryFile))
                .map(BookFileEntity::getBookType)
                .filter(a -> a != null)
                .collect(Collectors.toList());

        Assertions.assertThat(additionalFormatTypesActual)
                .describedAs("Book '%s' should have no additional formats", bookTitle)
                .isEmpty();

        return this;
    }

    public LibraryTestBuilderAssert bookHasNoSupplementaryFiles(String bookTitle) {
        var book = actual.getBookEntity(bookTitle);
        Assertions.assertThat(book)
                .describedAs("Book with title '%s' should exist", bookTitle)
                .isNotNull();

        List<BookFileEntity> allBookFiles = getAllBookFiles(book);
        Assertions.assertThat(allBookFiles)
                .describedAs("Book '%s' should have no supplementary files", bookTitle)
                .noneMatch(a -> !a.isBookFormat());

        return this;
    }

    public LibraryTestBuilderAssert bookHasNoAdditionalFiles(String bookTitle) {
        var book = actual.getBookEntity(bookTitle);
        Assertions.assertThat(book)
                .describedAs("Book with title '%s' should exist", bookTitle)
                .isNotNull();

        BookFileEntity primaryFile = getPrimaryBookFile(book);
        List<BookFileEntity> allBookFiles = getAllBookFiles(book);
        Assertions.assertThat(allBookFiles)
                .describedAs("Book '%s' should have no additional files", bookTitle)
                .allMatch(BookFileEntity::isBookFormat)
                .containsOnly(primaryFile);

        return this;
    }
}
