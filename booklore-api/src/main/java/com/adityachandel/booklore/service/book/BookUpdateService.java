package com.adityachandel.booklore.service.book;

import com.adityachandel.booklore.config.security.service.AuthenticationService;
import com.adityachandel.booklore.exception.ApiError;
import com.adityachandel.booklore.mapper.BookMapper;
import com.adityachandel.booklore.model.dto.*;
import com.adityachandel.booklore.model.dto.progress.CbxProgress;
import com.adityachandel.booklore.model.dto.progress.EpubProgress;
import com.adityachandel.booklore.model.dto.progress.PdfProgress;
import com.adityachandel.booklore.model.dto.request.ReadProgressRequest;
import com.adityachandel.booklore.model.dto.response.BookStatusUpdateResponse;
import com.adityachandel.booklore.model.dto.response.PersonalRatingUpdateResponse;
import com.adityachandel.booklore.model.entity.*;
import com.adityachandel.booklore.model.enums.BookFileType;
import com.adityachandel.booklore.model.enums.ReadStatus;
import com.adityachandel.booklore.model.enums.ResetProgressType;
import com.adityachandel.booklore.model.enums.UserPermission;
import com.adityachandel.booklore.repository.*;
import com.adityachandel.booklore.service.kobo.KoboReadingStateService;
import com.adityachandel.booklore.service.user.UserProgressService;
import com.adityachandel.booklore.util.BookProgressUtil;
import com.adityachandel.booklore.util.FileUtils;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.EnumUtils;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@AllArgsConstructor
@Service
public class BookUpdateService {

    private static final float READING_THRESHOLD = 0.5f;
    private static final float COMPLETED_THRESHOLD = 99.5f;

    private final BookRepository bookRepository;
    private final PdfViewerPreferencesRepository pdfViewerPreferencesRepository;
    private final CbxViewerPreferencesRepository cbxViewerPreferencesRepository;
    private final NewPdfViewerPreferencesRepository newPdfViewerPreferencesRepository;
    private final ShelfRepository shelfRepository;
    private final BookMapper bookMapper;
    private final UserRepository userRepository;
    private final UserBookProgressRepository userBookProgressRepository;
    private final AuthenticationService authenticationService;
    private final BookQueryService bookQueryService;
    private final UserProgressService userProgressService;
    private final KoboReadingStateService koboReadingStateService;
    private final EbookViewerPreferenceRepository ebookViewerPreferenceRepository;

    public void updateBookViewerSetting(long bookId, BookViewerSettings bookViewerSettings) {
        BookEntity book = bookRepository.findByIdWithBookFiles(bookId).orElseThrow(() -> ApiError.BOOK_NOT_FOUND.createException(bookId));
        BookLoreUser user = authenticationService.getAuthenticatedUser();
        if (book.getPrimaryBookFile().getBookType() == null) {
            throw ApiError.UNSUPPORTED_BOOK_TYPE.createException();
        }
        switch (book.getPrimaryBookFile().getBookType()) {
            case PDF -> updatePdfViewerSettings(bookId, user.getId(), bookViewerSettings);
            case EPUB, FB2, MOBI, AZW3 -> updateEbookViewerSettings(bookId, user.getId(), bookViewerSettings);
            case CBX -> updateCbxViewerSettings(bookId, user.getId(), bookViewerSettings);
            default -> throw ApiError.UNSUPPORTED_BOOK_TYPE.createException();
        }
    }

    @Transactional
    public void updateReadProgress(ReadProgressRequest request) {
        BookEntity book = bookRepository.findByIdWithBookFiles(request.getBookId()).orElseThrow(() -> ApiError.BOOK_NOT_FOUND.createException(request.getBookId()));
        BookLoreUser user = authenticationService.getAuthenticatedUser();

        UserBookProgressEntity progress = userBookProgressRepository
                .findByUserIdAndBookId(user.getId(), book.getId())
                .orElseGet(UserBookProgressEntity::new);

        BookLoreUserEntity userEntity = findUserOrThrow(user.getId());
        progress.setUser(userEntity);
        progress.setBook(book);
        progress.setLastReadTime(Instant.now());

        Float percentage = updateProgressByBookType(progress, book.getPrimaryBookFile().getBookType(), request);
        if (percentage != null) {
            progress.setReadStatus(calculateReadStatus(percentage));
            setProgressPercent(progress, book.getPrimaryBookFile().getBookType(), percentage);
        }
        if (request.getDateFinished() != null) {
            progress.setDateFinished(request.getDateFinished());
        }

        userBookProgressRepository.save(progress);
    }

    @Transactional
    public List<BookStatusUpdateResponse> updateReadStatus(List<Long> bookIds, String status) {
        BookLoreUser user = authenticationService.getAuthenticatedUser();
        validateBulkOperationPermission(bookIds, user, UserPermission.CAN_BULK_RESET_BOOK_READ_STATUS);

        ReadStatus readStatus = EnumUtils.getEnumIgnoreCase(ReadStatus.class, status);
        Set<Long> existingProgressBookIds = validateBooksAndGetExistingProgress(user.getId(), bookIds);

        Instant now = Instant.now();
        Instant dateFinished = readStatus == ReadStatus.READ ? now : null;

        updateExistingProgress(user.getId(), existingProgressBookIds, readStatus, now, dateFinished);
        createNewProgress(user.getId(), bookIds, existingProgressBookIds, readStatus, now, dateFinished);

        return buildStatusUpdateResponses(bookIds, readStatus, now, dateFinished);
    }

    @Transactional
    public List<BookStatusUpdateResponse> resetProgress(List<Long> bookIds, ResetProgressType type) {
        BookLoreUser user = authenticationService.getAuthenticatedUser();
        validateResetPermission(bookIds, user, type);

        Set<Long> existingProgressBookIds = validateBooksAndGetExistingProgress(user.getId(), bookIds);
        Instant now = Instant.now();

        if (!existingProgressBookIds.isEmpty()) {
            performReset(user.getId(), existingProgressBookIds, type, now);
        }

        return buildResetResponses(bookIds, existingProgressBookIds, now);
    }

    @Transactional
    public List<PersonalRatingUpdateResponse> updatePersonalRating(List<Long> bookIds, Integer rating) {
        BookLoreUser user = authenticationService.getAuthenticatedUser();
        Set<Long> existingProgressBookIds = validateBooksAndGetExistingProgress(user.getId(), bookIds);

        if (!existingProgressBookIds.isEmpty()) {
            userBookProgressRepository.bulkUpdatePersonalRating(user.getId(), new ArrayList<>(existingProgressBookIds), rating);
        }

        createProgressForRating(user.getId(), bookIds, existingProgressBookIds, rating);

        return buildRatingUpdateResponses(bookIds, rating);
    }

    @Transactional
    public List<PersonalRatingUpdateResponse> resetPersonalRating(List<Long> bookIds) {
        BookLoreUser user = authenticationService.getAuthenticatedUser();
        Set<Long> existingProgressBookIds = validateBooksAndGetExistingProgress(user.getId(), bookIds);

        if (!existingProgressBookIds.isEmpty()) {
            userBookProgressRepository.bulkUpdatePersonalRating(user.getId(), new ArrayList<>(existingProgressBookIds), null);
        }

        return buildRatingUpdateResponses(bookIds, null);
    }

    @Transactional
    public List<Book> assignShelvesToBooks(Set<Long> bookIds, Set<Long> shelfIdsToAssign, Set<Long> shelfIdsToUnassign) {
        BookLoreUser user = authenticationService.getAuthenticatedUser();
        BookLoreUserEntity userEntity = findUserOrThrow(user.getId());

        validateShelfOwnership(userEntity, shelfIdsToAssign, shelfIdsToUnassign);

        List<BookEntity> bookEntities = bookQueryService.findAllWithMetadataByIds(bookIds);
        List<ShelfEntity> shelvesToAssign = shelfRepository.findAllById(shelfIdsToAssign);

        updateBookShelves(bookEntities, shelvesToAssign, shelfIdsToUnassign);
        bookRepository.saveAll(bookEntities);

        return buildBooksWithProgress(bookEntities, user.getId());
    }

    private void updatePdfViewerSettings(long bookId, Long userId, BookViewerSettings settings) {
        if (settings.getPdfSettings() != null) {
            PdfViewerPreferencesEntity prefs = findOrCreatePdfPreferences(bookId, userId);
            PdfViewerPreferences pdfSettings = settings.getPdfSettings();
            prefs.setZoom(pdfSettings.getZoom());
            prefs.setSpread(pdfSettings.getSpread());
            pdfViewerPreferencesRepository.save(prefs);
        }

        if (settings.getNewPdfSettings() != null) {
            NewPdfViewerPreferencesEntity prefs = findOrCreateNewPdfPreferences(bookId, userId);
            NewPdfViewerPreferences pdfSettings = settings.getNewPdfSettings();
            prefs.setPageSpread(pdfSettings.getPageSpread());
            prefs.setPageViewMode(pdfSettings.getPageViewMode());
            prefs.setFitMode(pdfSettings.getFitMode());
            prefs.setScrollMode(pdfSettings.getScrollMode());
            prefs.setBackgroundColor(pdfSettings.getBackgroundColor());
            newPdfViewerPreferencesRepository.save(prefs);
        }
    }

    private void updateEbookViewerSettings(long bookId, Long userId, BookViewerSettings settings) {
        EbookViewerPreferenceEntity prefs = findOrCreateEbookPreferences(bookId, userId);
        EbookViewerPreferences epubSettings = settings.getEbookSettings();

        prefs.setUserId(userId);
        prefs.setBookId(bookId);
        prefs.setFontFamily(epubSettings.getFontFamily());
        prefs.setFontSize(epubSettings.getFontSize());
        prefs.setGap(epubSettings.getGap());
        prefs.setHyphenate(epubSettings.getHyphenate());
        prefs.setIsDark(epubSettings.getIsDark());
        prefs.setJustify(epubSettings.getJustify());
        prefs.setLineHeight(epubSettings.getLineHeight());
        prefs.setMaxBlockSize(epubSettings.getMaxBlockSize());
        prefs.setMaxColumnCount(epubSettings.getMaxColumnCount());
        prefs.setMaxInlineSize(epubSettings.getMaxInlineSize());
        prefs.setTheme(epubSettings.getTheme());
        prefs.setFlow(epubSettings.getFlow());

        ebookViewerPreferenceRepository.save(prefs);
    }

    private void updateCbxViewerSettings(long bookId, Long userId, BookViewerSettings settings) {
        CbxViewerPreferencesEntity prefs = findOrCreateCbxPreferences(bookId, userId);
        CbxViewerPreferences cbxSettings = settings.getCbxSettings();

        prefs.setPageSpread(cbxSettings.getPageSpread());
        prefs.setPageViewMode(cbxSettings.getPageViewMode());
        prefs.setFitMode(cbxSettings.getFitMode());
        prefs.setScrollMode(cbxSettings.getScrollMode());
        prefs.setBackgroundColor(cbxSettings.getBackgroundColor());

        cbxViewerPreferencesRepository.save(prefs);
    }

    private PdfViewerPreferencesEntity findOrCreatePdfPreferences(long bookId, Long userId) {
        return pdfViewerPreferencesRepository
                .findByBookIdAndUserId(bookId, userId)
                .orElseGet(() -> pdfViewerPreferencesRepository.save(
                        PdfViewerPreferencesEntity.builder()
                                .bookId(bookId)
                                .userId(userId)
                                .build()
                ));
    }

    private NewPdfViewerPreferencesEntity findOrCreateNewPdfPreferences(long bookId, Long userId) {
        return newPdfViewerPreferencesRepository
                .findByBookIdAndUserId(bookId, userId)
                .orElseGet(() -> newPdfViewerPreferencesRepository.save(
                        NewPdfViewerPreferencesEntity.builder()
                                .bookId(bookId)
                                .userId(userId)
                                .build()
                ));
    }

    private EbookViewerPreferenceEntity findOrCreateEbookPreferences(long bookId, Long userId) {
        return ebookViewerPreferenceRepository
                .findByBookIdAndUserId(bookId, userId)
                .orElseGet(() -> ebookViewerPreferenceRepository.save(
                        EbookViewerPreferenceEntity.builder()
                                .bookId(bookId)
                                .userId(userId)
                                .build()
                ));
    }

    private CbxViewerPreferencesEntity findOrCreateCbxPreferences(long bookId, Long userId) {
        return cbxViewerPreferencesRepository
                .findByBookIdAndUserId(bookId, userId)
                .orElseGet(() -> cbxViewerPreferencesRepository.save(
                        CbxViewerPreferencesEntity.builder()
                                .bookId(bookId)
                                .userId(userId)
                                .build()
                ));
    }

    private Float updateProgressByBookType(UserBookProgressEntity progress, BookFileType bookType, ReadProgressRequest request) {
        return switch (bookType) {
            case EPUB, FB2, MOBI, AZW3 -> updateEbookProgress(progress, request.getEpubProgress());
            case PDF -> updatePdfProgress(progress, request.getPdfProgress());
            case CBX -> updateCbxProgress(progress, request.getCbxProgress());
        };
    }

    private Float updateEbookProgress(UserBookProgressEntity progress, EpubProgress epubProgress) {
        if (epubProgress == null) return null;

        progress.setEpubProgress(epubProgress.getCfi());
        progress.setEpubProgressHref(epubProgress.getHref());

        float percentage = epubProgress.getPercentage();
        return Math.round(percentage * 10f) / 10f;
    }

    private Float updatePdfProgress(UserBookProgressEntity progress, PdfProgress pdfProgress) {
        if (pdfProgress == null) return null;

        progress.setPdfProgress(pdfProgress.getPage());
        float percentage = pdfProgress.getPercentage();
        return Math.round(percentage * 10f) / 10f;
    }

    private Float updateCbxProgress(UserBookProgressEntity progress, CbxProgress cbxProgress) {
        if (cbxProgress == null) return null;

        progress.setCbxProgress(cbxProgress.getPage());
        float percentage = cbxProgress.getPercentage();
        return Math.round(percentage * 10f) / 10f;
    }

    private void updateExistingProgress(Long userId, Set<Long> bookIds, ReadStatus status, Instant now, Instant dateFinished) {
        if (!bookIds.isEmpty()) {
            userBookProgressRepository.bulkUpdateReadStatus(userId, new ArrayList<>(bookIds), status, now, dateFinished);
        }
    }

    private void createNewProgress(Long userId, List<Long> allBookIds, Set<Long> existingBookIds, ReadStatus status, Instant now, Instant dateFinished) {
        Set<Long> newProgressBookIds = allBookIds.stream()
                .filter(id -> !existingBookIds.contains(id))
                .collect(Collectors.toSet());

        if (newProgressBookIds.isEmpty()) return;

        BookLoreUserEntity userEntity = findUserOrThrow(userId);
        List<UserBookProgressEntity> newProgressEntities = newProgressBookIds.stream()
                .map(bookId -> createProgressEntity(userEntity, bookId, status, now, dateFinished))
                .collect(Collectors.toList());

        userBookProgressRepository.saveAll(newProgressEntities);
    }

    private UserBookProgressEntity createProgressEntity(BookLoreUserEntity user, Long bookId, ReadStatus status, Instant now, Instant dateFinished) {
        UserBookProgressEntity progress = new UserBookProgressEntity();
        progress.setUser(user);

        BookEntity bookEntity = new BookEntity();
        bookEntity.setId(bookId);
        progress.setBook(bookEntity);

        progress.setReadStatus(status);
        progress.setReadStatusModifiedTime(now);
        progress.setDateFinished(dateFinished);
        return progress;
    }

    private void performReset(Long userId, Set<Long> bookIds, ResetProgressType type, Instant now) {
        List<Long> bookIdList = new ArrayList<>(bookIds);

        switch (type) {
            case BOOKLORE -> userBookProgressRepository.bulkResetBookloreProgress(userId, bookIdList, now);
            case KOREADER -> userBookProgressRepository.bulkResetKoreaderProgress(userId, bookIdList);
            case KOBO -> {
                userBookProgressRepository.bulkResetKoboProgress(userId, bookIdList);
                bookIds.forEach(koboReadingStateService::deleteReadingState);
            }
        }
    }

    private void createProgressForRating(Long userId, List<Long> allBookIds, Set<Long> existingBookIds, Integer rating) {
        Set<Long> newProgressBookIds = allBookIds.stream()
                .filter(id -> !existingBookIds.contains(id))
                .collect(Collectors.toSet());

        if (newProgressBookIds.isEmpty()) return;

        BookLoreUserEntity userEntity = findUserOrThrow(userId);
        List<UserBookProgressEntity> newProgressEntities = newProgressBookIds.stream()
                .map(bookId -> createProgressEntityWithRating(userEntity, bookId, rating))
                .collect(Collectors.toList());

        userBookProgressRepository.saveAll(newProgressEntities);
    }

    private UserBookProgressEntity createProgressEntityWithRating(BookLoreUserEntity user, Long bookId, Integer rating) {
        UserBookProgressEntity progress = new UserBookProgressEntity();
        progress.setUser(user);

        BookEntity bookEntity = new BookEntity();
        bookEntity.setId(bookId);
        progress.setBook(bookEntity);

        progress.setPersonalRating(rating);
        return progress;
    }

    private void validateShelfOwnership(BookLoreUserEntity user, Set<Long> shelfIdsToAssign, Set<Long> shelfIdsToUnassign) {
        Set<Long> userShelfIds = user.getShelves().stream()
                .map(ShelfEntity::getId)
                .collect(Collectors.toSet());

        if (!userShelfIds.containsAll(shelfIdsToAssign)) {
            throw ApiError.GENERIC_UNAUTHORIZED.createException("Cannot assign shelves that do not belong to the user.");
        }
        if (!userShelfIds.containsAll(shelfIdsToUnassign)) {
            throw ApiError.GENERIC_UNAUTHORIZED.createException("Cannot unassign shelves that do not belong to the user.");
        }
    }

    private void updateBookShelves(List<BookEntity> books, List<ShelfEntity> shelvesToAssign, Set<Long> shelfIdsToUnassign) {
        for (BookEntity book : books) {
            book.getShelves().removeIf(shelf -> shelfIdsToUnassign.contains(shelf.getId()));
            book.getShelves().addAll(shelvesToAssign);
        }
    }

    private List<Book> buildBooksWithProgress(List<BookEntity> bookEntities, Long userId) {
        Set<Long> bookIds = bookEntities.stream().map(BookEntity::getId).collect(Collectors.toSet());
        Map<Long, UserBookProgressEntity> progressMap = userProgressService.fetchUserProgress(userId, bookIds);

        return bookEntities.stream()
                .map(bookEntity -> buildBook(bookEntity, userId, progressMap))
                .collect(Collectors.toList());
    }

    private Book buildBook(BookEntity bookEntity, Long userId, Map<Long, UserBookProgressEntity> progressMap) {
        Book book = bookMapper.toBook(bookEntity);
        book.setShelves(filterShelvesByUserId(book.getShelves(), userId));
        book.setFilePath(FileUtils.getBookFullPath(bookEntity));
        BookProgressUtil.enrichBookWithProgress(book, progressMap.get(bookEntity.getId()));
        return book;
    }

    private BookLoreUserEntity findUserOrThrow(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new UsernameNotFoundException("User not found with ID: " + userId));
    }

    private void validateBulkOperationPermission(List<Long> bookIds, BookLoreUser user, UserPermission permission) {
        if (bookIds.size() > 1 && !permission.isGranted(user.getPermissions())) {
            throw ApiError.PERMISSION_DENIED.createException(permission);
        }
    }

    private void validateResetPermission(List<Long> bookIds, BookLoreUser user, ResetProgressType type) {
        if (bookIds.size() <= 1) return;
        UserPermission permission = switch (type) {
            case BOOKLORE -> UserPermission.CAN_BULK_RESET_BOOKLORE_READ_PROGRESS;
            case KOREADER -> UserPermission.CAN_BULK_RESET_KOREADER_READ_PROGRESS;
            default -> null;
        };
        if (permission != null && !permission.isGranted(user.getPermissions())) {
            throw ApiError.PERMISSION_DENIED.createException(permission);
        }
    }

    private Set<Long> validateBooksAndGetExistingProgress(Long userId, List<Long> bookIds) {
        long existingBooksCount = bookRepository.countByIdIn(bookIds);
        if (existingBooksCount != bookIds.size()) {
            throw ApiError.BOOK_NOT_FOUND.createException("One or more books not found");
        }

        return userBookProgressRepository.findExistingProgressBookIds(userId, new HashSet<>(bookIds));
    }

    private void setProgressPercent(UserBookProgressEntity progress, BookFileType type, Float percentage) {
        switch (type) {
            case EPUB, FB2, MOBI, AZW3 -> progress.setEpubProgressPercent(percentage);
            case PDF -> progress.setPdfProgressPercent(percentage);
            case CBX -> progress.setCbxProgressPercent(percentage);
        }
    }

    private ReadStatus calculateReadStatus(Float percentage) {
        if (percentage >= COMPLETED_THRESHOLD) return ReadStatus.READ;
        if (percentage > READING_THRESHOLD) return ReadStatus.READING;
        return ReadStatus.UNREAD;
    }

    private List<BookStatusUpdateResponse> buildStatusUpdateResponses(List<Long> bookIds, ReadStatus status, Instant now, Instant dateFinished) {
        return bookIds.stream()
                .map(bookId -> BookStatusUpdateResponse.builder()
                        .bookId(bookId)
                        .readStatus(status)
                        .readStatusModifiedTime(now)
                        .dateFinished(dateFinished)
                        .build())
                .collect(Collectors.toList());
    }

    private List<BookStatusUpdateResponse> buildResetResponses(List<Long> bookIds, Set<Long> existingBookIds, Instant now) {
        return bookIds.stream()
                .map(bookId -> BookStatusUpdateResponse.builder()
                        .bookId(bookId)
                        .readStatus(null)
                        .readStatusModifiedTime(existingBookIds.contains(bookId) ? now : null)
                        .dateFinished(null)
                        .build())
                .collect(Collectors.toList());
    }

    private List<PersonalRatingUpdateResponse> buildRatingUpdateResponses(List<Long> bookIds, Integer rating) {
        return bookIds.stream()
                .map(bookId -> PersonalRatingUpdateResponse.builder()
                        .bookId(bookId)
                        .personalRating(rating)
                        .build())
                .collect(Collectors.toList());
    }

    private Set<Shelf> filterShelvesByUserId(Set<Shelf> shelves, Long userId) {
        if (shelves == null) return Collections.emptySet();
        return shelves.stream()
                .filter(shelf -> userId.equals(shelf.getUserId()))
                .collect(Collectors.toSet());
    }
}

