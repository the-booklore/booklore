package org.booklore.service.metadata;

import org.booklore.mapper.BookMapper;
import org.booklore.mapper.BookMetadataMapper;
import org.booklore.mapper.MetadataClearFlagsMapper;
import org.booklore.model.dto.Book;
import org.booklore.model.dto.BookMetadata;
import org.booklore.model.dto.request.FetchMetadataRequest;
import org.booklore.model.entity.BookEntity;
import org.booklore.model.enums.MetadataProvider;
import org.booklore.repository.BookMetadataRepository;
import org.booklore.repository.BookRepository;
import org.booklore.service.NotificationService;
import org.booklore.service.book.BookQueryService;
import org.booklore.service.metadata.extractor.CbxMetadataExtractor;
import org.booklore.service.metadata.parser.BookParser;
import org.booklore.service.metadata.parser.custom.CustomBookParser;
import org.booklore.service.metadata.parser.custom.CustomProviderRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.transaction.PlatformTransactionManager;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/**
 * Tests for the custom provider integration paths in BookMetadataService.
 * Focused on fetchMetadataFromCustomProvider and getDetailedCustomProviderMetadata.
 */
class BookMetadataServiceCustomProviderTest {

    @Mock private BookRepository bookRepository;
    @Mock private BookMapper bookMapper;
    @Mock private BookMetadataMapper bookMetadataMapper;
    @Mock private BookMetadataUpdater bookMetadataUpdater;
    @Mock private NotificationService notificationService;
    @Mock private BookMetadataRepository bookMetadataRepository;
    @Mock private BookQueryService bookQueryService;
    @Mock private CbxMetadataExtractor cbxMetadataExtractor;
    @Mock private MetadataClearFlagsMapper metadataClearFlagsMapper;
    @Mock private PlatformTransactionManager transactionManager;
    @Mock private CustomProviderRegistry customProviderRegistry;

    private BookMetadataService service;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        Map<MetadataProvider, BookParser> parserMap = Map.of();
        service = new BookMetadataService(
                bookRepository, bookMapper, bookMetadataMapper, bookMetadataUpdater,
                notificationService, bookMetadataRepository, bookQueryService,
                parserMap, cbxMetadataExtractor, metadataClearFlagsMapper,
                transactionManager, customProviderRegistry
        );
    }

    @Nested
    @DisplayName("fetchMetadataFromCustomProvider")
    class FetchFromCustomProviderTests {

        @Test
        @DisplayName("Delegates to the custom parser when provider ID is registered")
        void fetchMetadata_registeredProvider_delegatesToParser() {
            CustomBookParser parser = mock(CustomBookParser.class);
            when(customProviderRegistry.getParser("custom-1")).thenReturn(parser);

            Book book = Book.builder().title("Test Book").build();
            FetchMetadataRequest request = FetchMetadataRequest.builder()
                    .title("Test Book").build();
            BookMetadata metadata = BookMetadata.builder().title("Result").build();
            when(parser.fetchMetadata(book, request)).thenReturn(List.of(metadata));

            List<BookMetadata> results = service.fetchMetadataFromCustomProvider("custom-1", book, request);

            assertThat(results).hasSize(1);
            assertThat(results.getFirst().getTitle()).isEqualTo("Result");
            verify(parser).fetchMetadata(book, request);
        }

        @Test
        @DisplayName("Returns empty list when custom provider ID is not registered")
        void fetchMetadata_unregisteredProvider_returnsEmpty() {
            when(customProviderRegistry.getParser("nonexistent")).thenReturn(null);

            Book book = Book.builder().title("Test").build();
            FetchMetadataRequest request = FetchMetadataRequest.builder().title("Test").build();

            List<BookMetadata> results = service.fetchMetadataFromCustomProvider("nonexistent", book, request);

            assertThat(results).isEmpty();
        }
    }

    @Nested
    @DisplayName("getDetailedCustomProviderMetadata")
    class DetailedCustomMetadataTests {

        @Test
        @DisplayName("Delegates to custom parser's fetchDetailedMetadata")
        void getDetailed_registeredProvider_delegatesToParser() {
            CustomBookParser parser = mock(CustomBookParser.class);
            when(customProviderRegistry.getParser("custom-1")).thenReturn(parser);

            BookMetadata detailed = BookMetadata.builder()
                    .title("Detailed")
                    .isbn13("9781234567890")
                    .build();
            when(parser.fetchDetailedMetadata("item-123")).thenReturn(detailed);

            BookMetadata result = service.getDetailedCustomProviderMetadata("custom-1", "item-123");

            assertThat(result).isNotNull();
            assertThat(result.getTitle()).isEqualTo("Detailed");
            verify(parser).fetchDetailedMetadata("item-123");
        }

        @Test
        @DisplayName("Returns null when provider is not registered")
        void getDetailed_unregisteredProvider_returnsNull() {
            when(customProviderRegistry.getParser("nonexistent")).thenReturn(null);

            BookMetadata result = service.getDetailedCustomProviderMetadata("nonexistent", "item-123");

            assertThat(result).isNull();
        }

        @Test
        @DisplayName("Returns null when parser returns null for unknown item ID")
        void getDetailed_itemNotFound_returnsNull() {
            CustomBookParser parser = mock(CustomBookParser.class);
            when(customProviderRegistry.getParser("custom-1")).thenReturn(parser);
            when(parser.fetchDetailedMetadata("unknown-item")).thenReturn(null);

            BookMetadata result = service.getDetailedCustomProviderMetadata("custom-1", "unknown-item");

            assertThat(result).isNull();
        }
    }

    @Nested
    @DisplayName("getProspectiveMetadataListForBookId - custom provider path")
    class ProspectiveMetadataCustomProviderTests {

        @Test
        @DisplayName("Custom provider results are included in the merged flux alongside built-in providers")
        void getProspective_withCustomProviderIds_includesCustomResults() {
            BookEntity bookEntity = mock(BookEntity.class);
            Book book = Book.builder().title("Test Book").build();
            when(bookRepository.findById(1L)).thenReturn(Optional.of(bookEntity));
            when(bookMapper.toBook(bookEntity)).thenReturn(book);

            CustomBookParser customParser = mock(CustomBookParser.class);
            when(customProviderRegistry.getParser("custom-1")).thenReturn(customParser);

            BookMetadata customResult = BookMetadata.builder().title("Custom Result").build();
            when(customParser.fetchMetadata(eq(book), any(FetchMetadataRequest.class)))
                    .thenReturn(List.of(customResult));

            FetchMetadataRequest request = FetchMetadataRequest.builder()
                    .providers(List.of())
                    .customProviderIds(List.of("custom-1"))
                    .title("Test Book")
                    .build();

            List<BookMetadata> results = service.getProspectiveMetadataListForBookId(1L, request)
                    .collectList()
                    .block();

            assertThat(results).isNotNull();
            assertThat(results).hasSize(1);
            assertThat(results.getFirst().getTitle()).isEqualTo("Custom Result");
        }

        @Test
        @DisplayName("Null customProviderIds list results in only built-in providers being queried")
        void getProspective_nullCustomProviderIds_onlyBuiltIn() {
            BookEntity bookEntity = mock(BookEntity.class);
            Book book = Book.builder().title("Test Book").build();
            when(bookRepository.findById(1L)).thenReturn(Optional.of(bookEntity));
            when(bookMapper.toBook(bookEntity)).thenReturn(book);

            FetchMetadataRequest request = FetchMetadataRequest.builder()
                    .providers(List.of())
                    .customProviderIds(null)
                    .title("Test Book")
                    .build();

            List<BookMetadata> results = service.getProspectiveMetadataListForBookId(1L, request)
                    .collectList()
                    .block();

            assertThat(results).isNotNull().isEmpty();
            verifyNoInteractions(customProviderRegistry);
        }

        @Test
        @DisplayName("Empty customProviderIds list results in only built-in providers being queried")
        void getProspective_emptyCustomProviderIds_onlyBuiltIn() {
            BookEntity bookEntity = mock(BookEntity.class);
            Book book = Book.builder().title("Test Book").build();
            when(bookRepository.findById(1L)).thenReturn(Optional.of(bookEntity));
            when(bookMapper.toBook(bookEntity)).thenReturn(book);

            FetchMetadataRequest request = FetchMetadataRequest.builder()
                    .providers(List.of())
                    .customProviderIds(List.of())
                    .title("Test Book")
                    .build();

            List<BookMetadata> results = service.getProspectiveMetadataListForBookId(1L, request)
                    .collectList()
                    .block();

            assertThat(results).isNotNull().isEmpty();
            verifyNoInteractions(customProviderRegistry);
        }
    }
}
