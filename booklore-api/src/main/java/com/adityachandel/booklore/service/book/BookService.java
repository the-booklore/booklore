package com.adityachandel.booklore.service.book;

import com.adityachandel.booklore.config.security.service.AuthenticationService;
import com.adityachandel.booklore.exception.ApiError;
import com.adityachandel.booklore.mapper.BookMapper;
import com.adityachandel.booklore.model.dto.*;
import com.adityachandel.booklore.model.dto.progress.*;
import com.adityachandel.booklore.model.dto.request.ReadProgressRequest;
import com.adityachandel.booklore.model.dto.response.BookDeletionResponse;
import com.adityachandel.booklore.model.dto.response.BookStatusUpdateResponse;
import com.adityachandel.booklore.model.entity.BookEntity;
import com.adityachandel.booklore.model.entity.LibraryPathEntity;
import com.adityachandel.booklore.model.entity.UserBookProgressEntity;
import com.adityachandel.booklore.model.enums.BookFileType;
import com.adityachandel.booklore.model.enums.ReadStatus;
import com.adityachandel.booklore.repository.*;
import com.adityachandel.booklore.service.monitoring.MonitoringRegistrationService;
import com.adityachandel.booklore.service.user.UserProgressService;
import com.adityachandel.booklore.util.FileService;
import com.adityachandel.booklore.util.FileUtils;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@AllArgsConstructor
@Service
public class BookService {

    private final BookRepository bookRepository;
    private final PdfViewerPreferencesRepository pdfViewerPreferencesRepository;
    private final EpubViewerPreferencesRepository epubViewerPreferencesRepository;
    private final CbxViewerPreferencesRepository cbxViewerPreferencesRepository;
    private final NewPdfViewerPreferencesRepository newPdfViewerPreferencesRepository;
    private final FileService fileService;
    private final BookMapper bookMapper;
    private final UserBookProgressRepository userBookProgressRepository;
    private final AuthenticationService authenticationService;
    private final BookQueryService bookQueryService;
    private final UserProgressService userProgressService;
    private final BookDownloadService bookDownloadService;
    private final MonitoringRegistrationService monitoringRegistrationService;
    private final BookUpdateService bookUpdateService;


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
                        .percentage(progress.getEpubProgressPercent())
                        .build());
                book.setKoreaderProgress(KoProgress.builder()
                        .percentage(progress.getKoreaderProgressPercent() != null ? progress.getKoreaderProgressPercent() * 100 : null)
                        .build());
            }
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

    private void enrichBookWithProgress(Book book, UserBookProgressEntity progress) {
        if (progress != null) {
            setBookProgress(book, progress);
            book.setLastReadTime(progress.getLastReadTime());
            book.setReadStatus(progress.getReadStatus() == null ? String.valueOf(ReadStatus.UNSET) : String.valueOf(progress.getReadStatus()));
            book.setDateFinished(progress.getDateFinished());
            book.setPersonalRating(progress.getPersonalRating());
        }
    }

    public List<Book> getBookDTOs(boolean includeDescription) {
        BookLoreUser user = authenticationService.getAuthenticatedUser();
        boolean isAdmin = user.getPermissions().isAdmin();

        List<Book> books = isAdmin
                ? bookQueryService.getAllBooks(includeDescription)
                : bookQueryService.getAllBooksByLibraryIds(
                user.getAssignedLibraries().stream()
                        .map(Library::getId)
                        .collect(Collectors.toSet()),
                includeDescription,
                user.getId()
        );

        Map<Long, UserBookProgressEntity> progressMap =
                userProgressService.fetchUserProgress(
                        user.getId(),
                        books.stream().map(Book::getId).collect(Collectors.toSet())
                );

        books.forEach(book -> {
            enrichBookWithProgress(book, progressMap.get(book.getId()));
            book.setShelves(filterShelvesByUserId(book.getShelves(), user.getId()));
        });

        return books;
    }

    public List<Book> getBooksByIds(Set<Long> bookIds, boolean withDescription) {
        BookLoreUser user = authenticationService.getAuthenticatedUser();

        List<BookEntity> bookEntities = bookQueryService.findAllWithMetadataByIds(bookIds);

        Map<Long, UserBookProgressEntity> progressMap = userProgressService.fetchUserProgress(
                user.getId(), bookEntities.stream().map(BookEntity::getId).collect(Collectors.toSet()));

        return bookEntities.stream().map(bookEntity -> {
            Book book = bookMapper.toBook(bookEntity);
            book.setFilePath(FileUtils.getBookFullPath(bookEntity));
            if (!withDescription) book.getMetadata().setDescription(null);
            enrichBookWithProgress(book, progressMap.get(bookEntity.getId()));
            return book;
        }).collect(Collectors.toList());
    }

    public Book getBook(long bookId, boolean withDescription) {
        BookLoreUser user = authenticationService.getAuthenticatedUser();
        BookEntity bookEntity = bookRepository.findById(bookId).orElseThrow(() -> ApiError.BOOK_NOT_FOUND.createException(bookId));

        UserBookProgressEntity userProgress = userBookProgressRepository.findByUserIdAndBookId(user.getId(), bookId).orElse(new UserBookProgressEntity());

        Book book = bookMapper.toBook(bookEntity);
        book.setShelves(filterShelvesByUserId(book.getShelves(), user.getId()));
        book.setLastReadTime(userProgress.getLastReadTime());

        if (userProgress.getKoboProgressPercent() != null) {
            book.setKoboProgress(KoboProgress.builder()
                    .percentage(userProgress.getKoboProgressPercent())
                    .build());
        }

        if (bookEntity.getBookType() == BookFileType.PDF) {
            book.setPdfProgress(PdfProgress.builder()
                    .page(userProgress.getPdfProgress())
                    .percentage(userProgress.getPdfProgressPercent())
                    .build());
        }
        if (bookEntity.getBookType() == BookFileType.EPUB) {
            book.setEpubProgress(EpubProgress.builder()
                    .cfi(userProgress.getEpubProgress())
                    .percentage(userProgress.getEpubProgressPercent())
                    .build());
            if (userProgress.getKoreaderProgressPercent() != null) {
                if (book.getKoreaderProgress() == null) {
                    book.setKoreaderProgress(KoProgress.builder().build());
                }
                book.getKoreaderProgress().setPercentage(userProgress.getKoreaderProgressPercent() * 100);
            }
        }
        if (bookEntity.getBookType() == BookFileType.CBX) {
            book.setCbxProgress(CbxProgress.builder()
                    .page(userProgress.getCbxProgress())
                    .percentage(userProgress.getCbxProgressPercent())
                    .build());
        }
        book.setFilePath(FileUtils.getBookFullPath(bookEntity));
        book.setReadStatus(userProgress.getReadStatus() == null ? String.valueOf(ReadStatus.UNSET) : String.valueOf(userProgress.getReadStatus()));
        book.setDateFinished(userProgress.getDateFinished());
        book.setPersonalRating(userProgress.getPersonalRating());

        if (!withDescription) {
            book.getMetadata().setDescription(null);
        }

        return book;
    }

    public BookViewerSettings getBookViewerSetting(long bookId) {
        BookEntity bookEntity = bookRepository.findById(bookId).orElseThrow(() -> ApiError.BOOK_NOT_FOUND.createException(bookId));
        BookLoreUser user = authenticationService.getAuthenticatedUser();

        BookViewerSettings.BookViewerSettingsBuilder settingsBuilder = BookViewerSettings.builder();
        if (bookEntity.getBookType() == BookFileType.EPUB) {
            epubViewerPreferencesRepository.findByBookIdAndUserId(bookId, user.getId())
                    .ifPresent(epubPref -> settingsBuilder.epubSettings(EpubViewerPreferences.builder()
                            .bookId(bookId)
                            .font(epubPref.getFont())
                            .fontSize(epubPref.getFontSize())
                            .theme(epubPref.getTheme())
                            .flow(epubPref.getFlow())
                            .spread(epubPref.getSpread())
                            .letterSpacing(epubPref.getLetterSpacing())
                            .lineHeight(epubPref.getLineHeight())
                            .build()));
        } else if (bookEntity.getBookType() == BookFileType.PDF) {
            pdfViewerPreferencesRepository.findByBookIdAndUserId(bookId, user.getId())
                    .ifPresent(pdfPref -> settingsBuilder.pdfSettings(PdfViewerPreferences.builder()
                            .bookId(bookId)
                            .zoom(pdfPref.getZoom())
                            .spread(pdfPref.getSpread())
                            .build()));
            newPdfViewerPreferencesRepository.findByBookIdAndUserId(bookId, user.getId())
                    .ifPresent(pdfPref -> settingsBuilder.newPdfSettings(NewPdfViewerPreferences.builder()
                            .bookId(bookId)
                            .pageViewMode(pdfPref.getPageViewMode())
                            .pageSpread(pdfPref.getPageSpread())
                            .build()));
        } else if (bookEntity.getBookType() == BookFileType.CBX) {
            cbxViewerPreferencesRepository.findByBookIdAndUserId(bookId, user.getId())
                    .ifPresent(cbxPref -> settingsBuilder.cbxSettings(CbxViewerPreferences.builder()
                            .bookId(bookId)
                            .pageViewMode(cbxPref.getPageViewMode())
                            .pageSpread(cbxPref.getPageSpread())
                            .fitMode(cbxPref.getFitMode())
                            .scrollMode(cbxPref.getScrollMode())
                            .backgroundColor(cbxPref.getBackgroundColor())
                            .build()));
        } else {
            throw ApiError.UNSUPPORTED_BOOK_TYPE.createException();
        }
        return settingsBuilder.build();
    }

    public void updateBookViewerSetting(long bookId, BookViewerSettings bookViewerSettings) {
        bookUpdateService.updateBookViewerSetting(bookId, bookViewerSettings);
    }

    @Transactional
    public void updateReadProgress(ReadProgressRequest request) {
        bookUpdateService.updateReadProgress(request);
    }

    @Transactional
    public List<BookStatusUpdateResponse> updateReadStatus(List<Long> bookIds, String status) {
        return bookUpdateService.updateReadStatus(bookIds, status);
    }

    @Transactional
    public List<Book> assignShelvesToBooks(Set<Long> bookIds, Set<Long> shelfIdsToAssign, Set<Long> shelfIdsToUnassign) {
        return bookUpdateService.assignShelvesToBooks(bookIds, shelfIdsToAssign, shelfIdsToUnassign);
    }

    public Resource getBookThumbnail(long bookId) {
        Path thumbnailPath = Paths.get(fileService.getThumbnailFile(bookId));
        try {
            if (Files.exists(thumbnailPath)) {
                return new UrlResource(thumbnailPath.toUri());
            } else {
                Path defaultCover = Paths.get("static/images/missing-cover.jpg");
                return new UrlResource(defaultCover.toUri());
            }
        } catch (MalformedURLException e) {
            throw new RuntimeException("Failed to load book cover for bookId=" + bookId, e);
        }
    }

    public Resource getBookCover(long bookId) {
        Path coverPath = Paths.get(fileService.getCoverFile(bookId));
        try {
            if (Files.exists(coverPath)) {
                return new UrlResource(coverPath.toUri());
            } else {
                return new ClassPathResource("static/images/missing-cover.jpg");
            }
        } catch (MalformedURLException e) {
            throw new RuntimeException("Failed to load book cover for bookId=" + bookId, e);
        }
    }

    public Resource getBookCover(String coverHash) {
        BookEntity bookEntity = bookRepository.findByBookCoverHash(coverHash).orElseThrow(() -> ApiError.BOOK_NOT_FOUND.createException(coverHash));
        return getBookCover(bookEntity.getId());
    }

    public Resource getBackgroundImage() {
        try {
            BookLoreUser user = authenticationService.getAuthenticatedUser();
            return fileService.getBackgroundResource(user.getId());
        } catch (Exception e) {
            log.error("Failed to get background image: {}", e.getMessage(), e);
            return fileService.getBackgroundResource(null);
        }
    }

    public ResponseEntity<Resource> downloadBook(Long bookId) {
        return bookDownloadService.downloadBook(bookId);
    }

    public ResponseEntity<ByteArrayResource> getBookContent(long bookId) throws IOException {
        BookEntity bookEntity = bookRepository.findById(bookId).orElseThrow(() -> ApiError.BOOK_NOT_FOUND.createException(bookId));
        try (FileInputStream inputStream = new FileInputStream(FileUtils.getBookFullPath(bookEntity))) {
            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_OCTET_STREAM)
                    .body(new ByteArrayResource(inputStream.readAllBytes()));
        }
    }


    @Transactional
    public ResponseEntity<BookDeletionResponse> deleteBooks(Set<Long> ids) {
        List<BookEntity> books = bookQueryService.findAllWithMetadataByIds(ids);
        List<Long> failedFileDeletions = new ArrayList<>();
        for (BookEntity book : books) {
            Path fullFilePath = book.getFullFilePath();
            try {
                if (Files.exists(fullFilePath)) {
                    try {
                        monitoringRegistrationService.unregisterSpecificPath(fullFilePath.getParent());
                    } catch (Exception ex) {
                        log.warn("Failed to unregister monitoring for path: {}", fullFilePath.getParent(), ex);
                    }
                    Files.delete(fullFilePath);
                    log.info("Deleted book file: {}", fullFilePath);

                    Set<Path> libraryRoots = book.getLibrary().getLibraryPaths().stream()
                            .map(LibraryPathEntity::getPath)
                            .map(Paths::get)
                            .map(Path::normalize)
                            .collect(Collectors.toSet());

                    deleteEmptyParentDirsUpToLibraryFolders(fullFilePath.getParent(), libraryRoots);
                }
            } catch (IOException e) {
                log.warn("Failed to delete book file: {}", fullFilePath, e);
                failedFileDeletions.add(book.getId());
            } finally {
                monitoringRegistrationService.registerSpecificPath(fullFilePath.getParent(), book.getLibrary().getId());
            }
        }

        bookRepository.deleteAll(books);
        BookDeletionResponse response = new BookDeletionResponse(ids, failedFileDeletions);
        return failedFileDeletions.isEmpty()
                ? ResponseEntity.ok(response)
                : ResponseEntity.status(HttpStatus.MULTI_STATUS).body(response);
    }

    public void deleteEmptyParentDirsUpToLibraryFolders(Path currentDir, Set<Path> libraryRoots) {
        Path dir = currentDir;
        Set<String> ignoredFilenames = Set.of(".DS_Store", "Thumbs.db");
        dir = dir.toAbsolutePath().normalize();

        Set<Path> normalizedRoots = new HashSet<>();
        for (Path root : libraryRoots) {
            normalizedRoots.add(root.toAbsolutePath().normalize());
        }

        while (dir != null) {
            boolean isLibraryRoot = false;
            for (Path root : normalizedRoots) {
                try {
                    if (Files.isSameFile(root, dir)) {
                        isLibraryRoot = true;
                        break;
                    }
                } catch (IOException e) {
                    log.warn("Failed to compare paths: {} and {}", root, dir);
                }
            }

            if (isLibraryRoot) {
                log.debug("Reached library root: {}. Stopping cleanup.", dir);
                break;
            }

            File[] files = dir.toFile().listFiles();
            if (files == null) {
                log.warn("Cannot read directory: {}. Stopping cleanup.", dir);
                break;
            }

            boolean hasImportantFiles = false;
            for (File file : files) {
                if (!ignoredFilenames.contains(file.getName())) {
                    hasImportantFiles = true;
                    break;
                }
            }

            if (!hasImportantFiles) {
                for (File file : files) {
                    try {
                        Files.delete(file.toPath());
                        log.info("Deleted ignored file: {}", file.getAbsolutePath());
                    } catch (IOException e) {
                        log.warn("Failed to delete ignored file: {}", file.getAbsolutePath());
                    }
                }
                try {
                    Files.delete(dir);
                    log.info("Deleted empty directory: {}", dir);
                } catch (IOException e) {
                    log.warn("Failed to delete directory: {}", dir, e);
                    break;
                }
                dir = dir.getParent();
            } else {
                log.debug("Directory {} contains important files. Stopping cleanup.", dir);
                break;
            }
        }
    }

    private Set<Shelf> filterShelvesByUserId(Set<Shelf> shelves, Long userId) {
        if (shelves == null) return Collections.emptySet();
        return shelves.stream()
                .filter(shelf -> userId.equals(shelf.getUserId()))
                .collect(Collectors.toSet());
    }
}

