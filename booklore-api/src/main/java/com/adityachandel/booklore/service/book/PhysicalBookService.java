package com.adityachandel.booklore.service.book;

import com.adityachandel.booklore.exception.APIException;
import com.adityachandel.booklore.mapper.BookMapper;
import com.adityachandel.booklore.model.dto.Book;
import com.adityachandel.booklore.model.dto.request.CreatePhysicalBookRequest;
import com.adityachandel.booklore.model.entity.AuthorEntity;
import com.adityachandel.booklore.model.entity.BookEntity;
import com.adityachandel.booklore.model.entity.BookMetadataEntity;
import com.adityachandel.booklore.model.entity.CategoryEntity;
import com.adityachandel.booklore.model.entity.LibraryEntity;
import com.adityachandel.booklore.repository.AuthorRepository;
import com.adityachandel.booklore.repository.BookRepository;
import com.adityachandel.booklore.repository.CategoryRepository;
import com.adityachandel.booklore.repository.LibraryRepository;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

@Slf4j
@Service
@AllArgsConstructor
public class PhysicalBookService {

    private final BookRepository bookRepository;
    private final LibraryRepository libraryRepository;
    private final AuthorRepository authorRepository;
    private final CategoryRepository categoryRepository;
    private final BookMapper bookMapper;

    @Transactional
    public Book createPhysicalBook(CreatePhysicalBookRequest request) {
        LibraryEntity library = libraryRepository.findById(request.getLibraryId())
                .orElseThrow(() -> new APIException("Library not found with id: " + request.getLibraryId(), HttpStatus.NOT_FOUND));

        BookEntity bookEntity = BookEntity.builder()
                .library(library)
                .libraryPath(null)
                .isPhysical(true)
                .addedOn(Instant.now())
                .bookFiles(new ArrayList<>())
                .build();

        BookMetadataEntity metadata = BookMetadataEntity.builder()
                .book(bookEntity)
                .title(request.getTitle())
                .description(request.getDescription())
                .publisher(request.getPublisher())
                .publishedDate(parsePublishedDate(request.getPublishedDate()))
                .language(request.getLanguage())
                .pageCount(request.getPageCount())
                .isbn13(extractIsbn13(request.getIsbn()))
                .isbn10(extractIsbn10(request.getIsbn()))
                .build();

        bookEntity.setMetadata(metadata);

        if (request.getAuthors() != null && !request.getAuthors().isEmpty()) {
            addAuthorsToBook(new HashSet<>(request.getAuthors()), bookEntity);
        }

        if (request.getCategories() != null && !request.getCategories().isEmpty()) {
            addCategoriesToBook(new HashSet<>(request.getCategories()), bookEntity);
        }

        BookEntity savedBook = bookRepository.save(bookEntity);
        log.info("Created physical book '{}' in library {} with id {}", request.getTitle(), library.getName(), savedBook.getId());

        return bookMapper.toBook(savedBook);
    }

    private LocalDate parsePublishedDate(String publishedDate) {
        if (publishedDate == null || publishedDate.isBlank()) {
            return null;
        }
        String trimmed = publishedDate.trim();
        // Try full date format first (YYYY-MM-DD)
        try {
            return LocalDate.parse(trimmed, DateTimeFormatter.ISO_LOCAL_DATE);
        } catch (DateTimeParseException ignored) {}
        // Try year only format (YYYY)
        try {
            int year = Integer.parseInt(trimmed);
            return LocalDate.of(year, 1, 1);
        } catch (NumberFormatException ignored) {}
        return null;
    }

    private String extractIsbn13(String isbn) {
        if (isbn == null) return null;
        String cleaned = isbn.replaceAll("[^0-9X]", "");
        return cleaned.length() == 13 ? cleaned : null;
    }

    private String extractIsbn10(String isbn) {
        if (isbn == null) return null;
        String cleaned = isbn.replaceAll("[^0-9X]", "");
        return cleaned.length() == 10 ? cleaned : null;
    }

    private void addAuthorsToBook(Set<String> authors, BookEntity bookEntity) {
        if (bookEntity.getMetadata().getAuthors() == null) {
            bookEntity.getMetadata().setAuthors(new HashSet<>());
        }
        authors.stream()
                .map(authorName -> truncate(authorName, 255))
                .map(authorName -> authorRepository.findByName(authorName)
                        .orElseGet(() -> authorRepository.save(AuthorEntity.builder().name(authorName).build())))
                .forEach(authorEntity -> bookEntity.getMetadata().getAuthors().add(authorEntity));
        bookEntity.getMetadata().updateSearchText();
    }

    private void addCategoriesToBook(Set<String> categories, BookEntity bookEntity) {
        if (bookEntity.getMetadata().getCategories() == null) {
            bookEntity.getMetadata().setCategories(new HashSet<>());
        }
        categories.stream()
                .map(cat -> truncate(cat, 255))
                .map(truncated -> categoryRepository.findByName(truncated)
                        .orElseGet(() -> categoryRepository.save(CategoryEntity.builder().name(truncated).build())))
                .forEach(catEntity -> bookEntity.getMetadata().getCategories().add(catEntity));
    }

    private String truncate(String input, int maxLength) {
        if (input == null) return null;
        return input.length() <= maxLength ? input : input.substring(0, maxLength);
    }
}
