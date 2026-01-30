package com.adityachandel.booklore.mobile.service;

import com.adityachandel.booklore.config.security.service.AuthenticationService;
import com.adityachandel.booklore.exception.ApiError;
import com.adityachandel.booklore.mobile.dto.*;
import com.adityachandel.booklore.mobile.mapper.MobileBookMapper;
import com.adityachandel.booklore.mobile.specification.MobileBookSpecification;
import com.adityachandel.booklore.model.dto.BookLoreUser;
import com.adityachandel.booklore.model.dto.Library;
import com.adityachandel.booklore.model.entity.*;
import com.adityachandel.booklore.model.enums.ReadStatus;
import com.adityachandel.booklore.repository.*;
import com.adityachandel.booklore.service.opds.MagicShelfBookService;
import lombok.AllArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@AllArgsConstructor
public class MobileBookService {

    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 50;

    private final BookRepository bookRepository;
    private final UserBookProgressRepository userBookProgressRepository;
    private final UserBookFileProgressRepository userBookFileProgressRepository;
    private final ShelfRepository shelfRepository;
    private final AuthenticationService authenticationService;
    private final MobileBookMapper mobileBookMapper;
    private final MagicShelfBookService magicShelfBookService;

    @Transactional(readOnly = true)
    public MobilePageResponse<MobileBookSummary> getBooks(
            Integer page,
            Integer size,
            String sortBy,
            String sortDir,
            Long libraryId,
            Long shelfId,
            ReadStatus status,
            String search) {

        BookLoreUser user = authenticationService.getAuthenticatedUser();
        Long userId = user.getId();
        Set<Long> accessibleLibraryIds = getAccessibleLibraryIds(user);

        int pageNum = page != null && page >= 0 ? page : 0;
        int pageSize = size != null && size > 0 ? Math.min(size, MAX_PAGE_SIZE) : DEFAULT_PAGE_SIZE;

        Sort sort = buildSort(sortBy, sortDir);
        Pageable pageable = PageRequest.of(pageNum, pageSize, sort);

        Specification<BookEntity> spec = buildSpecification(
                accessibleLibraryIds, libraryId, shelfId, status, search, userId);

        Page<BookEntity> bookPage = bookRepository.findAll(spec, pageable);

        Set<Long> bookIds = bookPage.getContent().stream()
                .map(BookEntity::getId)
                .collect(Collectors.toSet());
        Map<Long, UserBookProgressEntity> progressMap = getProgressMap(userId, bookIds);

        List<MobileBookSummary> summaries = bookPage.getContent().stream()
                .map(book -> mobileBookMapper.toSummary(book, progressMap.get(book.getId())))
                .collect(Collectors.toList());

        return MobilePageResponse.of(summaries, pageNum, pageSize, bookPage.getTotalElements());
    }

    @Transactional(readOnly = true)
    public MobileBookDetail getBookDetail(Long bookId) {
        BookLoreUser user = authenticationService.getAuthenticatedUser();
        Long userId = user.getId();
        Set<Long> accessibleLibraryIds = getAccessibleLibraryIds(user);

        BookEntity book = bookRepository.findByIdWithBookFiles(bookId)
                .orElseThrow(() -> ApiError.BOOK_NOT_FOUND.createException(bookId));

        if (accessibleLibraryIds != null && !accessibleLibraryIds.contains(book.getLibrary().getId())) {
            throw ApiError.FORBIDDEN.createException("Access denied to this book");
        }

        UserBookProgressEntity progress = userBookProgressRepository
                .findByUserIdAndBookId(userId, bookId)
                .orElse(null);

        UserBookFileProgressEntity fileProgress = userBookFileProgressRepository
                .findMostRecentAudiobookProgressByUserIdAndBookId(userId, bookId)
                .orElse(null);

        return mobileBookMapper.toDetail(book, progress, fileProgress);
    }

    @Transactional(readOnly = true)
    public MobilePageResponse<MobileBookSummary> searchBooks(
            String query,
            Integer page,
            Integer size) {

        if (query == null || query.trim().isEmpty()) {
            throw ApiError.INVALID_QUERY_PARAMETERS.createException();
        }

        BookLoreUser user = authenticationService.getAuthenticatedUser();
        Long userId = user.getId();
        Set<Long> accessibleLibraryIds = getAccessibleLibraryIds(user);

        int pageNum = page != null && page >= 0 ? page : 0;
        int pageSize = size != null && size > 0 ? Math.min(size, MAX_PAGE_SIZE) : DEFAULT_PAGE_SIZE;

        Pageable pageable = PageRequest.of(pageNum, pageSize, Sort.by(Sort.Direction.DESC, "addedOn"));

        Specification<BookEntity> spec = MobileBookSpecification.combine(
                MobileBookSpecification.notDeleted(),
                MobileBookSpecification.hasDigitalFile(),
                MobileBookSpecification.inLibraries(accessibleLibraryIds),
                MobileBookSpecification.searchText(query)
        );

        Page<BookEntity> bookPage = bookRepository.findAll(spec, pageable);

        Set<Long> bookIds = bookPage.getContent().stream()
                .map(BookEntity::getId)
                .collect(Collectors.toSet());
        Map<Long, UserBookProgressEntity> progressMap = getProgressMap(userId, bookIds);

        List<MobileBookSummary> summaries = bookPage.getContent().stream()
                .map(book -> mobileBookMapper.toSummary(book, progressMap.get(book.getId())))
                .collect(Collectors.toList());

        return MobilePageResponse.of(summaries, pageNum, pageSize, bookPage.getTotalElements());
    }

    @Transactional(readOnly = true)
    public List<MobileBookSummary> getContinueReading(Integer limit) {
        BookLoreUser user = authenticationService.getAuthenticatedUser();
        Long userId = user.getId();
        Set<Long> accessibleLibraryIds = getAccessibleLibraryIds(user);

        int maxItems = limit != null && limit > 0 ? Math.min(limit, MAX_PAGE_SIZE) : 10;

        Specification<BookEntity> spec = MobileBookSpecification.combine(
                MobileBookSpecification.notDeleted(),
                MobileBookSpecification.hasDigitalFile(),
                MobileBookSpecification.inLibraries(accessibleLibraryIds),
                MobileBookSpecification.inProgress(userId)
        );

        List<BookEntity> books = bookRepository.findAll(spec);

        Set<Long> bookIds = books.stream()
                .map(BookEntity::getId)
                .collect(Collectors.toSet());
        Map<Long, UserBookProgressEntity> progressMap = getProgressMap(userId, bookIds);

        return books.stream()
                .filter(book -> progressMap.containsKey(book.getId()))
                .sorted((b1, b2) -> {
                    Instant t1 = progressMap.get(b1.getId()).getLastReadTime();
                    Instant t2 = progressMap.get(b2.getId()).getLastReadTime();
                    if (t1 == null && t2 == null) return 0;
                    if (t1 == null) return 1;
                    if (t2 == null) return -1;
                    return t2.compareTo(t1);
                })
                .limit(maxItems)
                .map(book -> mobileBookMapper.toSummary(book, progressMap.get(book.getId())))
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<MobileBookSummary> getRecentlyAdded(Integer limit) {
        BookLoreUser user = authenticationService.getAuthenticatedUser();
        Long userId = user.getId();
        Set<Long> accessibleLibraryIds = getAccessibleLibraryIds(user);

        int maxItems = limit != null && limit > 0 ? Math.min(limit, MAX_PAGE_SIZE) : 10;

        Specification<BookEntity> spec = MobileBookSpecification.combine(
                MobileBookSpecification.notDeleted(),
                MobileBookSpecification.hasDigitalFile(),
                MobileBookSpecification.inLibraries(accessibleLibraryIds),
                MobileBookSpecification.addedWithinDays(30)
        );

        Pageable pageable = PageRequest.of(0, maxItems, Sort.by(Sort.Direction.DESC, "addedOn"));
        Page<BookEntity> bookPage = bookRepository.findAll(spec, pageable);

        Set<Long> bookIds = bookPage.getContent().stream()
                .map(BookEntity::getId)
                .collect(Collectors.toSet());
        Map<Long, UserBookProgressEntity> progressMap = getProgressMap(userId, bookIds);

        return bookPage.getContent().stream()
                .map(book -> mobileBookMapper.toSummary(book, progressMap.get(book.getId())))
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public MobilePageResponse<MobileBookSummary> getBooksByMagicShelf(
            Long magicShelfId,
            Integer page,
            Integer size) {

        BookLoreUser user = authenticationService.getAuthenticatedUser();
        Long userId = user.getId();

        int pageNum = page != null && page >= 0 ? page : 0;
        int pageSize = size != null && size > 0 ? Math.min(size, MAX_PAGE_SIZE) : DEFAULT_PAGE_SIZE;

        var booksPage = magicShelfBookService.getBooksByMagicShelfId(userId, magicShelfId, pageNum, pageSize);

        Set<Long> bookIds = booksPage.getContent().stream()
                .map(book -> book.getId())
                .collect(Collectors.toSet());
        Map<Long, UserBookProgressEntity> progressMap = getProgressMap(userId, bookIds);

        List<MobileBookSummary> summaries = booksPage.getContent().stream()
                .map(book -> {
                    BookEntity bookEntity = bookRepository.findById(book.getId()).orElse(null);
                    if (bookEntity == null) {
                        return null;
                    }
                    if (bookEntity.getIsPhysical() != null && bookEntity.getIsPhysical()) {
                        return null;
                    }
                    return mobileBookMapper.toSummary(bookEntity, progressMap.get(book.getId()));
                })
                .filter(summary -> summary != null)
                .collect(Collectors.toList());

        return MobilePageResponse.of(summaries, pageNum, pageSize, booksPage.getTotalElements());
    }

    @Transactional
    public void updateReadStatus(Long bookId, ReadStatus status) {
        BookLoreUser user = authenticationService.getAuthenticatedUser();
        Long userId = user.getId();
        Set<Long> accessibleLibraryIds = getAccessibleLibraryIds(user);

        BookEntity book = bookRepository.findById(bookId)
                .orElseThrow(() -> ApiError.BOOK_NOT_FOUND.createException(bookId));

        if (!accessibleLibraryIds.contains(book.getLibrary().getId())) {
            throw ApiError.FORBIDDEN.createException("Access denied to this book");
        }

        UserBookProgressEntity progress = userBookProgressRepository
                .findByUserIdAndBookId(userId, bookId)
                .orElseGet(() -> createNewProgress(userId, book));

        progress.setReadStatus(status);
        progress.setReadStatusModifiedTime(Instant.now());

        if (status == ReadStatus.READ && progress.getDateFinished() == null) {
            progress.setDateFinished(Instant.now());
        }

        userBookProgressRepository.save(progress);
    }

    @Transactional
    public void updatePersonalRating(Long bookId, Integer rating) {
        BookLoreUser user = authenticationService.getAuthenticatedUser();
        Long userId = user.getId();
        Set<Long> accessibleLibraryIds = getAccessibleLibraryIds(user);

        BookEntity book = bookRepository.findById(bookId)
                .orElseThrow(() -> ApiError.BOOK_NOT_FOUND.createException(bookId));

        if (!accessibleLibraryIds.contains(book.getLibrary().getId())) {
            throw ApiError.FORBIDDEN.createException("Access denied to this book");
        }

        UserBookProgressEntity progress = userBookProgressRepository
                .findByUserIdAndBookId(userId, bookId)
                .orElseGet(() -> createNewProgress(userId, book));

        progress.setPersonalRating(rating);
        userBookProgressRepository.save(progress);
    }

    private UserBookProgressEntity createNewProgress(Long userId, BookEntity book) {
        return UserBookProgressEntity.builder()
                .user(BookLoreUserEntity.builder().id(userId).build())
                .book(book)
                .build();
    }

    private Set<Long> getAccessibleLibraryIds(BookLoreUser user) {
        if (user.getPermissions().isAdmin()) {
            return null;
        }
        if (user.getAssignedLibraries() == null || user.getAssignedLibraries().isEmpty()) {
            return Collections.emptySet();
        }
        return user.getAssignedLibraries().stream()
                .map(Library::getId)
                .collect(Collectors.toSet());
    }

    private Map<Long, UserBookProgressEntity> getProgressMap(Long userId, Set<Long> bookIds) {
        if (bookIds.isEmpty()) {
            return Collections.emptyMap();
        }
        return userBookProgressRepository.findByUserIdAndBookIdIn(userId, bookIds).stream()
                .collect(Collectors.toMap(
                        p -> p.getBook().getId(),
                        Function.identity()
                ));
    }

    private Specification<BookEntity> buildSpecification(
            Set<Long> accessibleLibraryIds,
            Long libraryId,
            Long shelfId,
            ReadStatus status,
            String search,
            Long userId) {

        List<Specification<BookEntity>> specs = new ArrayList<>();
        specs.add(MobileBookSpecification.notDeleted());
        specs.add(MobileBookSpecification.hasDigitalFile());

        if (accessibleLibraryIds != null) {
            if (libraryId != null && accessibleLibraryIds.contains(libraryId)) {
                specs.add(MobileBookSpecification.inLibrary(libraryId));
            } else if (libraryId != null) {
                throw ApiError.FORBIDDEN.createException("Access denied to library " + libraryId);
            } else {
                specs.add(MobileBookSpecification.inLibraries(accessibleLibraryIds));
            }
        } else if (libraryId != null) {
            specs.add(MobileBookSpecification.inLibrary(libraryId));
        }

        if (shelfId != null) {
            ShelfEntity shelf = shelfRepository.findById(shelfId)
                    .orElseThrow(() -> ApiError.SHELF_NOT_FOUND.createException(shelfId));
            if (!shelf.isPublic() && !shelf.getUser().getId().equals(userId)) {
                throw ApiError.FORBIDDEN.createException("Access denied to shelf " + shelfId);
            }
            specs.add(MobileBookSpecification.inShelf(shelfId));
        }

        if (status != null) {
            specs.add(MobileBookSpecification.withReadStatus(status, userId));
        }

        if (search != null && !search.trim().isEmpty()) {
            specs.add(MobileBookSpecification.searchText(search));
        }

        return MobileBookSpecification.combine(specs.toArray(new Specification[0]));
    }

    private Sort buildSort(String sortBy, String sortDir) {
        Sort.Direction direction = "asc".equalsIgnoreCase(sortDir)
                ? Sort.Direction.ASC
                : Sort.Direction.DESC;

        String field = switch (sortBy != null ? sortBy.toLowerCase() : "") {
            case "title" -> "metadata.title";
            case "seriesname", "series" -> "metadata.seriesName";
            case "lastreadtime" -> "addedOn";
            default -> "addedOn";
        };

        return Sort.by(direction, field);
    }
}
