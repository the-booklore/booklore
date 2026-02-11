package org.booklore.service.book;

import org.booklore.mapper.v2.BookMapperV2;
import org.booklore.model.dto.Book;
import org.booklore.model.entity.BookEntity;
import org.booklore.repository.BookRepository;
import org.booklore.service.restriction.ContentRestrictionService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@RequiredArgsConstructor
@Service
public class BookQueryService {

    private final BookRepository bookRepository;
    private final BookMapperV2 bookMapperV2;
    private final ContentRestrictionService contentRestrictionService;

    public List<Book> getAllBooks(boolean includeDescription) {
        List<BookEntity> books = bookRepository.findAllWithMetadata();
        return mapBooksToDto(books, includeDescription, null);
    }

    public List<Book> getAllBooksByLibraryIds(Set<Long> libraryIds, boolean includeDescription, Long userId) {
        List<BookEntity> books = bookRepository.findAllWithMetadataByLibraryIds(libraryIds);
        books = contentRestrictionService.applyRestrictions(books, userId);
        return mapBooksToDto(books, includeDescription, userId);
    }

    public List<BookEntity> findAllWithMetadataByIds(Set<Long> bookIds) {
        return bookRepository.findAllWithMetadataByIds(bookIds);
    }

    public List<BookEntity> getAllFullBookEntities() {
        return bookRepository.findAllFullBooks();
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
}
