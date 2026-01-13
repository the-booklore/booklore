package com.adityachandel.booklore.service.book;

import com.adityachandel.booklore.config.security.service.AuthenticationService;
import com.adityachandel.booklore.exception.ApiError;
import com.adityachandel.booklore.mapper.BookMapper;
import com.adityachandel.booklore.model.dto.*;
import com.adityachandel.booklore.model.dto.progress.*;
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
    private final EpubViewerPreferenceV2Repository epubViewerPreferenceV2Repository;

    public void updateBookViewerSetting(long bookId, BookViewerSettings bookViewerSettings) {
        BookEntity bookEntity = bookRepository.findById(bookId).orElseThrow(() -> ApiError.BOOK_NOT_FOUND.createException(bookId));
        BookLoreUser user = authenticationService.getAuthenticatedUser();

        if (bookEntity.getBookType() == BookFileType.PDF) {
            if (bookViewerSettings.getPdfSettings() != null) {
                PdfViewerPreferencesEntity pdfPrefs = pdfViewerPreferencesRepository
                        .findByBookIdAndUserId(bookId, user.getId())
                        .orElseGet(() -> {
                            PdfViewerPreferencesEntity newPrefs = PdfViewerPreferencesEntity.builder()
                                    .bookId(bookId)
                                    .userId(user.getId())
                                    .build();
                            return pdfViewerPreferencesRepository.save(newPrefs);
                        });
                PdfViewerPreferences pdfSettings = bookViewerSettings.getPdfSettings();
                pdfPrefs.setZoom(pdfSettings.getZoom());
                pdfPrefs.setSpread(pdfSettings.getSpread());
                pdfViewerPreferencesRepository.save(pdfPrefs);
            }
            if (bookViewerSettings.getNewPdfSettings() != null) {
                NewPdfViewerPreferencesEntity pdfPrefs = newPdfViewerPreferencesRepository.findByBookIdAndUserId(bookId, user.getId())
                        .orElseGet(() -> {
                            NewPdfViewerPreferencesEntity entity = NewPdfViewerPreferencesEntity.builder()
                                    .bookId(bookId)
                                    .userId(user.getId())
                                    .build();
                            return newPdfViewerPreferencesRepository.save(entity);
                        });
                NewPdfViewerPreferences pdfSettings = bookViewerSettings.getNewPdfSettings();
                pdfPrefs.setPageSpread(pdfSettings.getPageSpread());
                pdfPrefs.setPageViewMode(pdfSettings.getPageViewMode());
                newPdfViewerPreferencesRepository.save(pdfPrefs);
            }
        } else if (bookEntity.getBookType() == BookFileType.EPUB) {
            EpubViewerPreferenceV2Entity epubPrefs = epubViewerPreferenceV2Repository
                    .findByBookIdAndUserId(bookId, user.getId())
                    .orElseGet(() -> {
                        EpubViewerPreferencesV2 epubSettings = bookViewerSettings.getEpubSettingsV2();
                        EpubViewerPreferenceV2Entity newPrefs = EpubViewerPreferenceV2Entity.builder()
                                .bookId(bookId)
                                .userId(user.getId())
                                .fontFamily(epubSettings != null && epubSettings.getFontFamily() != null ? epubSettings.getFontFamily() : "serif")
                                .fontSize(epubSettings != null && epubSettings.getFontSize() != null ? epubSettings.getFontSize() : 16)
                                .gap(epubSettings != null && epubSettings.getGap() != null ? epubSettings.getGap() : 0.05f)
                                .hyphenate(epubSettings != null && epubSettings.getHyphenate() != null ? epubSettings.getHyphenate() : false)
                                .isDark(epubSettings != null && epubSettings.getIsDark() != null ? epubSettings.getIsDark() : false)
                                .justify(epubSettings != null && epubSettings.getJustify() != null ? epubSettings.getJustify() : false)
                                .lineHeight(epubSettings != null && epubSettings.getLineHeight() != null ? epubSettings.getLineHeight() : 1.5f)
                                .maxBlockSize(epubSettings != null && epubSettings.getMaxBlockSize() != null ? epubSettings.getMaxBlockSize() : 720)
                                .maxColumnCount(epubSettings != null && epubSettings.getMaxColumnCount() != null ? epubSettings.getMaxColumnCount() : 2)
                                .maxInlineSize(epubSettings != null && epubSettings.getMaxInlineSize() != null ? epubSettings.getMaxInlineSize() : 1080)
                                .theme(epubSettings != null && epubSettings.getTheme() != null ? epubSettings.getTheme() : "gray")
                                .flow(epubSettings != null && epubSettings.getFlow() != null ? epubSettings.getFlow() : "paginated")
                                .build();
                        return epubViewerPreferenceV2Repository.save(newPrefs);
                    });

            EpubViewerPreferencesV2 epubSettings = bookViewerSettings.getEpubSettingsV2();
            epubPrefs.setUserId(user.getId());
            epubPrefs.setBookId(bookId);
            epubPrefs.setFontFamily(epubSettings.getFontFamily());
            epubPrefs.setFontSize(epubSettings.getFontSize());
            epubPrefs.setGap(epubSettings.getGap());
            epubPrefs.setHyphenate(epubSettings.getHyphenate());
            epubPrefs.setIsDark(epubSettings.getIsDark());
            epubPrefs.setJustify(epubSettings.getJustify());
            epubPrefs.setLineHeight(epubSettings.getLineHeight());
            epubPrefs.setMaxBlockSize(epubSettings.getMaxBlockSize());
            epubPrefs.setMaxColumnCount(epubSettings.getMaxColumnCount());
            epubPrefs.setMaxInlineSize(epubSettings.getMaxInlineSize());
            epubPrefs.setTheme(epubSettings.getTheme());
            epubPrefs.setFlow("paginated");
            epubViewerPreferenceV2Repository.save(epubPrefs);

        } else if (bookEntity.getBookType() == BookFileType.CBX) {
            CbxViewerPreferencesEntity cbxPrefs = cbxViewerPreferencesRepository
                    .findByBookIdAndUserId(bookId, user.getId())
                    .orElseGet(() -> {
                        CbxViewerPreferencesEntity newPrefs = CbxViewerPreferencesEntity.builder()
                                .bookId(bookId)
                                .userId(user.getId())
                                .build();
                        return cbxViewerPreferencesRepository.save(newPrefs);
                    });

            CbxViewerPreferences cbxSettings = bookViewerSettings.getCbxSettings();
            cbxPrefs.setPageSpread(cbxSettings.getPageSpread());
            cbxPrefs.setPageViewMode(cbxSettings.getPageViewMode());
            cbxPrefs.setFitMode(cbxSettings.getFitMode());
            cbxPrefs.setScrollMode(cbxSettings.getScrollMode());
            cbxPrefs.setBackgroundColor(cbxSettings.getBackgroundColor());
            cbxViewerPreferencesRepository.save(cbxPrefs);

        } else {
            throw ApiError.UNSUPPORTED_BOOK_TYPE.createException();
        }
    }

    @Transactional
    public void updateReadProgress(ReadProgressRequest request) {
        BookEntity book = bookRepository.findById(request.getBookId()).orElseThrow(() -> ApiError.BOOK_NOT_FOUND.createException(request.getBookId()));

        BookLoreUser user = authenticationService.getAuthenticatedUser();

        UserBookProgressEntity progress = userBookProgressRepository
                .findByUserIdAndBookId(user.getId(), book.getId())
                .orElseGet(UserBookProgressEntity::new);

        BookLoreUserEntity userEntity = userRepository.findById(user.getId())
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));
        progress.setUser(userEntity);

        progress.setBook(book);
        progress.setLastReadTime(Instant.now());

        Float percentage = null;
        switch (book.getBookType()) {
            case EPUB, FB2, MOBI, AZW3 -> {
                if (request.getEpubProgress() != null) {
                    progress.setEpubProgress(request.getEpubProgress().getCfi());
                    progress.setEpubProgressHref(request.getEpubProgress().getHref());
                    percentage = request.getEpubProgress().getPercentage();
                }
            }
            case PDF -> {
                if (request.getPdfProgress() != null) {
                    progress.setPdfProgress(request.getPdfProgress().getPage());
                    percentage = request.getPdfProgress().getPercentage();
                }
            }
            case CBX -> {
                if (request.getCbxProgress() != null) {
                    progress.setCbxProgress(request.getCbxProgress().getPage());
                    percentage = request.getCbxProgress().getPercentage();
                }
            }
        }

        if (percentage != null) {
            progress.setReadStatus(getStatus(percentage));
            setProgressPercent(progress, book.getBookType(), percentage);
        }

        if (request.getDateFinished() != null) {
            progress.setDateFinished(request.getDateFinished());
        }

        userBookProgressRepository.save(progress);
    }

    @Transactional
    public List<BookStatusUpdateResponse> updateReadStatus(List<Long> bookIds, String status) {
        BookLoreUser user = authenticationService.getAuthenticatedUser();

        if (bookIds.size() > 1 && !UserPermission.CAN_BULK_RESET_BOOK_READ_STATUS.isGranted(user.getPermissions())) {
            throw ApiError.PERMISSION_DENIED.createException(UserPermission.CAN_BULK_RESET_BOOK_READ_STATUS);
        }

        ReadStatus readStatus = EnumUtils.getEnumIgnoreCase(ReadStatus.class, status);

        Set<Long> existingProgressBookIds = validateBooksAndGetExistingProgress(user.getId(), bookIds);

        Instant now = Instant.now();
        Instant dateFinished = readStatus == ReadStatus.READ ? now : null;

        if (!existingProgressBookIds.isEmpty()) {
            userBookProgressRepository.bulkUpdateReadStatus(user.getId(), new ArrayList<>(existingProgressBookIds), readStatus, now, dateFinished);
        }

        Set<Long> newProgressBookIds = bookIds.stream()
                .filter(id -> !existingProgressBookIds.contains(id))
                .collect(Collectors.toSet());

        if (!newProgressBookIds.isEmpty()) {
            BookLoreUserEntity userEntity = userRepository.findById(user.getId()).orElseThrow(() -> new UsernameNotFoundException("User not found"));

            List<UserBookProgressEntity> newProgressEntities = newProgressBookIds.stream()
                    .map(bookId -> {
                        UserBookProgressEntity progress = new UserBookProgressEntity();
                        progress.setUser(userEntity);

                        BookEntity bookEntity = new BookEntity();
                        bookEntity.setId(bookId);
                        progress.setBook(bookEntity);

                        progress.setReadStatus(readStatus);
                        progress.setReadStatusModifiedTime(now);
                        progress.setDateFinished(dateFinished);
                        return progress;
                    })
                    .collect(Collectors.toList());

            userBookProgressRepository.saveAll(newProgressEntities);
        }

        return bookIds.stream()
                .map(bookId -> BookStatusUpdateResponse.builder()
                        .bookId(bookId)
                        .readStatus(readStatus)
                        .readStatusModifiedTime(now)
                        .dateFinished(dateFinished)
                        .build())
                .collect(Collectors.toList());
    }

    @Transactional
    public List<BookStatusUpdateResponse> resetProgress(List<Long> bookIds, ResetProgressType type) {
        BookLoreUser user = authenticationService.getAuthenticatedUser();

        if (bookIds.size() > 1 && type == ResetProgressType.BOOKLORE && !UserPermission.CAN_BULK_RESET_BOOKLORE_READ_PROGRESS.isGranted(user.getPermissions())) {
            throw ApiError.PERMISSION_DENIED.createException(UserPermission.CAN_BULK_RESET_BOOKLORE_READ_PROGRESS);
        }

        if (bookIds.size() > 1 && type == ResetProgressType.KOREADER && !UserPermission.CAN_BULK_RESET_KOREADER_READ_PROGRESS.isGranted(user.getPermissions())) {
            throw ApiError.PERMISSION_DENIED.createException(UserPermission.CAN_BULK_RESET_KOREADER_READ_PROGRESS);
        }

        Set<Long> existingProgressBookIds = validateBooksAndGetExistingProgress(user.getId(), bookIds);

        Instant now = Instant.now();

        if (!existingProgressBookIds.isEmpty()) {
            if (type == ResetProgressType.BOOKLORE) {
                userBookProgressRepository.bulkResetBookloreProgress(user.getId(), new ArrayList<>(existingProgressBookIds), now);
            } else if (type == ResetProgressType.KOREADER) {
                userBookProgressRepository.bulkResetKoreaderProgress(user.getId(), new ArrayList<>(existingProgressBookIds));
            } else if (type == ResetProgressType.KOBO) {
                userBookProgressRepository.bulkResetKoboProgress(user.getId(), new ArrayList<>(existingProgressBookIds));
                existingProgressBookIds.forEach(koboReadingStateService::deleteReadingState);
            }
        }

        return bookIds.stream()
                .map(bookId -> BookStatusUpdateResponse.builder()
                        .bookId(bookId)
                        .readStatus(null)
                        .readStatusModifiedTime(existingProgressBookIds.contains(bookId) ? now : null)
                        .dateFinished(null)
                        .build())
                .collect(Collectors.toList());
    }

    @Transactional
    public List<PersonalRatingUpdateResponse> updatePersonalRating(List<Long> bookIds, Integer rating) {
        BookLoreUser user = authenticationService.getAuthenticatedUser();

        Set<Long> existingProgressBookIds = validateBooksAndGetExistingProgress(user.getId(), bookIds);

        if (!existingProgressBookIds.isEmpty()) {
            userBookProgressRepository.bulkUpdatePersonalRating(user.getId(), new ArrayList<>(existingProgressBookIds), rating);
        }

        Set<Long> newProgressBookIds = bookIds.stream()
                .filter(id -> !existingProgressBookIds.contains(id))
                .collect(Collectors.toSet());

        if (!newProgressBookIds.isEmpty()) {
            BookLoreUserEntity userEntity = userRepository.findById(user.getId()).orElseThrow(() -> new UsernameNotFoundException("User not found"));

            List<UserBookProgressEntity> newProgressEntities = newProgressBookIds.stream()
                    .map(bookId -> {
                        UserBookProgressEntity progress = new UserBookProgressEntity();
                        progress.setUser(userEntity);

                        BookEntity bookEntity = new BookEntity();
                        bookEntity.setId(bookId);
                        progress.setBook(bookEntity);

                        progress.setPersonalRating(rating);
                        return progress;
                    })
                    .collect(Collectors.toList());

            userBookProgressRepository.saveAll(newProgressEntities);
        }

        return bookIds.stream()
                .map(bookId -> PersonalRatingUpdateResponse.builder()
                        .bookId(bookId)
                        .personalRating(rating)
                        .build())
                .collect(Collectors.toList());
    }

    @Transactional
    public List<PersonalRatingUpdateResponse> resetPersonalRating(List<Long> bookIds) {
        BookLoreUser user = authenticationService.getAuthenticatedUser();

        Set<Long> existingProgressBookIds = validateBooksAndGetExistingProgress(user.getId(), bookIds);

        if (!existingProgressBookIds.isEmpty()) {
            userBookProgressRepository.bulkUpdatePersonalRating(user.getId(), new ArrayList<>(existingProgressBookIds), null);
        }

        return bookIds.stream()
                .map(bookId -> PersonalRatingUpdateResponse.builder()
                        .bookId(bookId)
                        .personalRating(null)
                        .build())
                .collect(Collectors.toList());
    }

    @Transactional
    public List<Book> assignShelvesToBooks(Set<Long> bookIds, Set<Long> shelfIdsToAssign, Set<Long> shelfIdsToUnassign) {
        BookLoreUser user = authenticationService.getAuthenticatedUser();
        BookLoreUserEntity userEntity = userRepository.findById(user.getId()).orElseThrow(() -> ApiError.USER_NOT_FOUND.createException(user.getId()));

        Set<Long> userShelfIds = userEntity.getShelves().stream().map(ShelfEntity::getId).collect(Collectors.toSet());

        if (!userShelfIds.containsAll(shelfIdsToAssign)) {
            throw ApiError.GENERIC_UNAUTHORIZED.createException("Cannot assign shelves that do not belong to the user.");
        }
        if (!userShelfIds.containsAll(shelfIdsToUnassign)) {
            throw ApiError.GENERIC_UNAUTHORIZED.createException("Cannot unassign shelves that do not belong to the user.");
        }

        List<BookEntity> bookEntities = bookQueryService.findAllWithMetadataByIds(bookIds);
        List<ShelfEntity> shelvesToAssign = shelfRepository.findAllById(shelfIdsToAssign);
        for (BookEntity bookEntity : bookEntities) {
            bookEntity.getShelves().removeIf(shelf -> shelfIdsToUnassign.contains(shelf.getId()));
            bookEntity.getShelves().addAll(shelvesToAssign);
        }
        bookRepository.saveAll(bookEntities);

        Map<Long, UserBookProgressEntity> progressMap = userProgressService.fetchUserProgress(
                user.getId(), bookEntities.stream().map(BookEntity::getId).collect(Collectors.toSet()));

        return bookEntities.stream().map(bookEntity -> {
            Book book = bookMapper.toBook(bookEntity);
            book.setShelves(filterShelvesByUserId(book.getShelves(), user.getId()));
            book.setFilePath(FileUtils.getBookFullPath(bookEntity));
            enrichBookWithProgress(book, progressMap.get(bookEntity.getId()));
            return book;
        }).collect(Collectors.toList());
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
            case EPUB -> progress.setEpubProgressPercent(percentage);
            case PDF -> progress.setPdfProgressPercent(percentage);
            case CBX -> progress.setCbxProgressPercent(percentage);
        }
    }

    private ReadStatus getStatus(Float percentage) {
        if (percentage >= 99.5f) return ReadStatus.READ;
        if (percentage > 0.5f) return ReadStatus.READING;
        return ReadStatus.UNREAD;
    }

    private void enrichBookWithProgress(Book book, UserBookProgressEntity progress) {
        if (progress != null) {
            setBookProgress(book, progress);
            book.setLastReadTime(progress.getLastReadTime());
            book.setReadStatus(progress.getReadStatus() == null ? String.valueOf(ReadStatus.UNSET) : String.valueOf(progress.getReadStatus()));
            book.setDateFinished(progress.getDateFinished());
            book.setPersonalRating(progress.getPersonalRating());
        }
    }

    private void setBookProgress(Book book, UserBookProgressEntity progress) {
        if (progress.getKoboProgressPercent() != null) {
            book.setKoboProgress(KoboProgress.builder()
                    .percentage(progress.getKoboProgressPercent())
                    .build());
        }

        switch (book.getBookType()) {
            case EPUB -> {
                book.setEpubProgress(EpubProgress.builder()
                        .cfi(progress.getEpubProgress())
                        .href(progress.getEpubProgressHref())
                        .percentage(progress.getEpubProgressPercent())
                        .build());
                book.setKoreaderProgress(KoProgress.builder()
                        .percentage(progress.getKoreaderProgressPercent() != null ? progress.getKoreaderProgressPercent() * 100 : null)
                        .build());
            }
            case MOBI, AZW3, FB2 -> book.setEpubProgress(EpubProgress.builder()
                    .cfi(progress.getEpubProgress())
                    .href(progress.getEpubProgressHref())
                    .percentage(progress.getEpubProgressPercent())
                    .build());
            case PDF -> book.setPdfProgress(PdfProgress.builder()
                    .page(progress.getPdfProgress())
                    .percentage(progress.getPdfProgressPercent())
                    .build());
            case CBX -> book.setCbxProgress(CbxProgress.builder()
                    .page(progress.getCbxProgress())
                    .percentage(progress.getCbxProgressPercent())
                    .build());
        }
    }

    private Set<Shelf> filterShelvesByUserId(Set<Shelf> shelves, Long userId) {
        if (shelves == null) return Collections.emptySet();
        return shelves.stream()
                .filter(shelf -> userId.equals(shelf.getUserId()))
                .collect(Collectors.toSet());
    }
}

