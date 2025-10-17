package com.adityachandel.booklore.service;

import com.adityachandel.booklore.mapper.v2.BookMapperV2;
import com.adityachandel.booklore.model.dto.Book;
import com.adityachandel.booklore.model.entity.BookEntity;
import com.adityachandel.booklore.repository.BookRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@RequiredArgsConstructor
@Service
public class BookQueryService {

    private final BookRepository bookRepository;
    private final BookMapperV2 bookMapperV2;

    public List<Book> getAllBooks(boolean includeDescription) {
        List<BookEntity> books = bookRepository.findAllWithMetadata();
        return mapBooksToDto(books, includeDescription, null);
    }

    public Page<Book> getAllBooksPage(boolean includeDescription, int page, int size) {
        Pageable pageable = PageRequest.of(Math.max(page - 1, 0), size);
        Page<BookEntity> books = bookRepository.findAllWithMetadata(pageable);
        return createPageFromBooks(books, pageable, includeDescription, null);
    }

    public List<Book> getAllBooksByLibraryIds(Set<Long> libraryIds, boolean includeDescription, Long userId) {
        List<BookEntity> books = bookRepository.findAllWithMetadataByLibraryIds(libraryIds);
        return mapBooksToDto(books, includeDescription, userId);
    }

    public List<BookEntity> findAllWithMetadataByIds(Set<Long> bookIds) {
        return bookRepository.findAllWithMetadataByIds(bookIds);
    }

    public List<BookEntity> findWithMetadataByIdsWithPagination(Set<Long> bookIds, int offset, int limit) {
        Pageable pageable = PageRequest.of(offset / limit, limit);
        return bookRepository.findWithMetadataByIdsWithPagination(bookIds, pageable);
    }

    public List<BookEntity> getAllFullBookEntities() {
        return bookRepository.findAllFullBooks();
    }

    public Page<Book> searchBooksByMetadataPage(String text, int page, int size) {
        Pageable pageable = PageRequest.of(Math.max(page - 1, 0), size);
        Page<BookEntity> books = bookRepository.searchByMetadata(text, pageable);
        List<Book> mapped = books.getContent().stream()
                .map(bookMapperV2::toDTO)
                .collect(Collectors.toList());
        return new PageImpl<>(mapped, pageable, books.getTotalElements());
    }

    public void saveAll(List<BookEntity> books) {
        bookRepository.saveAll(books);
    }

    public List<Book> searchBooksByMetadata(String text) {
        Pageable pageable = PageRequest.of(0, Integer.MAX_VALUE);
        Page<BookEntity> books = bookRepository.searchByMetadata(text, pageable);
        return books.getContent().stream()
                .map(bookMapperV2::toDTO)
                .collect(Collectors.toList());
    }

    private List<Book> mapBooksToDto(List<BookEntity> books, boolean includeDescription, Long userId) {
        return books.stream()
                .map(book -> mapBookToDto(book, includeDescription, userId))
                .collect(Collectors.toList());
    }

    private Book mapBookToDto(BookEntity bookEntity, boolean includeDescription, Long userId) {
        Book dto = bookMapperV2.toDTO(bookEntity);

        if (!includeDescription && dto.getMetadata() != null) {
            dto.getMetadata().setDescription(null);
        }

        if (dto.getShelves() != null && userId != null) {
            dto.setShelves(dto.getShelves().stream()
                    .filter(shelf -> userId.equals(shelf.getUserId()))
                    .collect(Collectors.toSet()));
        }

        return dto;
    }

    private Page<Book> createPageFromBooks(Page<BookEntity> books, Pageable pageable, boolean includeDescription, Long userId) {
        List<Book> mapped = mapBooksToDto(books.getContent(), includeDescription, userId);
        return new PageImpl<>(mapped, pageable, books.getTotalElements());
    }
}
