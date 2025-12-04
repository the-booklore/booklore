package com.adityachandel.booklore.service.opds;

import com.adityachandel.booklore.exception.ApiError;
import com.adityachandel.booklore.mapper.BookMapper;
import com.adityachandel.booklore.mapper.custom.BookLoreUserTransformer;
import com.adityachandel.booklore.model.dto.*;
import com.adityachandel.booklore.model.entity.BookEntity;
import com.adityachandel.booklore.model.entity.BookLoreUserEntity;
import com.adityachandel.booklore.model.entity.ShelfEntity;
import com.adityachandel.booklore.repository.BookOpdsRepository;
import com.adityachandel.booklore.repository.ShelfRepository;
import com.adityachandel.booklore.repository.UserRepository;
import com.adityachandel.booklore.service.library.LibraryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@RequiredArgsConstructor
@Service
public class OpdsBookService {

    private final BookOpdsRepository bookOpdsRepository;
    private final BookMapper bookMapper;
    private final UserRepository userRepository;
    private final BookLoreUserTransformer bookLoreUserTransformer;
    private final ShelfRepository shelfRepository;
    private final LibraryService libraryService;

    public List<Library> getAccessibleLibraries(Long userId) {
        if (userId == null) {
            return List.of();
        }

        BookLoreUserEntity entity = userRepository.findById(userId)
                .orElseThrow(() -> ApiError.USER_NOT_FOUND.createException(userId));
        BookLoreUser user = bookLoreUserTransformer.toDTO(entity);

        if (user.getPermissions() != null && user.getPermissions().isAdmin()) {
            return libraryService.getAllLibraries();
        }

        return user.getAssignedLibraries();
    }

    public Page<Book> getBooksPage(Long userId, String query, Long libraryId, Long shelfId, int page, int size) {
        if (userId == null) {
            throw ApiError.FORBIDDEN.createException("Authentication required");
        }

        BookLoreUserEntity entity = userRepository.findById(userId)
                .orElseThrow(() -> ApiError.USER_NOT_FOUND.createException(userId));

        if (entity.getPermissions() == null ||
                (!entity.getPermissions().isPermissionAccessOpds() && !entity.getPermissions().isPermissionAdmin())) {
            throw ApiError.FORBIDDEN.createException("You are not allowed to access this resource");
        }

        BookLoreUser user = bookLoreUserTransformer.toDTO(entity);
        boolean isAdmin = user.getPermissions().isAdmin();
        Set<Long> userLibraryIds = user.getAssignedLibraries().stream()
                .map(Library::getId)
                .collect(Collectors.toSet());

        if (shelfId != null) {
            validateShelfAccess(shelfId, user.getId(), isAdmin);
            return getBooksByShelfIdPageInternal(shelfId, page, size);
        }

        if (libraryId != null) {
            validateLibraryAccess(libraryId, userLibraryIds, isAdmin);
            Page<Book> books = query != null && !query.isBlank()
                    ? searchByMetadataInLibrariesPageInternal(query, Set.of(libraryId), page, size)
                    : getBooksByLibraryIdsPageInternal(Set.of(libraryId), page, size);
            return applyBookFilters(books, userId);
        }

        if (isAdmin) {
            return query != null && !query.isBlank()
                    ? searchByMetadataPageInternal(query, page, size)
                    : getAllBooksPageInternal(page, size);
        }

        Page<Book> books = query != null && !query.isBlank()
                ? searchByMetadataInLibrariesPageInternal(query, userLibraryIds, page, size)
                : getBooksByLibraryIdsPageInternal(userLibraryIds, page, size);
        return applyBookFilters(books, userId);
    }

    public Page<Book> getRecentBooksPage(Long userId, int page, int size) {
        if (userId == null) {
            throw ApiError.FORBIDDEN.createException("Authentication required");
        }

        BookLoreUserEntity entity = userRepository.findById(userId)
                .orElseThrow(() -> ApiError.USER_NOT_FOUND.createException(userId));
        BookLoreUser user = bookLoreUserTransformer.toDTO(entity);

        if (user.getPermissions().isAdmin()) {
            return getRecentBooksPageInternal(page, size);
        }

        Set<Long> libraryIds = user.getAssignedLibraries().stream()
                .map(Library::getId)
                .collect(Collectors.toSet());

        Page<Book> books = getRecentBooksByLibraryIdsPageInternal(libraryIds, page, size);
        return applyBookFilters(books, userId);
    }

    public String getLibraryName(Long libraryId) {
        try {
            List<Library> libraries = libraryService.getAllLibraries();
            return libraries.stream()
                    .filter(lib -> lib.getId().equals(libraryId))
                    .map(Library::getName)
                    .findFirst()
                    .orElse("Library Books");
        } catch (Exception e) {
            log.warn("Failed to get library name for id: {}", libraryId, e);
            return "Library Books";
        }
    }

    public String getShelfName(Long shelfId) {
        return shelfRepository.findById(shelfId)
                .map(s -> s.getName() + " - Shelf")
                .orElse("Shelf Books");
    }

    public List<ShelfEntity> getUserShelves(Long userId) {
        return shelfRepository.findByUserId(userId);
    }

    public List<Book> getRandomBooks(Long userId, int count) {
        List<Library> accessibleLibraries = getAccessibleLibraries(userId);
        if (accessibleLibraries == null || accessibleLibraries.isEmpty()) {
            return List.of();
        }

        List<Long> libraryIds = accessibleLibraries.stream().map(Library::getId).toList();
        List<Long> ids = bookOpdsRepository.findRandomBookIdsByLibraryIds(libraryIds);

        if (ids.isEmpty()) {
            return List.of();
        }

        List<BookEntity> books = bookOpdsRepository.findAllWithMetadataByIds(ids.stream().limit(count).toList());
        return books.stream().map(bookMapper::toBook).toList();
    }

    private Page<Book> getAllBooksPageInternal(int page, int size) {
        Pageable pageable = PageRequest.of(Math.max(page, 0), size);

        Page<Long> idPage = bookOpdsRepository.findBookIds(pageable);
        if (idPage.isEmpty()) {
            return new PageImpl<>(List.of(), pageable, 0);
        }

        List<BookEntity> books = bookOpdsRepository.findAllWithMetadataByIds(idPage.getContent());
        return createPageFromEntities(books, idPage, pageable);
    }

    private Page<Book> getRecentBooksPageInternal(int page, int size) {
        Pageable pageable = PageRequest.of(Math.max(page, 0), size);

        Page<Long> idPage = bookOpdsRepository.findRecentBookIds(pageable);
        if (idPage.isEmpty()) {
            return new PageImpl<>(List.of(), pageable, 0);
        }

        List<BookEntity> books = bookOpdsRepository.findAllWithMetadataByIds(idPage.getContent());
        return createPageFromEntities(books, idPage, pageable);
    }

    private Page<Book> getBooksByLibraryIdsPageInternal(Set<Long> libraryIds, int page, int size) {
        Pageable pageable = PageRequest.of(Math.max(page, 0), size);

        Page<Long> idPage = bookOpdsRepository.findBookIdsByLibraryIds(libraryIds, pageable);
        if (idPage.isEmpty()) {
            return new PageImpl<>(List.of(), pageable, 0);
        }

        List<BookEntity> books = bookOpdsRepository.findAllWithMetadataByIdsAndLibraryIds(idPage.getContent(), libraryIds);
        return createPageFromEntities(books, idPage, pageable);
    }

    private Page<Book> getRecentBooksByLibraryIdsPageInternal(Set<Long> libraryIds, int page, int size) {
        Pageable pageable = PageRequest.of(Math.max(page, 0), size);

        Page<Long> idPage = bookOpdsRepository.findRecentBookIdsByLibraryIds(libraryIds, pageable);
        if (idPage.isEmpty()) {
            return new PageImpl<>(List.of(), pageable, 0);
        }

        List<BookEntity> books = bookOpdsRepository.findAllWithMetadataByIdsAndLibraryIds(idPage.getContent(), libraryIds);
        return createPageFromEntities(books, idPage, pageable);
    }

    private Page<Book> getBooksByShelfIdPageInternal(Long shelfId, int page, int size) {
        Pageable pageable = PageRequest.of(Math.max(page, 0), size);

        Page<Long> idPage = bookOpdsRepository.findBookIdsByShelfId(shelfId, pageable);
        if (idPage.isEmpty()) {
            return new PageImpl<>(List.of(), pageable, 0);
        }

        List<BookEntity> books = bookOpdsRepository.findAllWithMetadataByIdsAndShelfId(idPage.getContent(), shelfId);
        return createPageFromEntities(books, idPage, pageable);
    }

    private Page<Book> searchByMetadataPageInternal(String text, int page, int size) {
        Pageable pageable = PageRequest.of(Math.max(page, 0), size);

        Page<Long> idPage = bookOpdsRepository.findBookIdsByMetadataSearch(text, pageable);
        if (idPage.isEmpty()) {
            return new PageImpl<>(List.of(), pageable, 0);
        }

        List<BookEntity> books = bookOpdsRepository.findAllWithFullMetadataByIds(idPage.getContent());
        return createPageFromEntities(books, idPage, pageable);
    }

    private Page<Book> searchByMetadataInLibrariesPageInternal(String text, Set<Long> libraryIds, int page, int size) {
        Pageable pageable = PageRequest.of(Math.max(page, 0), size);

        Page<Long> idPage = bookOpdsRepository.findBookIdsByMetadataSearchAndLibraryIds(text, libraryIds, pageable);
        if (idPage.isEmpty()) {
            return new PageImpl<>(List.of(), pageable, 0);
        }

        List<BookEntity> books = bookOpdsRepository.findAllWithFullMetadataByIdsAndLibraryIds(idPage.getContent(), libraryIds);
        return createPageFromEntities(books, idPage, pageable);
    }

    private void validateShelfAccess(Long shelfId, Long userId, boolean isAdmin) {
        var shelf = shelfRepository.findById(shelfId)
                .orElseThrow(() -> ApiError.SHELF_NOT_FOUND.createException(shelfId));
        if (!shelf.getUser().getId().equals(userId) && !isAdmin) {
            throw ApiError.FORBIDDEN.createException("You are not allowed to access this shelf");
        }
    }

    private void validateLibraryAccess(Long libraryId, Set<Long> userLibraryIds, boolean isAdmin) {
        if (!isAdmin && !userLibraryIds.contains(libraryId)) {
            throw ApiError.FORBIDDEN.createException("You are not allowed to access this library");
        }
    }

    private Page<Book> createPageFromEntities(List<BookEntity> books, Page<Long> idPage, Pageable pageable) {
        Map<Long, BookEntity> bookMap = books.stream()
                .collect(Collectors.toMap(BookEntity::getId, Function.identity()));

        List<Book> sortedBooks = idPage.getContent().stream()
                .map(bookMap::get)
                .filter(Objects::nonNull)
                .map(bookMapper::toBook)
                .toList();

        return new PageImpl<>(sortedBooks, pageable, idPage.getTotalElements());
    }

    private Page<Book> applyBookFilters(Page<Book> books, Long userId) {
        List<Book> filtered = books.getContent().stream()
                .map(book -> filterBook(book, userId))
                .collect(Collectors.toList());
        return new PageImpl<>(filtered, books.getPageable(), books.getTotalElements());
    }

    private Book filterBook(Book dto, Long userId) {
        if (dto.getShelves() != null && userId != null) {
            dto.setShelves(dto.getShelves().stream()
                    .filter(shelf -> userId.equals(shelf.getUserId()))
                    .collect(Collectors.toSet()));
        }
        return dto;
    }
}
