package org.booklore.mobile.service;

import org.booklore.config.security.service.AuthenticationService;
import org.booklore.exception.ApiError;
import org.booklore.mobile.dto.MobileBookDetail;
import org.booklore.mobile.dto.MobileBookSummary;
import org.booklore.mobile.dto.MobileFilterOptions;
import org.booklore.mobile.dto.MobilePageResponse;
import org.booklore.mobile.mapper.MobileBookMapper;
import org.booklore.mobile.specification.MobileBookSpecification;
import org.booklore.model.dto.Book;
import org.booklore.model.dto.BookLoreUser;
import org.booklore.model.dto.Library;
import org.booklore.model.entity.*;
import org.booklore.model.enums.BookFileType;
import org.booklore.model.enums.ReadStatus;
import org.booklore.repository.BookRepository;
import org.booklore.repository.ShelfRepository;
import org.booklore.repository.UserBookFileProgressRepository;
import org.booklore.repository.UserBookProgressRepository;
import org.booklore.service.opds.MagicShelfBookService;
import lombok.AllArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.EntityManager;
import jakarta.persistence.Tuple;
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
    private final EntityManager entityManager;

    @Transactional(readOnly = true)
    public MobilePageResponse<MobileBookSummary> getBooks(
            Integer page,
            Integer size,
            String sortBy,
            String sortDir,
            Long libraryId,
            Long shelfId,
            ReadStatus status,
            String search,
            BookFileType fileType,
            Integer minRating,
            Integer maxRating,
            String authors,
            String language) {

        BookLoreUser user = authenticationService.getAuthenticatedUser();
        Long userId = user.getId();
        Set<Long> accessibleLibraryIds = getAccessibleLibraryIds(user);

        int pageNum = page != null && page >= 0 ? page : 0;
        int pageSize = size != null && size > 0 ? Math.min(size, MAX_PAGE_SIZE) : DEFAULT_PAGE_SIZE;

        Sort sort = buildSort(sortBy, sortDir);
        Pageable pageable = PageRequest.of(pageNum, pageSize, sort);

        Specification<BookEntity> spec = buildSpecification(
                accessibleLibraryIds, libraryId, shelfId, status, search, userId,
                fileType, minRating, maxRating, authors, language);

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

        int pageNum = validatePageNumber(page);
        int pageSize = validatePageSize(size);

        Pageable pageable = PageRequest.of(pageNum, pageSize, Sort.by(Sort.Direction.DESC, "addedOn"));

        Specification<BookEntity> spec = MobileBookSpecification.combine(
                MobileBookSpecification.notDeleted(),
                MobileBookSpecification.hasDigitalFile(),
                MobileBookSpecification.inLibraries(accessibleLibraryIds),
                MobileBookSpecification.searchText(query)
        );

        Page<BookEntity> bookPage = bookRepository.findAll(spec, pageable);
        return buildPageResponse(bookPage, userId, pageNum, pageSize);
    }

    @Transactional(readOnly = true)
    public List<MobileBookSummary> getContinueReading(Integer limit) {
        BookLoreUser user = authenticationService.getAuthenticatedUser();
        Long userId = user.getId();
        Set<Long> accessibleLibraryIds = getAccessibleLibraryIds(user);

        int maxItems = validateLimit(limit, 10);

        Specification<BookEntity> spec = MobileBookSpecification.combine(
                MobileBookSpecification.notDeleted(),
                MobileBookSpecification.hasDigitalFile(),
                MobileBookSpecification.inLibraries(accessibleLibraryIds),
                MobileBookSpecification.inProgress(userId),
                MobileBookSpecification.hasNonAudiobookFile()
        );

        List<BookEntity> books = bookRepository.findAll(spec);
        Map<Long, UserBookProgressEntity> progressMap = getProgressMapForBooks(userId, books);

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
    public List<MobileBookSummary> getContinueListening(Integer limit) {
        BookLoreUser user = authenticationService.getAuthenticatedUser();
        Long userId = user.getId();
        Set<Long> accessibleLibraryIds = getAccessibleLibraryIds(user);

        int maxItems = validateLimit(limit, 10);

        Specification<BookEntity> spec = MobileBookSpecification.combine(
                MobileBookSpecification.notDeleted(),
                MobileBookSpecification.hasDigitalFile(),
                MobileBookSpecification.inLibraries(accessibleLibraryIds),
                MobileBookSpecification.inProgress(userId),
                MobileBookSpecification.hasAudiobookFile()
        );

        List<BookEntity> books = bookRepository.findAll(spec);
        Map<Long, UserBookProgressEntity> progressMap = getProgressMapForBooks(userId, books);

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

        int maxItems = validateLimit(limit, 10);

        Specification<BookEntity> spec = MobileBookSpecification.combine(
                MobileBookSpecification.notDeleted(),
                MobileBookSpecification.hasDigitalFile(),
                MobileBookSpecification.inLibraries(accessibleLibraryIds),
                MobileBookSpecification.addedWithinDays(30)
        );

        Pageable pageable = PageRequest.of(0, maxItems, Sort.by(Sort.Direction.DESC, "addedOn"));
        Page<BookEntity> bookPage = bookRepository.findAll(spec, pageable);
        Map<Long, UserBookProgressEntity> progressMap = getProgressMapForBooks(userId, bookPage.getContent());

        return bookPage.getContent().stream()
                .map(book -> mobileBookMapper.toSummary(book, progressMap.get(book.getId())))
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public MobilePageResponse<MobileBookSummary> getRandomBooks(
            Integer page,
            Integer size,
            Long libraryId) {

        BookLoreUser user = authenticationService.getAuthenticatedUser();
        Long userId = user.getId();
        Set<Long> accessibleLibraryIds = getAccessibleLibraryIds(user);

        int pageNum = validatePageNumber(page);
        int pageSize = validatePageSize(size);

        Specification<BookEntity> spec = buildBaseSpecification(accessibleLibraryIds, libraryId);

        long totalElements = bookRepository.count(spec);

        if (totalElements == 0) {
            return MobilePageResponse.of(Collections.emptyList(), pageNum, pageSize, 0L);
        }

        long maxOffset = Math.max(0, totalElements - pageSize);
        int randomOffset = (int) (Math.random() * (maxOffset + 1));

        Pageable pageable = PageRequest.of(randomOffset / pageSize, pageSize);
        Page<BookEntity> bookPage = bookRepository.findAll(spec, pageable);

        return buildPageResponse(bookPage, userId, pageNum, pageSize);
    }

    @Transactional(readOnly = true)
    public MobilePageResponse<MobileBookSummary> getBooksByMagicShelf(
            Long magicShelfId,
            Integer page,
            Integer size) {

        BookLoreUser user = authenticationService.getAuthenticatedUser();
        Long userId = user.getId();

        int pageNum = validatePageNumber(page);
        int pageSize = validatePageSize(size);

        var booksPage = magicShelfBookService.getBooksByMagicShelfId(userId, magicShelfId, pageNum, pageSize);

        Set<Long> bookIds = booksPage.getContent().stream()
                .map(Book::getId)
                .collect(Collectors.toSet());

        if (bookIds.isEmpty()) {
            return MobilePageResponse.of(Collections.emptyList(), pageNum, pageSize, 0L);
        }

        List<BookEntity> bookEntities = bookRepository.findAllById(bookIds);
        Map<Long, UserBookProgressEntity> progressMap = getProgressMapForBooks(userId, bookEntities);

        List<MobileBookSummary> summaries = bookEntities.stream()
                .filter(bookEntity -> bookEntity.getIsPhysical() == null || !bookEntity.getIsPhysical())
                .map(bookEntity -> mobileBookMapper.toSummary(bookEntity, progressMap.get(bookEntity.getId())))
                .collect(Collectors.toList());

        return MobilePageResponse.of(summaries, pageNum, pageSize, booksPage.getTotalElements());
    }

    @Transactional(readOnly = true)
    public MobileFilterOptions getFilterOptions() {
        BookLoreUser user = authenticationService.getAuthenticatedUser();
        Set<Long> accessibleLibraryIds = getAccessibleLibraryIds(user);

        // Base filter: non-deleted digital books in accessible libraries
        String libraryClause = accessibleLibraryIds != null
                ? "AND b.library.id IN :libraryIds"
                : "";

        // Authors with book count (top 200 by count)
        String authorQuery = """
                SELECT a.name, COUNT(DISTINCT b.id) FROM BookEntity b
                JOIN b.metadata m JOIN m.authors a
                WHERE (b.deleted IS NULL OR b.deleted = false)
                AND (b.isPhysical IS NULL OR b.isPhysical = false)
                """ + libraryClause + """
                GROUP BY a.name ORDER BY COUNT(DISTINCT b.id) DESC
                """;
        var authorQ = entityManager.createQuery(authorQuery, Tuple.class);
        if (accessibleLibraryIds != null) authorQ.setParameter("libraryIds", accessibleLibraryIds);
        authorQ.setMaxResults(200);

        List<MobileFilterOptions.AuthorOption> authors = authorQ.getResultList().stream()
                .map(t -> MobileFilterOptions.AuthorOption.builder()
                        .name(t.get(0, String.class))
                        .count(t.get(1, Long.class))
                        .build())
                .toList();

        // Languages with book count
        String langQuery = """
                SELECT m.language, COUNT(DISTINCT b.id) FROM BookEntity b
                JOIN b.metadata m
                WHERE (b.deleted IS NULL OR b.deleted = false)
                AND (b.isPhysical IS NULL OR b.isPhysical = false)
                AND m.language IS NOT NULL AND m.language <> ''
                """ + libraryClause + """
                GROUP BY m.language ORDER BY COUNT(DISTINCT b.id) DESC
                """;
        var langQ = entityManager.createQuery(langQuery, Tuple.class);
        if (accessibleLibraryIds != null) langQ.setParameter("libraryIds", accessibleLibraryIds);

        List<MobileFilterOptions.LanguageOption> languages = langQ.getResultList().stream()
                .map(t -> MobileFilterOptions.LanguageOption.builder()
                        .code(t.get(0, String.class))
                        .label(Locale.forLanguageTag(t.get(0, String.class)).getDisplayLanguage(Locale.ENGLISH))
                        .count(t.get(1, Long.class))
                        .build())
                .toList();

        // Distinct file types present in accessible books
        String fileTypeQuery = """
                SELECT DISTINCT bf.bookType FROM BookEntity b
                JOIN b.bookFiles bf
                WHERE (b.deleted IS NULL OR b.deleted = false)
                AND (b.isPhysical IS NULL OR b.isPhysical = false)
                AND bf.isBookFormat = true
                """ + libraryClause;
        var ftQ = entityManager.createQuery(fileTypeQuery, BookFileType.class);
        if (accessibleLibraryIds != null) ftQ.setParameter("libraryIds", accessibleLibraryIds);

        List<String> fileTypes = ftQ.getResultList().stream()
                .map(Enum::name)
                .sorted()
                .toList();

        // Read statuses — return all meaningful values
        List<String> readStatuses = Arrays.stream(ReadStatus.values())
                .filter(s -> s != ReadStatus.UNSET)
                .map(Enum::name)
                .toList();

        return MobileFilterOptions.builder()
                .authors(authors)
                .languages(languages)
                .fileTypes(fileTypes)
                .readStatuses(readStatuses)
                .build();
    }

    @Transactional
    public void updateReadStatus(Long bookId, ReadStatus status) {
        UserBookProgressEntity progress = validateAccessAndGetProgress(bookId);

        progress.setReadStatus(status);
        progress.setReadStatusModifiedTime(Instant.now());

        if (status == ReadStatus.READ && progress.getDateFinished() == null) {
            progress.setDateFinished(Instant.now());
        }

        userBookProgressRepository.save(progress);
    }

    @Transactional
    public void updatePersonalRating(Long bookId, Integer rating) {
        UserBookProgressEntity progress = validateAccessAndGetProgress(bookId);

        progress.setPersonalRating(rating);
        userBookProgressRepository.save(progress);
    }

    private UserBookProgressEntity validateAccessAndGetProgress(Long bookId) {
        BookLoreUser user = authenticationService.getAuthenticatedUser();
        Long userId = user.getId();
        Set<Long> accessibleLibraryIds = getAccessibleLibraryIds(user);

        BookEntity book = bookRepository.findById(bookId)
                .orElseThrow(() -> ApiError.BOOK_NOT_FOUND.createException(bookId));

        validateLibraryAccess(accessibleLibraryIds, book.getLibrary().getId());

        return userBookProgressRepository
                .findByUserIdAndBookId(userId, bookId)
                .orElseGet(() -> createNewProgress(userId, book));
    }

    private void validateLibraryAccess(Set<Long> accessibleLibraryIds, Long libraryId) {
        if (accessibleLibraryIds != null && !accessibleLibraryIds.contains(libraryId)) {
            throw ApiError.FORBIDDEN.createException("Access denied to this book");
        }
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
            Long userId,
            BookFileType fileType,
            Integer minRating,
            Integer maxRating,
            String authors,
            String language) {

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

        if (fileType != null) {
            specs.add(MobileBookSpecification.withFileType(fileType));
        }

        if (minRating != null) {
            specs.add(MobileBookSpecification.withMinRating(minRating, userId));
        }

        if (maxRating != null) {
            specs.add(MobileBookSpecification.withMaxRating(maxRating, userId));
        }

        if (authors != null && !authors.trim().isEmpty()) {
            specs.add(MobileBookSpecification.withAuthor(authors.trim()));
        }

        if (language != null && !language.trim().isEmpty()) {
            specs.add(MobileBookSpecification.withLanguage(language.trim()));
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

    private int validatePageNumber(Integer page) {
        return page != null && page >= 0 ? page : 0;
    }

    private int validatePageSize(Integer size) {
        return size != null && size > 0 ? Math.min(size, MAX_PAGE_SIZE) : DEFAULT_PAGE_SIZE;
    }

    private int validateLimit(Integer limit, int defaultValue) {
        return limit != null && limit > 0 ? Math.min(limit, MAX_PAGE_SIZE) : defaultValue;
    }

    private Specification<BookEntity> buildBaseSpecification(Set<Long> accessibleLibraryIds, Long libraryId) {
        List<Specification<BookEntity>> specs = new ArrayList<>();
        specs.add(MobileBookSpecification.notDeleted());
        specs.add(MobileBookSpecification.hasDigitalFile());

        if (accessibleLibraryIds != null) {
            if (libraryId != null && !accessibleLibraryIds.contains(libraryId)) {
                throw ApiError.FORBIDDEN.createException("Access denied to library " + libraryId);
            }
            specs.add(libraryId != null
                    ? MobileBookSpecification.inLibrary(libraryId)
                    : MobileBookSpecification.inLibraries(accessibleLibraryIds));
        } else if (libraryId != null) {
            specs.add(MobileBookSpecification.inLibrary(libraryId));
        }

        return MobileBookSpecification.combine(specs.toArray(new Specification[0]));
    }

    private MobilePageResponse<MobileBookSummary> buildPageResponse(
            Page<BookEntity> bookPage,
            Long userId,
            int pageNum,
            int pageSize) {

        Map<Long, UserBookProgressEntity> progressMap = getProgressMapForBooks(userId, bookPage.getContent());

        List<MobileBookSummary> summaries = bookPage.getContent().stream()
                .map(book -> mobileBookMapper.toSummary(book, progressMap.get(book.getId())))
                .collect(Collectors.toList());

        return MobilePageResponse.of(summaries, pageNum, pageSize, bookPage.getTotalElements());
    }

    private Map<Long, UserBookProgressEntity> getProgressMapForBooks(Long userId, List<BookEntity> books) {
        if (books.isEmpty()) {
            return Collections.emptyMap();
        }
        Set<Long> bookIds = books.stream()
                .map(BookEntity::getId)
                .collect(Collectors.toSet());
        return getProgressMap(userId, bookIds);
    }
}
