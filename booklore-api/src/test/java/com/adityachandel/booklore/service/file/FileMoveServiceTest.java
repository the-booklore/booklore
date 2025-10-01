package com.adityachandel.booklore.service.file;

import com.adityachandel.booklore.mapper.BookMapper;
import com.adityachandel.booklore.model.dto.Book;
import com.adityachandel.booklore.model.dto.request.FileMoveRequest;
import com.adityachandel.booklore.model.entity.BookEntity;
import com.adityachandel.booklore.model.entity.BookMetadataEntity;
import com.adityachandel.booklore.model.websocket.Topic;
import com.adityachandel.booklore.repository.BookRepository;
import com.adityachandel.booklore.service.BookQueryService;
import com.adityachandel.booklore.service.NotificationService;
import com.adityachandel.booklore.util.PathPatternResolver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class FileMoveServiceTest {

    @Mock
    private BookQueryService bookQueryService;
    @Mock
    private BookRepository bookRepository;
    @Mock
    private BookMapper bookMapper;
    @Mock
    private NotificationService notificationService;
    @Mock
    private UnifiedFileMoveService unifiedFileMoveService;

    @InjectMocks
    private FileMoveService fileMoveService;

    private BookEntity bookEntity1;
    private BookEntity bookEntity2;
    private Book book1;
    private Book book2;

    @BeforeEach
    void setUp() {
        // Setup BookMetadataEntity for book1
        BookMetadataEntity metadata1 = new BookMetadataEntity();
        metadata1.setTitle("Test Book 1");

        bookEntity1 = new BookEntity();
        bookEntity1.setId(1L);
        bookEntity1.setMetadata(metadata1);
        metadata1.setBook(bookEntity1);

        // Setup BookMetadataEntity for book2
        BookMetadataEntity metadata2 = new BookMetadataEntity();
        metadata2.setTitle("Test Book 2");

        bookEntity2 = new BookEntity();
        bookEntity2.setId(2L);
        bookEntity2.setMetadata(metadata2);
        metadata2.setBook(bookEntity2);

        book1 = mock(Book.class);
        book2 = mock(Book.class);
    }

    @Test
    void moveFiles_WhenSingleBatch_ShouldProcessAllBooks() {
        // Given
        Set<Long> bookIds = Set.of(1L, 2L);
        FileMoveRequest request = new FileMoveRequest();
        request.setBookIds(bookIds);
        request.setMoves(List.of());

        List<BookEntity> batchBooks = List.of(bookEntity1, bookEntity2);
        when(bookQueryService.findWithMetadataByIdsWithPagination(bookIds, 0, 100))
                .thenReturn(batchBooks);

        when(bookRepository.save(bookEntity1)).thenReturn(bookEntity1);
        when(bookRepository.save(bookEntity2)).thenReturn(bookEntity2);
        when(bookMapper.toBook(bookEntity1)).thenReturn(book1);
        when(bookMapper.toBook(bookEntity2)).thenReturn(book2);

        doAnswer(invocation -> {
            UnifiedFileMoveService.BatchMoveCallback callback = invocation.getArgument(2);
            callback.onBookMoved(bookEntity1);
            callback.onBookMoved(bookEntity2);
            return null;
        }).when(unifiedFileMoveService).moveBatchBookFiles(eq(batchBooks), eq(Map.of()), any());

        // When
        fileMoveService.moveFiles(request);

        // Then
        verify(bookQueryService).findWithMetadataByIdsWithPagination(bookIds, 0, 100);
        verify(unifiedFileMoveService).moveBatchBookFiles(eq(batchBooks), eq(Map.of()), any());
        verify(bookRepository).save(bookEntity1);
        verify(bookRepository).save(bookEntity2);
        verify(notificationService).sendMessage(eq(Topic.BOOK_METADATA_BATCH_UPDATE), eq(List.of(book1, book2)));
    }

    @Test
    void moveFiles_WhenMultipleBatches_ShouldProcessAllBatches() {
        // Given - create >100 ids so service iterates multiple batches
        Set<Long> bookIds = IntStream.rangeClosed(1, 150)
                .mapToObj(i -> (long) i)
                .collect(Collectors.toSet());
        FileMoveRequest request = new FileMoveRequest();
        request.setBookIds(bookIds);
        request.setMoves(List.of());

        // Create subset for first batch (first 100 items)
        Set<Long> firstBatchIds = IntStream.rangeClosed(1, 100)
                .mapToObj(i -> (long) i)
                .collect(Collectors.toSet());

        // Create subset for second batch (remaining 50 items)
        Set<Long> secondBatchIds = IntStream.rangeClosed(101, 150)
                .mapToObj(i -> (long) i)
                .collect(Collectors.toSet());

        // First batch
        when(bookQueryService.findWithMetadataByIdsWithPagination(firstBatchIds, 0, 100))
                .thenReturn(List.of(bookEntity1));
        // Second batch
        when(bookQueryService.findWithMetadataByIdsWithPagination(secondBatchIds, 100, 100))
                .thenReturn(List.of(bookEntity2));

        when(book1.getId()).thenReturn(1L);
        when(book2.getId()).thenReturn(2L);
        when(bookRepository.save(bookEntity1)).thenReturn(bookEntity1);
        when(bookRepository.save(bookEntity2)).thenReturn(bookEntity2);
        when(bookMapper.toBook(bookEntity1)).thenReturn(book1);
        when(bookMapper.toBook(bookEntity2)).thenReturn(book2);

        doAnswer(invocation -> {
            UnifiedFileMoveService.BatchMoveCallback callback = invocation.getArgument(2);
            List<BookEntity> books = invocation.getArgument(0);
            for (BookEntity book : books) {
                callback.onBookMoved(book);
            }
            return null;
        }).when(unifiedFileMoveService).moveBatchBookFiles(any(), eq(Map.of()), any());

        // When
        fileMoveService.moveFiles(request);

        // Then
        verify(bookQueryService).findWithMetadataByIdsWithPagination(firstBatchIds, 0, 100);
        verify(bookQueryService).findWithMetadataByIdsWithPagination(secondBatchIds, 100, 100);
        verify(unifiedFileMoveService, times(2)).moveBatchBookFiles(any(), eq(Map.of()), any());
        verify(bookRepository).save(bookEntity1);
        verify(bookRepository).save(bookEntity2);
        verify(notificationService).sendMessage(eq(Topic.BOOK_METADATA_BATCH_UPDATE), eq(List.of(book1, book2)));
    }

    @Test
    void moveFiles_WhenNoBooksFound_ShouldNotProcessAnything() {
        // Given
        Set<Long> bookIds = Set.of(1L, 2L);
        FileMoveRequest request = new FileMoveRequest();
        request.setBookIds(bookIds);
        request.setMoves(List.of());

        when(bookQueryService.findWithMetadataByIdsWithPagination(bookIds, 0, 100))
                .thenReturn(List.of());

        // When
        fileMoveService.moveFiles(request);

        // Then
        verify(bookQueryService).findWithMetadataByIdsWithPagination(bookIds, 0, 100);
        verify(unifiedFileMoveService, never()).moveBatchBookFiles(any(), any(), any());
        verify(bookRepository, never()).save(any());
        verify(notificationService, never()).sendMessage(any(), any());
    }

    @Test
    void moveFiles_WhenMoveFailsForSomeBooks_ShouldThrowException() {
        // Given
        Set<Long> bookIds = Set.of(1L, 2L);
        FileMoveRequest request = new FileMoveRequest();
        request.setBookIds(bookIds);
        request.setMoves(List.of());

        List<BookEntity> batchBooks = List.of(bookEntity1, bookEntity2);
        when(bookQueryService.findWithMetadataByIdsWithPagination(bookIds, 0, 100))
                .thenReturn(batchBooks);

        when(bookRepository.save(bookEntity1)).thenReturn(bookEntity1);
        when(bookMapper.toBook(bookEntity1)).thenReturn(book1);

        RuntimeException moveException = new RuntimeException("File move failed");
        doAnswer(invocation -> {
            UnifiedFileMoveService.BatchMoveCallback callback = invocation.getArgument(2);
            callback.onBookMoved(bookEntity1);
            callback.onBookMoveFailed(bookEntity2, moveException);
            return null;
        }).when(unifiedFileMoveService).moveBatchBookFiles(eq(batchBooks), eq(Map.of()), any());

        // When & Then
        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            fileMoveService.moveFiles(request);
        });

        assertEquals("File move failed for book id 2", exception.getMessage());
        assertEquals(moveException, exception.getCause());
        verify(bookRepository).save(bookEntity1);
        verify(bookRepository, never()).save(bookEntity2);
    }

    @Test
    void moveFiles_WhenEmptyBookIds_ShouldCompleteWithoutProcessing() {
        // Given
        Set<Long> bookIds = Set.of();
        FileMoveRequest request = new FileMoveRequest();
        request.setBookIds(bookIds);
        request.setMoves(List.of());

        // When
        fileMoveService.moveFiles(request);

        // Then: service should not call pagination when bookIds is empty
        verify(bookQueryService, never()).findWithMetadataByIdsWithPagination(anySet(), anyInt(), anyInt());
        verify(unifiedFileMoveService, never()).moveBatchBookFiles(any(), any(), any());
        verify(notificationService, never()).sendMessage(any(), any());
    }

    @Test
    void moveFiles_WhenPartialBatch_ShouldProcessCorrectly() {
        // Given
        Set<Long> bookIds = Set.of(1L);
        FileMoveRequest request = new FileMoveRequest();
        request.setBookIds(bookIds);
        request.setMoves(List.of());

        when(bookQueryService.findWithMetadataByIdsWithPagination(bookIds, 0, 100))
                .thenReturn(List.of(bookEntity1));

        when(book1.getId()).thenReturn(1L);
        when(bookRepository.save(bookEntity1)).thenReturn(bookEntity1);
        when(bookMapper.toBook(bookEntity1)).thenReturn(book1);

        doAnswer(invocation -> {
            UnifiedFileMoveService.BatchMoveCallback callback = invocation.getArgument(2);
            callback.onBookMoved(bookEntity1);
            return null;
        }).when(unifiedFileMoveService).moveBatchBookFiles(eq(List.of(bookEntity1)), eq(Map.of()), any());

        // When
        fileMoveService.moveFiles(request);

        // Then
        verify(bookQueryService).findWithMetadataByIdsWithPagination(bookIds, 0, 100);
        verify(unifiedFileMoveService).moveBatchBookFiles(eq(List.of(bookEntity1)), eq(Map.of()), any());
        verify(bookRepository).save(bookEntity1);
        verify(notificationService).sendMessage(eq(Topic.BOOK_METADATA_BATCH_UPDATE), eq(List.of(book1)));
    }

    @Test
    void generatePathFromPattern_ShouldDelegateToPathPatternResolver() {
        // Given
        String pattern = "{author}/{title}";
        String expectedPath = "John Doe/Test Book";

        try (MockedStatic<PathPatternResolver> mockedResolver = mockStatic(PathPatternResolver.class)) {
            mockedResolver.when(() -> PathPatternResolver.resolvePattern(bookEntity1, pattern))
                    .thenReturn(expectedPath);

            // When
            String result = fileMoveService.generatePathFromPattern(bookEntity1, pattern);

            // Then
            assertEquals(expectedPath, result);
            mockedResolver.verify(() -> PathPatternResolver.resolvePattern(bookEntity1, pattern));
        }
    }

    @Test
    void generatePathFromPattern_WithDifferentPatterns_ShouldReturnCorrectPaths() {
        // Given
        String pattern1 = "{title}";
        String pattern2 = "{author}/{series}/{title}";
        String expectedPath1 = "Test Book 1";
        String expectedPath2 = "Author/Series/Test Book 1";

        try (MockedStatic<PathPatternResolver> mockedResolver = mockStatic(PathPatternResolver.class)) {
            mockedResolver.when(() -> PathPatternResolver.resolvePattern(bookEntity1, pattern1))
                    .thenReturn(expectedPath1);
            mockedResolver.when(() -> PathPatternResolver.resolvePattern(bookEntity1, pattern2))
                    .thenReturn(expectedPath2);

            // When
            String result1 = fileMoveService.generatePathFromPattern(bookEntity1, pattern1);
            String result2 = fileMoveService.generatePathFromPattern(bookEntity1, pattern2);

            // Then
            assertEquals(expectedPath1, result1);
            assertEquals(expectedPath2, result2);
            mockedResolver.verify(() -> PathPatternResolver.resolvePattern(bookEntity1, pattern1));
            mockedResolver.verify(() -> PathPatternResolver.resolvePattern(bookEntity1, pattern2));
        }
    }

    @Test
    void moveFiles_ShouldSendNotificationWithAllUpdatedBooks() {
        // Given
        Set<Long> bookIds = Set.of(1L, 2L);
        FileMoveRequest request = new FileMoveRequest();
        request.setBookIds(bookIds);
        request.setMoves(List.of());

        when(bookQueryService.findWithMetadataByIdsWithPagination(bookIds, 0, 100))
                .thenReturn(List.of(bookEntity1, bookEntity2));

        when(bookRepository.save(bookEntity1)).thenReturn(bookEntity1);
        when(bookRepository.save(bookEntity2)).thenReturn(bookEntity2);
        when(bookMapper.toBook(bookEntity1)).thenReturn(book1);
        when(bookMapper.toBook(bookEntity2)).thenReturn(book2);

        doAnswer(invocation -> {
            UnifiedFileMoveService.BatchMoveCallback callback = invocation.getArgument(2);
            callback.onBookMoved(bookEntity1);
            callback.onBookMoved(bookEntity2);
            return null;
        }).when(unifiedFileMoveService).moveBatchBookFiles(any(), eq(Map.of()), any());

        // When
        fileMoveService.moveFiles(request);

        // Then
        ArgumentCaptor<List<Book>> booksCaptor = ArgumentCaptor.forClass(List.class);
        verify(notificationService).sendMessage(eq(Topic.BOOK_METADATA_BATCH_UPDATE), booksCaptor.capture());

        List<Book> sentBooks = booksCaptor.getValue();
        assertEquals(2, sentBooks.size());
        assertTrue(sentBooks.contains(book1));
        assertTrue(sentBooks.contains(book2));
    }

    @Test
    void moveFiles_WhenNoBooksMoved_ShouldNotSendNotification() {
        // Given
        Set<Long> bookIds = Set.of(1L, 2L);
        FileMoveRequest request = new FileMoveRequest();
        request.setBookIds(bookIds);
        request.setMoves(List.of());

        when(bookQueryService.findWithMetadataByIdsWithPagination(bookIds, 0, 100))
                .thenReturn(List.of(bookEntity1, bookEntity2));

        RuntimeException moveException = new RuntimeException("All moves failed");
        doAnswer(invocation -> {
            UnifiedFileMoveService.BatchMoveCallback callback = invocation.getArgument(2);
            callback.onBookMoveFailed(bookEntity1, moveException);
            return null;
        }).when(unifiedFileMoveService).moveBatchBookFiles(any(), eq(Map.of()), any());

        // When & Then
        assertThrows(RuntimeException.class, () -> {
            fileMoveService.moveFiles(request);
        });

        verify(notificationService, never()).sendMessage(any(), any());
    }
}
