package com.adityachandel.booklore.service;

import com.adityachandel.booklore.mapper.v2.BookMapperV2;
import com.adityachandel.booklore.model.dto.Book;
import com.adityachandel.booklore.model.entity.BookEntity;
import com.adityachandel.booklore.repository.BookRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Sort;

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

    public Page<Book> getRecentBooksPage(boolean includeDescription, int page, int size) {
        Pageable pageable = PageRequest.of(Math.max(page - 1, 0), size, Sort.by("addedOn").descending());
        Page<BookEntity> books = bookRepository.findAllWithMetadata(pageable);
        return createPageFromBooks(books, pageable, includeDescription, null);
    }

    public List<Book> getAllBooksByLibraryIds(Set<Long> libraryIds, boolean includeDescription, Long userId) {
        List<BookEntity> books = bookRepository.findAllWithMetadataByLibraryIds(libraryIds);
        return mapBooksToDto(books, includeDescription, userId);
    }

    public Page<Book> getAllBooksByLibraryIdsPage(Set<Long> libraryIds, boolean includeDescription, int page, int size, Long userId) {
        Pageable pageable = PageRequest.of(Math.max(page - 1, 0), size);
        Page<BookEntity> books = bookRepository.findAllWithMetadataByLibraryIds(libraryIds, pageable);
        return createPageFromBooks(books, pageable, includeDescription, userId);
    }

    public Page<Book> getRecentBooksByLibraryIdsPage(Set<Long> libraryIds, boolean includeDescription, int page, int size, Long userId) {
        Pageable pageable = PageRequest.of(Math.max(page - 1, 0), size, Sort.by("addedOn").descending());
        Page<BookEntity> books = bookRepository.findAllWithMetadataByLibraryIds(libraryIds, pageable);
        return createPageFromBooks(books, pageable, includeDescription, userId);
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

    public List<Book> searchBooksByMetadata(String text) {
        List<BookEntity> bookEntities = bookRepository.searchByMetadata(text);
        return bookEntities.stream()
                .map(bookMapperV2::toDTO)
                .collect(Collectors.toList());
    }

    public Page<Book> searchBooksByMetadataPage(String text, int page, int size) {
        Pageable pageable = PageRequest.of(Math.max(page - 1, 0), size);
        Page<BookEntity> books = bookRepository.searchByMetadata(text, pageable);
        List<Book> mapped = books.getContent().stream()
                .map(bookMapperV2::toDTO)
                .collect(Collectors.toList());
        return new PageImpl<>(mapped, pageable, books.getTotalElements());
    }

    public List<Book> searchBooksByMetadataInLibraries(String text, Set<Long> libraryIds) {
        List<BookEntity> bookEntities = bookRepository.searchByMetadataAndLibraryIds(text, libraryIds);
        return bookEntities.stream()
                .map(bookMapperV2::toDTO)
                .collect(Collectors.toList());
    }

    public Page<Book> searchBooksByMetadataInLibrariesPage(String text, Set<Long> libraryIds, int page, int size) {
        Pageable pageable = PageRequest.of(Math.max(page - 1, 0), size);
        Page<BookEntity> books = bookRepository.searchByMetadataAndLibraryIds(text, libraryIds, pageable);
        List<Book> mapped = books.getContent().stream()
                .map(bookMapperV2::toDTO)
                .collect(Collectors.toList());
        return new PageImpl<>(mapped, pageable, books.getTotalElements());
    }

    public Page<Book> getAllBooksByShelfPage(Long shelfId, boolean includeDescription, int page, int size) {
        Pageable pageable = PageRequest.of(Math.max(page - 1, 0), size);
        Page<BookEntity> books = bookRepository.findAllWithMetadataByShelfId(shelfId, pageable);
        return createPageFromBooks(books, pageable, includeDescription, null);
    }

    public void saveAll(List<BookEntity> books) {
        bookRepository.saveAll(books);
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
