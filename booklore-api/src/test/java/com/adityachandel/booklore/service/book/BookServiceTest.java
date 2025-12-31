package com.adityachandel.booklore.service.book;

import com.adityachandel.booklore.config.security.service.AuthenticationService;
import com.adityachandel.booklore.exception.APIException;
import com.adityachandel.booklore.mapper.BookMapper;
import com.adityachandel.booklore.model.dto.*;
import com.adityachandel.booklore.model.dto.request.ReadProgressRequest;
import com.adityachandel.booklore.model.dto.response.BookDeletionResponse;
import com.adityachandel.booklore.model.dto.response.BookStatusUpdateResponse;
import com.adityachandel.booklore.model.entity.*;
import com.adityachandel.booklore.model.enums.BookFileType;
import com.adityachandel.booklore.repository.*;
import com.adityachandel.booklore.service.monitoring.MonitoringRegistrationService;
import com.adityachandel.booklore.service.user.UserProgressService;
import com.adityachandel.booklore.util.FileService;
import com.adityachandel.booklore.util.FileUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(org.mockito.junit.jupiter.MockitoExtension.class)
class BookServiceTest {

    @Mock private BookRepository bookRepository;
    @Mock private PdfViewerPreferencesRepository pdfViewerPreferencesRepository;
    @Mock private EpubViewerPreferencesRepository epubViewerPreferencesRepository;
    @Mock private CbxViewerPreferencesRepository cbxViewerPreferencesRepository;
    @Mock private NewPdfViewerPreferencesRepository newPdfViewerPreferencesRepository;
    @Mock private FileService fileService;
    @Mock private BookMapper bookMapper;
    @Mock private UserBookProgressRepository userBookProgressRepository;
    @Mock private AuthenticationService authenticationService;
    @Mock private BookQueryService bookQueryService;
    @Mock private UserProgressService userProgressService;
    @Mock private BookDownloadService bookDownloadService;
    @Mock private MonitoringRegistrationService monitoringRegistrationService;
    @Mock private BookUpdateService bookUpdateService;

    @InjectMocks
    private BookService bookService;

    private BookLoreUser testUser;

    @BeforeEach
    void setUp() {
        BookLoreUser.UserPermissions perms = new BookLoreUser.UserPermissions();
        perms.setAdmin(true);
        testUser = BookLoreUser.builder()
                .id(1L)
                .permissions(perms)
                .assignedLibraries(List.of())
                .build();
    }

    @Test
    void getBookDTOs_adminUser_returnsBooksWithProgress() {
        Book book = Book.builder().id(1L).bookType(BookFileType.PDF).shelves(Set.of()).build();
        when(bookQueryService.getAllBooks(anyBoolean())).thenReturn(List.of(book));
        when(userProgressService.fetchUserProgress(anyLong(), anySet())).thenReturn(Map.of(1L, new UserBookProgressEntity()));
        when(authenticationService.getAuthenticatedUser()).thenReturn(testUser);

        List<Book> result = bookService.getBookDTOs(true);

        assertEquals(1, result.size());
        verify(bookQueryService).getAllBooks(true);
    }

    @Test
    void getBooksByIds_returnsMappedBooksWithProgress() {
        BookEntity entity = new BookEntity();
        entity.setId(2L);
        entity.setBookType(BookFileType.EPUB);
        LibraryPathEntity libPath = new LibraryPathEntity();
        libPath.setPath("/tmp/library");
        LibraryEntity library = new LibraryEntity();
        library.setLibraryPaths(List.of(libPath));
        entity.setLibrary(library);
        when(bookQueryService.findAllWithMetadataByIds(anySet())).thenReturn(List.of(entity));
        when(userProgressService.fetchUserProgress(anyLong(), anySet())).thenReturn(Map.of(2L, new UserBookProgressEntity()));
        Book mappedBook = Book.builder().id(2L).bookType(BookFileType.EPUB).metadata(BookMetadata.builder().build()).build();
        when(bookMapper.toBook(entity)).thenReturn(mappedBook);
        when(authenticationService.getAuthenticatedUser()).thenReturn(testUser);

        try (MockedStatic<FileUtils> fileUtilsMock = mockStatic(FileUtils.class)) {
            fileUtilsMock.when(() -> FileUtils.getBookFullPath(entity)).thenReturn("/tmp/library/book.epub");
            List<Book> result = bookService.getBooksByIds(Set.of(2L), false);

            assertEquals(1, result.size());
            assertEquals(2L, result.get(0).getId());
        }
    }

    @Test
    void getBook_existingBook_returnsBookWithProgress() {
        BookEntity entity = new BookEntity();
        entity.setId(3L);
        entity.setBookType(BookFileType.PDF);
        LibraryPathEntity libPath = new LibraryPathEntity();
        libPath.setPath("/tmp/library");
        LibraryEntity library = new LibraryEntity();
        library.setLibraryPaths(List.of(libPath));
        entity.setLibrary(library);
        when(bookRepository.findById(3L)).thenReturn(Optional.of(entity));
        when(userBookProgressRepository.findByUserIdAndBookId(anyLong(), eq(3L))).thenReturn(Optional.of(new UserBookProgressEntity()));
        Book mappedBook = Book.builder().id(3L).bookType(BookFileType.PDF).metadata(BookMetadata.builder().build()).shelves(Set.of()).build();
        when(bookMapper.toBook(entity)).thenReturn(mappedBook);
        when(authenticationService.getAuthenticatedUser()).thenReturn(testUser);

        try (MockedStatic<FileUtils> fileUtilsMock = mockStatic(FileUtils.class)) {
            fileUtilsMock.when(() -> FileUtils.getBookFullPath(entity)).thenReturn("/tmp/library/book.pdf");
            Book result = bookService.getBook(3L, true);
            assertEquals(3L, result.getId());
            verify(bookRepository).findById(3L);
        }
    }

    @Test
    void getBook_notFound_throwsException() {
        when(bookRepository.findById(99L)).thenReturn(Optional.empty());
        when(authenticationService.getAuthenticatedUser()).thenReturn(testUser);
        assertThrows(APIException.class, () -> bookService.getBook(99L, true));
    }

    @Test
    void getBookViewerSetting_epub_returnsEpubSettings() {
        BookEntity entity = new BookEntity();
        entity.setId(4L);
        entity.setBookType(BookFileType.EPUB);
        when(bookRepository.findById(4L)).thenReturn(Optional.of(entity));
        EpubViewerPreferencesEntity epubPref = new EpubViewerPreferencesEntity();
        epubPref.setFont("Arial");
        when(epubViewerPreferencesRepository.findByBookIdAndUserId(4L, testUser.getId())).thenReturn(Optional.of(epubPref));
        when(authenticationService.getAuthenticatedUser()).thenReturn(testUser);

        BookViewerSettings settings = bookService.getBookViewerSetting(4L);

        assertNotNull(settings.getEpubSettings());
        assertEquals("Arial", settings.getEpubSettings().getFont());
    }

    @Test
    void getBookViewerSetting_unsupportedType_throwsException() {
        BookEntity entity = new BookEntity();
        entity.setId(5L);
        entity.setBookType(null);
        when(bookRepository.findById(5L)).thenReturn(Optional.of(entity));
        when(authenticationService.getAuthenticatedUser()).thenReturn(testUser);
        assertThrows(APIException.class, () -> bookService.getBookViewerSetting(5L));
    }

    @Test
    void updateBookViewerSetting_delegatesToUpdateService() {
        BookViewerSettings settings = BookViewerSettings.builder().build();
        bookService.updateBookViewerSetting(1L, settings);
        verify(bookUpdateService).updateBookViewerSetting(1L, settings);
    }

    @Test
    void updateReadProgress_delegatesToUpdateService() {
        ReadProgressRequest req = new ReadProgressRequest();
        bookService.updateReadProgress(req);
        verify(bookUpdateService).updateReadProgress(req);
    }

    @Test
    void updateReadStatus_delegatesToUpdateService() {
        List<Long> ids = List.of(1L, 2L);
        List<BookStatusUpdateResponse> responses = List.of(new BookStatusUpdateResponse());
        when(bookUpdateService.updateReadStatus(ids, "READ")).thenReturn(responses);

        List<BookStatusUpdateResponse> result = bookService.updateReadStatus(ids, "READ");

        assertEquals(responses, result);
    }

    @Test
    void assignShelvesToBooks_delegatesToUpdateService() {
        Set<Long> bookIds = Set.of(1L);
        Set<Long> assign = Set.of(2L);
        Set<Long> unassign = Set.of(3L);
        List<Book> books = List.of(Book.builder().id(1L).build());
        when(bookUpdateService.assignShelvesToBooks(bookIds, assign, unassign)).thenReturn(books);

        List<Book> result = bookService.assignShelvesToBooks(bookIds, assign, unassign);

        assertEquals(books, result);
    }

    @Test
    void getBookThumbnail_fileExists_returnsUrlResource() throws Exception {
        when(fileService.getThumbnailFile(1L)).thenReturn("/tmp/cover.jpg");
        Path path = Paths.get("/tmp/cover.jpg");
        Files.createFile(path);
        try {
            Resource res = bookService.getBookThumbnail(1L);
            assertTrue(res instanceof UrlResource);
        } finally {
            Files.deleteIfExists(path);
        }
    }

    @Test
    void getBookThumbnail_fileMissing_returnsDefault() throws Exception {
        when(fileService.getThumbnailFile(1L)).thenReturn("/tmp/nonexistent.jpg");
        Resource res = bookService.getBookThumbnail(1L);
        assertTrue(res instanceof UrlResource);
    }

    @Test
    void getBookThumbnail_malformedPath_throwsRuntimeException() {
        when(fileService.getThumbnailFile(123L)).thenReturn("\0illegal:path");
        assertThrows(RuntimeException.class, () -> bookService.getBookThumbnail(123L));
    }

    @Test
    void getBookCover_fileExists_returnsUrlResource() throws Exception {
        when(fileService.getCoverFile(1L)).thenReturn("/tmp/cover2.jpg");
        Path path = Paths.get("/tmp/cover2.jpg");
        Files.createFile(path);
        try {
            Resource res = bookService.getBookCover(1L);
            assertTrue(res instanceof UrlResource);
        } finally {
            Files.deleteIfExists(path);
        }
    }

    @Test
    void getBookCover_fileMissing_returnsClassPathResource() {
        when(fileService.getCoverFile(1L)).thenReturn("/tmp/nonexistent2.jpg");
        Resource res = bookService.getBookCover(1L);
        assertTrue(res instanceof ClassPathResource);
    }

    @Test
    void getBookCover_malformedPath_throwsRuntimeException() {
        when(fileService.getCoverFile(123L)).thenReturn("\0illegal:path");
        assertThrows(RuntimeException.class, () -> bookService.getBookCover(123L));
    }

    @Test
    void getBackgroundImage_returnsResource() {
        Resource mockResource = mock(Resource.class);
        when(fileService.getBackgroundResource(testUser.getId())).thenReturn(mockResource);

        when(authenticationService.getAuthenticatedUser()).thenReturn(testUser);

        Resource res = bookService.getBackgroundImage();

        assertEquals(mockResource, res);
    }

    @Test
    void downloadBook_delegatesToDownloadService() {
        ResponseEntity<Resource> response = ResponseEntity.ok(mock(Resource.class));
        when(bookDownloadService.downloadBook(1L)).thenReturn(response);

        ResponseEntity<Resource> result = bookService.downloadBook(1L);

        assertEquals(response, result);
    }

    @Test
    void getBookContent_returnsByteArrayResource() throws Exception {
        BookEntity entity = new BookEntity();
        entity.setId(10L);
        when(bookRepository.findById(10L)).thenReturn(Optional.of(entity));
        Path path = Paths.get("/tmp/bookcontent.txt");
        Files.write(path, "hello".getBytes());
        try (MockedStatic<FileUtils> fileUtilsMock = mockStatic(FileUtils.class)) {
            fileUtilsMock.when(() -> FileUtils.getBookFullPath(entity)).thenReturn(path.toString());
            ResponseEntity<ByteArrayResource> response = bookService.getBookContent(10L);
            assertEquals(HttpStatus.OK, response.getStatusCode());
            assertArrayEquals("hello".getBytes(), response.getBody().getByteArray());
        } finally {
            Files.deleteIfExists(path);
        }
    }

    @Test
    void getBookContent_bookNotFound_throwsException() {
        when(bookRepository.findById(404L)).thenReturn(Optional.empty());
        assertThrows(APIException.class, () -> bookService.getBookContent(404L));
    }

    @Test
    void getBookContent_fileIoError_throwsIOException() throws Exception {
        BookEntity entity = new BookEntity();
        entity.setId(12L);
        when(bookRepository.findById(12L)).thenReturn(Optional.of(entity));
        try (MockedStatic<FileUtils> fileUtilsMock = mockStatic(FileUtils.class)) {
            fileUtilsMock.when(() -> FileUtils.getBookFullPath(entity)).thenReturn("/tmp/nonexistentfile.txt");
            assertThrows(java.io.FileNotFoundException.class, () -> bookService.getBookContent(12L));
        }
    }

    @Test
    void deleteBooks_deletesFilesAndEntities() throws Exception {
        BookEntity entity = new BookEntity();
        entity.setId(11L);
        LibraryEntity library = new LibraryEntity();
        LibraryPathEntity libPath = new LibraryPathEntity();
        libPath.setPath("/tmp/library");
        library.setLibraryPaths(List.of(libPath));
        entity.setLibrary(library);
        Path filePath = Paths.get("/tmp/bookfile.txt");
        Files.createDirectories(filePath.getParent());
        Files.write(filePath, "abc".getBytes());

        when(bookQueryService.findAllWithMetadataByIds(Set.of(11L))).thenReturn(List.of(entity));
        doNothing().when(bookRepository).deleteAll(anyList());
        BookEntity spyEntity = spy(entity);
        doReturn(filePath).when(spyEntity).getFullFilePath();

        when(bookQueryService.findAllWithMetadataByIds(Set.of(11L))).thenReturn(List.of(spyEntity));

        BookDeletionResponse response = bookService.deleteBooks(Set.of(11L)).getBody();

        assertNotNull(response);
        assertTrue(response.getFailedFileDeletions().isEmpty());
        assertEquals(Set.of(11L), response.getDeleted());
        Files.deleteIfExists(filePath);
    }

    @Test
    void deleteBooks_fileDoesNotExist_deletesEntityOnly() {
        BookEntity entity = new BookEntity();
        entity.setId(13L);
        LibraryEntity library = new LibraryEntity();
        LibraryPathEntity libPath = new LibraryPathEntity();
        libPath.setPath("/tmp/library");
        library.setLibraryPaths(List.of(libPath));
        entity.setLibrary(library);

        BookEntity spyEntity = spy(entity);
        doReturn(Paths.get("/tmp/nonexistentfile.txt")).when(spyEntity).getFullFilePath();

        when(bookQueryService.findAllWithMetadataByIds(Set.of(13L))).thenReturn(List.of(spyEntity));
        doNothing().when(bookRepository).deleteAll(anyList());

        BookDeletionResponse response = bookService.deleteBooks(Set.of(13L)).getBody();

        assertNotNull(response);
        assertTrue(response.getFailedFileDeletions().isEmpty());
        assertEquals(Set.of(13L), response.getDeleted());
    }

    @Test
    void deleteEmptyParentDirsUpToLibraryFolders_deletesEmptyDirs() throws Exception {
        Path root = Files.createTempDirectory("libroot");
        Path subdir = Files.createDirectory(root.resolve("subdir"));
        Path file = subdir.resolve(".DS_Store");
        Files.createFile(file);

        Set<Path> roots = Set.of(root);
        bookService.deleteEmptyParentDirsUpToLibraryFolders(subdir, roots);

        assertFalse(Files.exists(subdir));
        Files.deleteIfExists(root);
    }

    @Test
    void filterShelvesByUserId_returnsOnlyUserShelves() {
        Shelf shelf1 = Shelf.builder().id(1L).userId(1L).build();
        Shelf shelf2 = Shelf.builder().id(2L).userId(2L).build();
        Set<Shelf> shelves = Set.of(shelf1, shelf2);

        Set<Shelf> result = org.springframework.test.util.ReflectionTestUtils.invokeMethod(
                bookService, "filterShelvesByUserId", shelves, 1L);

        assertEquals(1, result.size());
        assertTrue(result.contains(shelf1));
    }
}