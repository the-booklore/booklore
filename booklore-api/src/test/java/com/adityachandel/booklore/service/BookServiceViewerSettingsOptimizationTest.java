package com.adityachandel.booklore.service;

import com.adityachandel.booklore.config.security.service.AuthenticationService;
import com.adityachandel.booklore.mapper.BookMapper;
import com.adityachandel.booklore.model.dto.BookViewerSettings;
import com.adityachandel.booklore.model.entity.*;
import com.adityachandel.booklore.model.enums.BookFileType;
import com.adityachandel.booklore.repository.*;
import com.adityachandel.booklore.service.book.BookService;
import com.adityachandel.booklore.service.kobo.KoboReadingStateService;
import com.adityachandel.booklore.service.monitoring.MonitoringRegistrationService;
import com.adityachandel.booklore.service.user.UserProgressService;
import com.adityachandel.booklore.util.FileService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BookServiceViewerSettingsOptimizationTest {

    @Mock
    private BookRepository bookRepository;

    @Mock
    private PdfViewerPreferencesRepository pdfViewerPreferencesRepository;

    @Mock
    private EpubViewerPreferencesRepository epubViewerPreferencesRepository;

    @Mock
    private CbxViewerPreferencesRepository cbxViewerPreferencesRepository;

    @Mock
    private NewPdfViewerPreferencesRepository newPdfViewerPreferencesRepository;

    @Mock
    private ShelfRepository shelfRepository;

    @Mock
    private FileService fileService;

    @Mock
    private BookMapper bookMapper;

    @Mock
    private UserRepository userRepository;

    @Mock
    private UserBookProgressRepository userBookProgressRepository;

    @Mock
    private AuthenticationService authenticationService;

    @Mock
    private com.adityachandel.booklore.service.book.BookQueryService bookQueryService;

    @Mock
    private UserProgressService userProgressService;

    @Mock
    private com.adityachandel.booklore.service.book.BookDownloadService bookDownloadService;

    @Mock
    private MonitoringRegistrationService monitoringRegistrationService;

    @Mock
    private KoboReadingStateService koboReadingStateService;

    private BookService bookService;

    @BeforeEach
    void setUp() {
        bookService = new BookService(
                bookRepository,
                pdfViewerPreferencesRepository,
                epubViewerPreferencesRepository,
                cbxViewerPreferencesRepository,
                newPdfViewerPreferencesRepository,
                shelfRepository,
                fileService,
                bookMapper,
                userRepository,
                userBookProgressRepository,
                authenticationService,
                bookQueryService,
                userProgressService,
                bookDownloadService,
                monitoringRegistrationService,
                koboReadingStateService
        );
    }

    @Test
    void getBookViewerSettingsForBooks_shouldBatchLoadEpubPreferences() {
        Long userId = 1L;
        BookEntity epubBook = new BookEntity();
        epubBook.setId(1L);
        epubBook.setBookType(BookFileType.EPUB);

        EpubViewerPreferencesEntity epubPref = new EpubViewerPreferencesEntity();
        epubPref.setBookId(1L);
        epubPref.setUserId(userId);
        epubPref.setFont("Arial");
        epubPref.setFontSize(12);

        when(bookRepository.findAllById(Set.of(1L))).thenReturn(List.of(epubBook));
        when(epubViewerPreferencesRepository.findByBookIdInAndUserId(List.of(1L), userId))
                .thenReturn(List.of(epubPref));

        Map<Long, BookViewerSettings> result = bookService.getBookViewerSettingsForBooks(Set.of(1L), userId);

        assertNotNull(result);
        assertTrue(result.containsKey(1L));
        BookViewerSettings settings = result.get(1L);
        assertNotNull(settings.getEpubSettings());
        assertEquals("Arial", settings.getEpubSettings().getFont());
        assertEquals(12, settings.getEpubSettings().getFontSize());

        verify(epubViewerPreferencesRepository, times(1)).findByBookIdInAndUserId(List.of(1L), userId);
    }

    @Test
    void getBookViewerSettingsForBooks_shouldBatchLoadPdfPreferences() {
        Long userId = 1L;
        BookEntity pdfBook = new BookEntity();
        pdfBook.setId(2L);
        pdfBook.setBookType(BookFileType.PDF);

        PdfViewerPreferencesEntity pdfPref = new PdfViewerPreferencesEntity();
        pdfPref.setBookId(2L);
        pdfPref.setUserId(userId);
        pdfPref.setZoom(1.5f);
        pdfPref.setSpread("auto");

        NewPdfViewerPreferencesEntity newPdfPref = new NewPdfViewerPreferencesEntity();
        newPdfPref.setBookId(2L);
        newPdfPref.setUserId(userId);
        newPdfPref.setPageViewMode("single");
        newPdfPref.setPageSpread("auto");

        when(bookRepository.findAllById(Set.of(2L))).thenReturn(List.of(pdfBook));
        when(pdfViewerPreferencesRepository.findByBookIdInAndUserId(List.of(2L), userId))
                .thenReturn(List.of(pdfPref));
        when(newPdfViewerPreferencesRepository.findByBookIdInAndUserId(List.of(2L), userId))
                .thenReturn(List.of(newPdfPref));

        Map<Long, BookViewerSettings> result = bookService.getBookViewerSettingsForBooks(Set.of(2L), userId);

        assertNotNull(result);
        assertTrue(result.containsKey(2L));
        BookViewerSettings settings = result.get(2L);
        assertNotNull(settings.getPdfSettings());
        assertEquals(1.5f, settings.getPdfSettings().getZoom());
        assertNotNull(settings.getNewPdfSettings());
        assertEquals("single", settings.getNewPdfSettings().getPageViewMode());

        verify(pdfViewerPreferencesRepository, times(1)).findByBookIdInAndUserId(List.of(2L), userId);
        verify(newPdfViewerPreferencesRepository, times(1)).findByBookIdInAndUserId(List.of(2L), userId);
    }

    @Test
    void getBookViewerSettingsForBooks_shouldBatchLoadMultipleBookTypes() {
        Long userId = 1L;
        
        BookEntity epubBook = new BookEntity();
        epubBook.setId(1L);
        epubBook.setBookType(BookFileType.EPUB);
        
        BookEntity pdfBook = new BookEntity();
        pdfBook.setId(2L);
        pdfBook.setBookType(BookFileType.PDF);
        
        BookEntity cbxBook = new BookEntity();
        cbxBook.setId(3L);
        cbxBook.setBookType(BookFileType.CBX);

        EpubViewerPreferencesEntity epubPref = new EpubViewerPreferencesEntity();
        epubPref.setBookId(1L);
        epubPref.setUserId(userId);
        epubPref.setFont("Times New Roman");

        PdfViewerPreferencesEntity pdfPref = new PdfViewerPreferencesEntity();
        pdfPref.setBookId(2L);
        pdfPref.setUserId(userId);
        pdfPref.setZoom(2.0f);

        CbxViewerPreferencesEntity cbxPref = new CbxViewerPreferencesEntity();
        cbxPref.setBookId(3L);
        cbxPref.setUserId(userId);
        cbxPref.setPageViewMode("fit_width");

        Set<Long> bookIds = Set.of(1L, 2L, 3L);
        when(bookRepository.findAllById(bookIds)).thenReturn(List.of(epubBook, pdfBook, cbxBook));
        when(epubViewerPreferencesRepository.findByBookIdInAndUserId(List.of(1L), userId))
                .thenReturn(List.of(epubPref));
        when(pdfViewerPreferencesRepository.findByBookIdInAndUserId(List.of(2L), userId))
                .thenReturn(List.of(pdfPref));
        when(newPdfViewerPreferencesRepository.findByBookIdInAndUserId(List.of(2L), userId))
                .thenReturn(new ArrayList<>()); // No new PDF prefs for this book
        when(cbxViewerPreferencesRepository.findByBookIdInAndUserId(List.of(3L), userId))
                .thenReturn(List.of(cbxPref));

        Map<Long, BookViewerSettings> result = bookService.getBookViewerSettingsForBooks(bookIds, userId);

        assertNotNull(result);
        assertEquals(3, result.size());
        
        BookViewerSettings epubSettings = result.get(1L);
        assertNotNull(epubSettings.getEpubSettings());
        assertEquals("Times New Roman", epubSettings.getEpubSettings().getFont());
        
        BookViewerSettings pdfSettings = result.get(2L);
        assertNotNull(pdfSettings.getPdfSettings());
        assertEquals(2.0f, pdfSettings.getPdfSettings().getZoom());
        
        BookViewerSettings cbxSettings = result.get(3L);
        assertNotNull(cbxSettings.getCbxSettings());
        assertEquals("fit_width", cbxSettings.getCbxSettings().getPageViewMode());

        verify(epubViewerPreferencesRepository, times(1)).findByBookIdInAndUserId(any(), eq(userId));
        verify(pdfViewerPreferencesRepository, times(1)).findByBookIdInAndUserId(any(), eq(userId));
        verify(newPdfViewerPreferencesRepository, times(1)).findByBookIdInAndUserId(any(), eq(userId));
        verify(cbxViewerPreferencesRepository, times(1)).findByBookIdInAndUserId(any(), eq(userId));
    }

    @Test
    void getBookViewerSettingsForBooks_shouldHandleBooksWithoutPreferences() {
        Long userId = 1L;
        BookEntity epubBook = new BookEntity();
        epubBook.setId(1L);
        epubBook.setBookType(BookFileType.EPUB);

        when(bookRepository.findAllById(Set.of(1L))).thenReturn(List.of(epubBook));
        when(epubViewerPreferencesRepository.findByBookIdInAndUserId(List.of(1L), userId))
                .thenReturn(new ArrayList<>());

        Map<Long, BookViewerSettings> result = bookService.getBookViewerSettingsForBooks(Set.of(1L), userId);

        assertNotNull(result);
        assertTrue(result.containsKey(1L));
        BookViewerSettings settings = result.get(1L);
        assertNull(settings.getEpubSettings()); // Should be null when no preferences exist
    }

    @Test
    void getBookViewerSetting_shouldUseDirectQueryForSingleBook() {
        Long userId = 1L;
        BookEntity epubBook = new BookEntity();
        epubBook.setId(1L);
        epubBook.setBookType(BookFileType.EPUB);

        EpubViewerPreferencesEntity epubPref = new EpubViewerPreferencesEntity();
        epubPref.setBookId(1L);
        epubPref.setUserId(userId);
        epubPref.setFont("Arial");

        when(authenticationService.getAuthenticatedUser()).thenReturn(new BookLoreUser() {{
            setId(userId);
        }});
        when(bookRepository.findById(1L)).thenReturn(Optional.of(epubBook));
        when(epubViewerPreferencesRepository.findByBookIdAndUserId(1L, userId))
                .thenReturn(Optional.of(epubPref));

        BookViewerSettings result = bookService.getBookViewerSetting(1L);

        assertNotNull(result);
        assertNotNull(result.getEpubSettings());
        assertEquals("Arial", result.getEpubSettings().getFont());

        verify(epubViewerPreferencesRepository, never()).findByBookIdInAndUserId(any(), eq(userId));
        verify(epubViewerPreferencesRepository, times(1)).findByBookIdAndUserId(1L, userId);
    }
}