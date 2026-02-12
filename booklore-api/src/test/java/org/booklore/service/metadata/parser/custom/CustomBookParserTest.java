package org.booklore.service.metadata.parser.custom;

import org.booklore.mapper.ExternalMetadataMapper;
import org.booklore.model.dto.Book;
import org.booklore.model.dto.BookMetadata;
import org.booklore.model.dto.CoverImage;
import org.booklore.model.dto.external.ExternalBookMetadata;
import org.booklore.model.dto.external.ExternalCoverImage;
import org.booklore.model.dto.external.ExternalProviderCapabilities;
import org.booklore.model.dto.request.CoverFetchRequest;
import org.booklore.model.dto.request.FetchMetadataRequest;
import org.booklore.model.dto.settings.CustomMetadataProviderConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class CustomBookParserTest {

    @Mock
    private CustomProviderClient client;

    private ExternalMetadataMapper mapper;
    private CustomMetadataProviderConfig config;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        mapper = new ExternalMetadataMapper();
        config = CustomMetadataProviderConfig.builder()
                .id("test-provider-id")
                .name("Test Provider")
                .baseUrl("https://provider.example.com")
                .enabled(true)
                .build();
        when(client.getConfig()).thenReturn(config);
    }

    private CustomBookParser createParser() {
        return new CustomBookParser(client, mapper);
    }

    @Nested
    @DisplayName("Search Fallback Strategy")
    class SearchFallbackTests {

        @Test
        @DisplayName("ISBN-13 search is attempted first when ISBN is available and provider supports it")
        void fetchMetadata_isbnAvailable_searchesByIsbnFirst() {
            config.setCapabilities(ExternalProviderCapabilities.Capabilities.builder()
                    .supportsMetadata(true)
                    .supportsIsbnSearch(true)
                    .supportsTitleAuthorSearch(true)
                    .build());

            ExternalBookMetadata hit = ExternalBookMetadata.builder()
                    .title("Found by ISBN").build();
            when(client.searchMetadata(isNull(), isNull(), isNull(), eq("9780441172719"), isNull(), isNull(), isNull(), eq(10)))
                    .thenReturn(List.of(hit));

            FetchMetadataRequest request = FetchMetadataRequest.builder()
                    .isbn("9780441172719")
                    .title("Dune")
                    .author("Frank Herbert")
                    .build();

            List<BookMetadata> results = createParser().fetchMetadata(null, request);

            assertThat(results).hasSize(1);
            assertThat(results.getFirst().getTitle()).isEqualTo("Found by ISBN");
            // Title+author search should NOT have been called since ISBN succeeded
            verify(client, never()).searchMetadata(isNull(), eq("Dune"), any(), isNull(), isNull(), any(), isNull(), anyInt());
        }

        @Test
        @DisplayName("Falls back to title+author search when ISBN search returns empty")
        void fetchMetadata_isbnSearchEmpty_fallsBackToTitleAuthor() {
            config.setCapabilities(ExternalProviderCapabilities.Capabilities.builder()
                    .supportsMetadata(true)
                    .supportsIsbnSearch(true)
                    .supportsTitleAuthorSearch(true)
                    .build());

            // ISBN search returns empty
            when(client.searchMetadata(isNull(), isNull(), isNull(), eq("9780441172719"), isNull(), isNull(), isNull(), eq(10)))
                    .thenReturn(List.of());

            ExternalBookMetadata hit = ExternalBookMetadata.builder()
                    .title("Found by Title").build();
            when(client.searchMetadata(isNull(), eq("Dune"), eq("Frank Herbert"), isNull(), isNull(), isNull(), isNull(), eq(10)))
                    .thenReturn(List.of(hit));

            FetchMetadataRequest request = FetchMetadataRequest.builder()
                    .isbn("9780441172719")
                    .title("Dune")
                    .author("Frank Herbert")
                    .build();

            List<BookMetadata> results = createParser().fetchMetadata(null, request);

            assertThat(results).hasSize(1);
            assertThat(results.getFirst().getTitle()).isEqualTo("Found by Title");
        }

        @Test
        @DisplayName("Falls back to general query when both ISBN and title+author searches return empty")
        void fetchMetadata_allSpecificSearchesEmpty_fallsBackToGeneralQuery() {
            config.setCapabilities(ExternalProviderCapabilities.Capabilities.builder()
                    .supportsMetadata(true)
                    .supportsIsbnSearch(true)
                    .supportsTitleAuthorSearch(true)
                    .build());

            // ISBN search returns empty
            when(client.searchMetadata(isNull(), isNull(), isNull(), eq("9780441172719"), isNull(), isNull(), isNull(), eq(10)))
                    .thenReturn(List.of());
            // Title+author search returns empty
            when(client.searchMetadata(isNull(), eq("Dune"), eq("Frank Herbert"), isNull(), isNull(), isNull(), isNull(), eq(10)))
                    .thenReturn(List.of());

            ExternalBookMetadata hit = ExternalBookMetadata.builder()
                    .title("Found by Query").build();
            when(client.searchMetadata(eq("Dune Frank Herbert"), isNull(), isNull(), isNull(), isNull(), isNull(), isNull(), eq(10)))
                    .thenReturn(List.of(hit));

            FetchMetadataRequest request = FetchMetadataRequest.builder()
                    .isbn("9780441172719")
                    .title("Dune")
                    .author("Frank Herbert")
                    .build();

            List<BookMetadata> results = createParser().fetchMetadata(null, request);

            assertThat(results).hasSize(1);
            assertThat(results.getFirst().getTitle()).isEqualTo("Found by Query");
        }

        @Test
        @DisplayName("ISBN-10 is placed in the isbn10 parameter slot, not isbn13")
        void fetchMetadata_isbn10_routedToCorrectParam() {
            config.setCapabilities(ExternalProviderCapabilities.Capabilities.builder()
                    .supportsMetadata(true)
                    .supportsIsbnSearch(true)
                    .build());

            ExternalBookMetadata hit = ExternalBookMetadata.builder()
                    .title("Found").build();
            when(client.searchMetadata(isNull(), isNull(), isNull(), isNull(), eq("0441172717"), isNull(), isNull(), eq(10)))
                    .thenReturn(List.of(hit));

            FetchMetadataRequest request = FetchMetadataRequest.builder()
                    .isbn("0441172717")
                    .build();

            List<BookMetadata> results = createParser().fetchMetadata(null, request);

            assertThat(results).hasSize(1);
            verify(client).searchMetadata(isNull(), isNull(), isNull(), isNull(), eq("0441172717"), isNull(), isNull(), eq(10));
        }

        @Test
        @DisplayName("Skips ISBN search entirely when provider does not support it")
        void fetchMetadata_isbnSearchNotSupported_skipsDirectlyToTitleAuthor() {
            config.setCapabilities(ExternalProviderCapabilities.Capabilities.builder()
                    .supportsMetadata(true)
                    .supportsIsbnSearch(false)
                    .supportsTitleAuthorSearch(true)
                    .build());

            ExternalBookMetadata hit = ExternalBookMetadata.builder()
                    .title("Found by Title").build();
            when(client.searchMetadata(isNull(), eq("Dune"), eq("Frank Herbert"), isNull(), isNull(), isNull(), isNull(), eq(10)))
                    .thenReturn(List.of(hit));

            FetchMetadataRequest request = FetchMetadataRequest.builder()
                    .isbn("9780441172719")
                    .title("Dune")
                    .author("Frank Herbert")
                    .build();

            List<BookMetadata> results = createParser().fetchMetadata(null, request);

            assertThat(results).hasSize(1);
            assertThat(results.getFirst().getTitle()).isEqualTo("Found by Title");
            // Verify ISBN search was never attempted
            verify(client, never()).searchMetadata(isNull(), isNull(), isNull(), any(), any(), isNull(), isNull(), anyInt());
        }

        @Test
        @DisplayName("General query is built from title only when author is absent")
        void fetchMetadata_noAuthor_generalQueryIsTitleOnly() {
            config.setCapabilities(ExternalProviderCapabilities.Capabilities.builder()
                    .supportsMetadata(true)
                    .supportsIsbnSearch(false)
                    .supportsTitleAuthorSearch(false)
                    .build());

            ExternalBookMetadata hit = ExternalBookMetadata.builder()
                    .title("Found").build();
            when(client.searchMetadata(eq("Dune"), isNull(), isNull(), isNull(), isNull(), isNull(), isNull(), eq(10)))
                    .thenReturn(List.of(hit));

            FetchMetadataRequest request = FetchMetadataRequest.builder()
                    .title("Dune")
                    .build();

            List<BookMetadata> results = createParser().fetchMetadata(null, request);

            assertThat(results).hasSize(1);
            verify(client).searchMetadata(eq("Dune"), isNull(), isNull(), isNull(), isNull(), isNull(), isNull(), eq(10));
        }
    }

    @Nested
    @DisplayName("Field Fallback from Book Entity")
    class FieldFallbackTests {

        @Test
        @DisplayName("Request fields with existing book metadata fall back to book entity values when request is empty")
        void fetchMetadata_emptyRequest_fallsBackToBookMetadata() {
            config.setCapabilities(ExternalProviderCapabilities.Capabilities.builder()
                    .supportsMetadata(true)
                    .supportsIsbnSearch(false)
                    .supportsTitleAuthorSearch(true)
                    .build());

            BookMetadata existingMetadata = BookMetadata.builder()
                    .title("Existing Title")
                    .authors(Set.of("Existing Author"))
                    .isbn13("9781234567890")
                    .asin("B001234567")
                    .build();
            Book book = Book.builder().metadata(existingMetadata).build();

            ExternalBookMetadata hit = ExternalBookMetadata.builder()
                    .title("Result").build();
            when(client.searchMetadata(isNull(), eq("Existing Title"), eq("Existing Author"), isNull(), isNull(), eq("B001234567"), isNull(), eq(10)))
                    .thenReturn(List.of(hit));

            FetchMetadataRequest request = FetchMetadataRequest.builder().build();

            List<BookMetadata> results = createParser().fetchMetadata(book, request);

            assertThat(results).hasSize(1);
        }

        @Test
        @DisplayName("Request fields take precedence over book entity metadata")
        void fetchMetadata_requestFieldsPresent_overrideBookMetadata() {
            config.setCapabilities(ExternalProviderCapabilities.Capabilities.builder()
                    .supportsMetadata(true)
                    .supportsIsbnSearch(false)
                    .supportsTitleAuthorSearch(true)
                    .build());

            BookMetadata existingMetadata = BookMetadata.builder()
                    .title("Old Title")
                    .authors(Set.of("Old Author"))
                    .build();
            Book book = Book.builder().metadata(existingMetadata).build();

            ExternalBookMetadata hit = ExternalBookMetadata.builder()
                    .title("Result").build();
            when(client.searchMetadata(isNull(), eq("New Title"), eq("New Author"), isNull(), isNull(), isNull(), isNull(), eq(10)))
                    .thenReturn(List.of(hit));

            FetchMetadataRequest request = FetchMetadataRequest.builder()
                    .title("New Title")
                    .author("New Author")
                    .build();

            List<BookMetadata> results = createParser().fetchMetadata(book, request);

            assertThat(results).hasSize(1);
            verify(client).searchMetadata(isNull(), eq("New Title"), eq("New Author"), isNull(), isNull(), isNull(), isNull(), eq(10));
        }

        @Test
        @DisplayName("ISBN13 from book metadata is used for isbn13 param, not isbn10")
        void fetchMetadata_bookHasIsbn13_usedAsIsbn13() {
            config.setCapabilities(ExternalProviderCapabilities.Capabilities.builder()
                    .supportsMetadata(true)
                    .supportsIsbnSearch(true)
                    .build());

            BookMetadata existingMetadata = BookMetadata.builder()
                    .isbn13("9781234567890")
                    .build();
            Book book = Book.builder().metadata(existingMetadata).build();

            when(client.searchMetadata(isNull(), isNull(), isNull(), eq("9781234567890"), isNull(), isNull(), isNull(), eq(10)))
                    .thenReturn(List.of(ExternalBookMetadata.builder().title("Hit").build()));

            FetchMetadataRequest request = FetchMetadataRequest.builder().build();

            createParser().fetchMetadata(book, request);

            verify(client).searchMetadata(isNull(), isNull(), isNull(), eq("9781234567890"), isNull(), isNull(), isNull(), eq(10));
        }

        @Test
        @DisplayName("ISBN10 from book metadata is used when isbn13 is absent")
        void fetchMetadata_bookHasOnlyIsbn10_usedForSearch() {
            config.setCapabilities(ExternalProviderCapabilities.Capabilities.builder()
                    .supportsMetadata(true)
                    .supportsIsbnSearch(true)
                    .build());

            BookMetadata existingMetadata = BookMetadata.builder()
                    .isbn10("0441172717")
                    .build();
            Book book = Book.builder().metadata(existingMetadata).build();

            when(client.searchMetadata(isNull(), isNull(), isNull(), isNull(), eq("0441172717"), isNull(), isNull(), eq(10)))
                    .thenReturn(List.of(ExternalBookMetadata.builder().title("Hit").build()));

            FetchMetadataRequest request = FetchMetadataRequest.builder().build();

            createParser().fetchMetadata(book, request);

            verify(client).searchMetadata(isNull(), isNull(), isNull(), isNull(), eq("0441172717"), isNull(), isNull(), eq(10));
        }

        @Test
        @DisplayName("Multiple authors from book metadata are joined with comma-space")
        void fetchMetadata_multipleAuthorsOnBook_joinedWithComma() {
            config.setCapabilities(ExternalProviderCapabilities.Capabilities.builder()
                    .supportsMetadata(true)
                    .supportsIsbnSearch(false)
                    .supportsTitleAuthorSearch(true)
                    .build());

            BookMetadata existingMetadata = BookMetadata.builder()
                    .title("Good Omens")
                    .authors(Set.of("Terry Pratchett", "Neil Gaiman"))
                    .build();
            Book book = Book.builder().metadata(existingMetadata).build();

            when(client.searchMetadata(isNull(), eq("Good Omens"), argThat(a -> a != null && a.contains("Terry Pratchett") && a.contains("Neil Gaiman")), isNull(), isNull(), isNull(), isNull(), eq(10)))
                    .thenReturn(List.of(ExternalBookMetadata.builder().title("Good Omens").build()));

            FetchMetadataRequest request = FetchMetadataRequest.builder().build();

            List<BookMetadata> results = createParser().fetchMetadata(book, request);

            assertThat(results).hasSize(1);
        }
    }

    @Nested
    @DisplayName("Capability Gating")
    class CapabilityGatingTests {

        @Test
        @DisplayName("fetchMetadata returns empty when provider does not support metadata")
        void fetchMetadata_metadataNotSupported_returnsEmpty() {
            config.setCapabilities(ExternalProviderCapabilities.Capabilities.builder()
                    .supportsMetadata(false)
                    .build());

            FetchMetadataRequest request = FetchMetadataRequest.builder()
                    .title("Anything").build();

            List<BookMetadata> results = createParser().fetchMetadata(null, request);

            assertThat(results).isEmpty();
            verify(client, never()).searchMetadata(any(), any(), any(), any(), any(), any(), any(), any());
            verify(client, never()).getMetadataById(any());
        }

        @Test
        @DisplayName("fetchMetadata treats null capabilities as metadata supported (optimistic default)")
        void fetchMetadata_nullCapabilities_treatedAsSupported() {
            config.setCapabilities(null);

            ExternalBookMetadata hit = ExternalBookMetadata.builder()
                    .title("Found").build();
            when(client.searchMetadata(eq("Test"), isNull(), isNull(), isNull(), isNull(), isNull(), isNull(), eq(10)))
                    .thenReturn(List.of(hit));

            FetchMetadataRequest request = FetchMetadataRequest.builder()
                    .title("Test").build();

            List<BookMetadata> results = createParser().fetchMetadata(null, request);

            assertThat(results).hasSize(1);
        }

        @Test
        @DisplayName("getCovers returns empty when provider does not support covers")
        void getCovers_coversNotSupported_returnsEmpty() {
            config.setCapabilities(ExternalProviderCapabilities.Capabilities.builder()
                    .supportsCovers(false)
                    .build());

            CoverFetchRequest request = CoverFetchRequest.builder()
                    .title("Test").build();

            List<CoverImage> results = createParser().getCovers(request);

            assertThat(results).isEmpty();
        }

        @Test
        @DisplayName("getCovers returns empty when capabilities are null (covers requires explicit opt-in)")
        void getCovers_nullCapabilities_returnsEmpty() {
            config.setCapabilities(null);

            CoverFetchRequest request = CoverFetchRequest.builder()
                    .title("Test").build();

            List<CoverImage> results = createParser().getCovers(request);

            assertThat(results).isEmpty();
        }

        @Test
        @DisplayName("getCovers delegates to client when covers are supported")
        void getCovers_coversSupported_delegatesToClient() {
            config.setCapabilities(ExternalProviderCapabilities.Capabilities.builder()
                    .supportsCovers(true)
                    .build());

            ExternalCoverImage cover = ExternalCoverImage.builder()
                    .url("https://example.com/cover.jpg")
                    .width(600).height(900).build();
            when(client.searchCovers(isNull(), eq("Test Book"), eq("Author"), isNull(), isNull(), isNull(), eq("large")))
                    .thenReturn(List.of(cover));

            CoverFetchRequest request = CoverFetchRequest.builder()
                    .title("Test Book")
                    .author("Author")
                    .build();

            List<CoverImage> results = createParser().getCovers(request);

            assertThat(results).hasSize(1);
            assertThat(results.getFirst().getUrl()).isEqualTo("https://example.com/cover.jpg");
        }

        @Test
        @DisplayName("fetchDetailedMetadata returns null when metadata is not supported")
        void fetchDetailedMetadata_metadataNotSupported_returnsNull() {
            config.setCapabilities(ExternalProviderCapabilities.Capabilities.builder()
                    .supportsMetadata(false)
                    .build());

            BookMetadata result = createParser().fetchDetailedMetadata("some-id");

            assertThat(result).isNull();
            verify(client, never()).getMetadataById(any());
        }
    }

    @Nested
    @DisplayName("fetchTopMetadata")
    class FetchTopMetadataTests {

        @Test
        @DisplayName("Returns the first result from fetchMetadata")
        void fetchTopMetadata_multipleResults_returnsFirst() {
            config.setCapabilities(ExternalProviderCapabilities.Capabilities.builder()
                    .supportsMetadata(true)
                    .supportsIsbnSearch(false)
                    .supportsTitleAuthorSearch(true)
                    .build());

            ExternalBookMetadata first = ExternalBookMetadata.builder().title("First").build();
            ExternalBookMetadata second = ExternalBookMetadata.builder().title("Second").build();
            when(client.searchMetadata(isNull(), eq("Query"), isNull(), isNull(), isNull(), isNull(), isNull(), eq(10)))
                    .thenReturn(List.of(first, second));

            FetchMetadataRequest request = FetchMetadataRequest.builder()
                    .title("Query").build();

            BookMetadata result = createParser().fetchTopMetadata(null, request);

            assertThat(result).isNotNull();
            assertThat(result.getTitle()).isEqualTo("First");
        }

        @Test
        @DisplayName("Returns null when no results found")
        void fetchTopMetadata_noResults_returnsNull() {
            config.setCapabilities(ExternalProviderCapabilities.Capabilities.builder()
                    .supportsMetadata(true)
                    .supportsIsbnSearch(false)
                    .supportsTitleAuthorSearch(false)
                    .build());

            when(client.searchMetadata(eq("Query"), isNull(), isNull(), isNull(), isNull(), isNull(), isNull(), eq(10)))
                    .thenReturn(List.of());

            FetchMetadataRequest request = FetchMetadataRequest.builder()
                    .title("Query").build();

            BookMetadata result = createParser().fetchTopMetadata(null, request);

            assertThat(result).isNull();
        }
    }

    @Nested
    @DisplayName("fetchDetailedMetadata")
    class FetchDetailedMetadataTests {

        @Test
        @DisplayName("Delegates to client and maps result")
        void fetchDetailedMetadata_clientReturnsData_mapped() {
            config.setCapabilities(ExternalProviderCapabilities.Capabilities.builder()
                    .supportsMetadata(true)
                    .build());

            ExternalBookMetadata external = ExternalBookMetadata.builder()
                    .title("Detailed Book")
                    .authors(List.of("Author"))
                    .isbn13("9781234567890")
                    .build();
            when(client.getMetadataById("item-123")).thenReturn(external);

            BookMetadata result = createParser().fetchDetailedMetadata("item-123");

            assertThat(result).isNotNull();
            assertThat(result.getTitle()).isEqualTo("Detailed Book");
            assertThat(result.getIsbn13()).isEqualTo("9781234567890");
        }

        @Test
        @DisplayName("Returns null when client returns null for unknown item ID")
        void fetchDetailedMetadata_clientReturnsNull_returnsNull() {
            config.setCapabilities(ExternalProviderCapabilities.Capabilities.builder()
                    .supportsMetadata(true)
                    .build());

            when(client.getMetadataById("nonexistent")).thenReturn(null);

            BookMetadata result = createParser().fetchDetailedMetadata("nonexistent");

            assertThat(result).isNull();
        }
    }

    @Nested
    @DisplayName("externalUrl Stripping")
    class ExternalUrlTests {

        @Test
        @DisplayName("externalUrl on search results is always nullified to prevent leaking provider internal URLs")
        void fetchMetadata_externalUrlStripped() {
            config.setCapabilities(ExternalProviderCapabilities.Capabilities.builder()
                    .supportsMetadata(true)
                    .supportsIsbnSearch(false)
                    .supportsTitleAuthorSearch(true)
                    .build());

            ExternalBookMetadata hit = ExternalBookMetadata.builder()
                    .title("Book With URL")
                    .build();
            when(client.searchMetadata(isNull(), eq("Test"), isNull(), isNull(), isNull(), isNull(), isNull(), eq(10)))
                    .thenReturn(List.of(hit));

            FetchMetadataRequest request = FetchMetadataRequest.builder()
                    .title("Test").build();

            List<BookMetadata> results = createParser().fetchMetadata(null, request);

            assertThat(results).hasSize(1);
            assertThat(results.getFirst().getExternalUrl()).isNull();
        }
    }

    @Test
    @DisplayName("getProviderId and getProviderName return config values")
    void providerIdentity_returnsConfigValues() {
        CustomBookParser parser = createParser();

        assertThat(parser.getProviderId()).isEqualTo("test-provider-id");
        assertThat(parser.getProviderName()).isEqualTo("Test Provider");
    }
}
