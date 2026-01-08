package com.adityachandel.booklore.service.metadata;

import com.adityachandel.booklore.mapper.BookMapper;
import com.adityachandel.booklore.mapper.BookMetadataMapper;
import com.adityachandel.booklore.mapper.MetadataClearFlagsMapper;
import com.adityachandel.booklore.model.dto.Book;
import com.adityachandel.booklore.model.dto.BookMetadata;
import com.adityachandel.booklore.model.dto.request.BulkMetadataUpdateRequest;
import com.adityachandel.booklore.model.dto.request.FetchMetadataRequest;
import com.adityachandel.booklore.model.dto.request.ToggleAllLockRequest;
import com.adityachandel.booklore.model.entity.BookEntity;
import com.adityachandel.booklore.model.entity.BookMetadataEntity;
import com.adityachandel.booklore.model.enums.Lock;
import com.adityachandel.booklore.model.enums.MetadataProvider;
import com.adityachandel.booklore.repository.BookMetadataRepository;
import com.adityachandel.booklore.repository.BookRepository;
import com.adityachandel.booklore.service.NotificationService;
import com.adityachandel.booklore.service.book.BookQueryService;
import com.adityachandel.booklore.service.metadata.extractor.CbxMetadataExtractor;
import com.adityachandel.booklore.service.metadata.parser.BookParser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BookMetadataServiceTest {

    @Mock private BookRepository bookRepository;
    @Mock private BookMapper bookMapper;
    @Mock private BookMetadataMapper bookMetadataMapper;
    @Mock private BookMetadataUpdater bookMetadataUpdater;
    @Mock private NotificationService notificationService;
    @Mock private BookMetadataRepository bookMetadataRepository;
    @Mock private BookQueryService bookQueryService;
    @Mock private CbxMetadataExtractor cbxMetadataExtractor;
    @Mock private MetadataClearFlagsMapper metadataClearFlagsMapper;
    @Mock private BookCoverService bookCoverService;

    private BookMetadataService bookMetadataService;
    private Map<MetadataProvider, BookParser> parserMap;
    private BookParser googleParser;
    private BookParser goodreadsParser;

    @BeforeEach
    void setUp() {
        googleParser = mock(BookParser.class);
        goodreadsParser = mock(BookParser.class);
        
        parserMap = Map.of(
                MetadataProvider.Google, googleParser,
                MetadataProvider.GoodReads, goodreadsParser
        );

        bookMetadataService = new BookMetadataService(
                bookRepository,
                bookMapper,
                bookMetadataMapper,
                bookMetadataUpdater,
                notificationService,
                bookMetadataRepository,
                bookQueryService,
                parserMap,
                cbxMetadataExtractor,
                metadataClearFlagsMapper,
                bookCoverService
        );
    }

    @Test
    void getProspectiveMetadataListForBookId_shouldHandleSingleProviderFailure() {
        long bookId = 1L;
        setupBook(bookId);
        FetchMetadataRequest request = createFetchRequest(MetadataProvider.Google, MetadataProvider.GoodReads);

        when(googleParser.fetchMetadata(any(), any())).thenThrow(new RuntimeException("Simulated Failure"));
        BookMetadata result = BookMetadata.builder().title("GoodReads Title").build();
        when(goodreadsParser.fetchMetadata(any(), any())).thenReturn(List.of(result));

        List<BookMetadata> results = bookMetadataService.getProspectiveMetadataListForBookId(bookId, request).collectList().block();

        assertEquals(1, results.size());
        assertEquals("GoodReads Title", results.get(0).getTitle());
    }

    @Test
    void getProspectiveMetadataListForBookId_shouldInterleaveResults() {
        long bookId = 1L;
        setupBook(bookId);
        FetchMetadataRequest request = createFetchRequest(MetadataProvider.Google, MetadataProvider.GoodReads);

        BookMetadata g1 = BookMetadata.builder().title("Google 1").build();
        BookMetadata g2 = BookMetadata.builder().title("Google 2").build();
        when(googleParser.fetchMetadata(any(), any())).thenReturn(List.of(g1, g2));

        BookMetadata gr1 = BookMetadata.builder().title("GoodReads 1").build();
        BookMetadata gr2 = BookMetadata.builder().title("GoodReads 2").build();
        when(goodreadsParser.fetchMetadata(any(), any())).thenReturn(List.of(gr1, gr2));

        List<BookMetadata> results = bookMetadataService.getProspectiveMetadataListForBookId(bookId, request).collectList().block();

        assertEquals(4, results.size());
        assertEquals("Google 1", results.get(0).getTitle());
        assertEquals("GoodReads 1", results.get(1).getTitle());
        assertEquals("Google 2", results.get(2).getTitle());
        assertEquals("GoodReads 2", results.get(3).getTitle());
    }

    @Test
    void getProspectiveMetadataListForBookId_shouldHandleSlowProvider() {
        long bookId = 1L;
        setupBook(bookId);
        FetchMetadataRequest request = createFetchRequest(MetadataProvider.Google, MetadataProvider.GoodReads);

        // Simulate slow provider
        when(googleParser.fetchMetadata(any(), any())).thenAnswer(invocation -> {
            Thread.sleep(100); // Small delay to simulate slowness
            return List.of(BookMetadata.builder().title("Slow Result").build());
        });

        BookMetadata fastResult = BookMetadata.builder().title("Fast Result").build();
        when(goodreadsParser.fetchMetadata(any(), any())).thenReturn(List.of(fastResult));

        List<BookMetadata> results = bookMetadataService.getProspectiveMetadataListForBookId(bookId, request).collectList().block();

        assertEquals(2, results.size());
    }

    @Test
    void getProspectiveMetadataListForBookId_shouldReturnEmptyForNoProviders() {
        long bookId = 1L;
        FetchMetadataRequest request = FetchMetadataRequest.builder()
                .providers(List.of())
                .build();

        List<BookMetadata> results = bookMetadataService.getProspectiveMetadataListForBookId(bookId, request).collectList().block();

        assertEquals(0, results.size());
        verify(bookRepository, never()).findById(anyLong());
    }

    @Test
    void getProspectiveMetadataListForBookId_shouldReturnEmptyForNullProviders() {
        long bookId = 1L;
        FetchMetadataRequest request = FetchMetadataRequest.builder()
                .providers(null)
                .build();

        List<BookMetadata> results = bookMetadataService.getProspectiveMetadataListForBookId(bookId, request).collectList().block();

        assertEquals(0, results.size());
        verify(bookRepository, never()).findById(anyLong());
    }

    @Test
    void getProspectiveMetadataListForBookId_shouldReturnEmptyWhenAllProvidersFail() {
        long bookId = 1L;
        setupBook(bookId);
        FetchMetadataRequest request = createFetchRequest(MetadataProvider.Google, MetadataProvider.GoodReads);

        when(googleParser.fetchMetadata(any(), any())).thenThrow(new RuntimeException("Google Failed"));
        when(goodreadsParser.fetchMetadata(any(), any())).thenThrow(new RuntimeException("GoodReads Failed"));

        List<BookMetadata> results = bookMetadataService.getProspectiveMetadataListForBookId(bookId, request).collectList().block();

        assertEquals(0, results.size());
    }

    @Test
    void toggleFieldLocks_shouldInvokeSettersCorrectly() {
        List<Long> bookIds = List.of(1L);
        BookMetadataEntity metadataEntity = spy(new BookMetadataEntity());
        metadataEntity.setBookId(1L);
        
        when(bookMetadataRepository.getMetadataForBookIds(bookIds)).thenReturn(List.of(metadataEntity));

        Map<String, String> fieldActions = Map.of(
                "titleLocked", "LOCK",
                "thumbnailLocked", "UNLOCK" // mapped to coverLocked
        );

        bookMetadataService.toggleFieldLocks(bookIds, fieldActions);

        assertTrue(metadataEntity.getTitleLocked());
        assertFalse(metadataEntity.getCoverLocked());
        verify(bookMetadataRepository).saveAll(any());
    }

    @Test
    void toggleAllLock_shouldApplyToAllFields() {
        Set<Long> bookIds = Set.of(1L);
        BookMetadataEntity metadataEntity = new BookMetadataEntity();
        BookEntity bookEntity = BookEntity.builder().metadata(metadataEntity).build();
        
        when(bookQueryService.findAllWithMetadataByIds(bookIds)).thenReturn(List.of(bookEntity));
        
        ToggleAllLockRequest request = new ToggleAllLockRequest();
        request.setBookIds(bookIds);
        request.setLock(Lock.LOCK);

        bookMetadataService.toggleAllLock(request);

        assertTrue(metadataEntity.areAllFieldsLocked());
        verify(bookRepository).saveAll(any());
    }

    @Test
    void bulkUpdateMetadata_shouldInvokeUpdaterAndNotify() {
        Set<Long> bookIds = Set.of(1L);
        BookEntity bookEntity = new BookEntity();
        when(bookRepository.findAllWithMetadataByIds(any())).thenReturn(List.of(bookEntity));
        when(bookMapper.toBook(any())).thenReturn(Book.builder().build());

        BulkMetadataUpdateRequest request = new BulkMetadataUpdateRequest();
        request.setBookIds(bookIds);
        request.setAuthors(Set.of("Author"));

        bookMetadataService.bulkUpdateMetadata(request, false, false, false);

        verify(bookMetadataUpdater).setBookMetadata(any());
        verify(notificationService).sendMessage(any(), any());
    }

    @Test
    void fetchTopMetadataMap_shouldReturnResults_whenProvidersSucceed() {
        Book book = Book.builder().id(1L).title("Test Book").build();
        FetchMetadataRequest request = createFetchRequest(MetadataProvider.Google, MetadataProvider.GoodReads);

        BookMetadata gMeta = BookMetadata.builder().provider(MetadataProvider.Google).title("Google Title").build();
        BookMetadata grMeta = BookMetadata.builder().provider(MetadataProvider.GoodReads).title("GoodReads Title").build();

        when(googleParser.fetchTopMetadata(any(), any())).thenReturn(gMeta);
        when(goodreadsParser.fetchTopMetadata(any(), any())).thenReturn(grMeta);

        Map<MetadataProvider, BookMetadata> result = bookMetadataService.fetchTopMetadataMap(book, request);

        assertEquals(2, result.size());
        assertEquals("Google Title", result.get(MetadataProvider.Google).getTitle());
        assertEquals("GoodReads Title", result.get(MetadataProvider.GoodReads).getTitle());
    }

    @Test
    void fetchTopMetadataMap_shouldHandlePartialFailure() {
        Book book = Book.builder().id(1L).title("Test Book").build();
        FetchMetadataRequest request = createFetchRequest(MetadataProvider.Google, MetadataProvider.GoodReads);

        when(googleParser.fetchTopMetadata(any(), any())).thenThrow(new RuntimeException("Google Failed"));
        BookMetadata grMeta = BookMetadata.builder().provider(MetadataProvider.GoodReads).title("GoodReads Title").build();
        when(goodreadsParser.fetchTopMetadata(any(), any())).thenReturn(grMeta);

        Map<MetadataProvider, BookMetadata> result = bookMetadataService.fetchTopMetadataMap(book, request);

        assertEquals(1, result.size());
        assertEquals("GoodReads Title", result.get(MetadataProvider.GoodReads).getTitle());
        assertFalse(result.containsKey(MetadataProvider.Google));
    }

    @Test
    void fetchTopMetadataMap_shouldHandleSlowProvider() {
        Book book = Book.builder().id(1L).title("Test Book").build();
        FetchMetadataRequest request = createFetchRequest(MetadataProvider.Google, MetadataProvider.GoodReads);

        when(googleParser.fetchTopMetadata(any(), any())).thenAnswer(invocation -> {
            Thread.sleep(100);
            return BookMetadata.builder().provider(MetadataProvider.Google).title("Slow Google").build();
        });
        BookMetadata grMeta = BookMetadata.builder().provider(MetadataProvider.GoodReads).title("Fast GoodReads").build();
        when(goodreadsParser.fetchTopMetadata(any(), any())).thenReturn(grMeta);

        Map<MetadataProvider, BookMetadata> result = bookMetadataService.fetchTopMetadataMap(book, request);

        assertEquals(2, result.size());
        assertEquals("Slow Google", result.get(MetadataProvider.Google).getTitle());
    }

    @Test
    void fetchTopMetadataMap_shouldReturnEmptyForEmptyProviders() {
        Book book = Book.builder().id(1L).build();
        FetchMetadataRequest request = createFetchRequest(); // No providers

        Map<MetadataProvider, BookMetadata> result = bookMetadataService.fetchTopMetadataMap(book, request);

        assertTrue(result.isEmpty());
    }

    @Test
    void fetchTopMetadataMap_shouldReturnEmptyForNullProviders() {
        Book book = Book.builder().id(1L).build();
        FetchMetadataRequest request = FetchMetadataRequest.builder().providers(null).build();

        Map<MetadataProvider, BookMetadata> result = bookMetadataService.fetchTopMetadataMap(book, request);

        assertTrue(result.isEmpty());
    }

    private void setupBook(long bookId) {
        BookEntity bookEntity = new BookEntity();
        Book book = Book.builder().build();
        when(bookRepository.findById(bookId)).thenReturn(Optional.of(bookEntity));
        when(bookMapper.toBook(bookEntity)).thenReturn(book);
    }

    private FetchMetadataRequest createFetchRequest(MetadataProvider... providers) {
        return FetchMetadataRequest.builder()
                .providers(providers != null ? List.of(providers) : null)
                .build();
    }
}