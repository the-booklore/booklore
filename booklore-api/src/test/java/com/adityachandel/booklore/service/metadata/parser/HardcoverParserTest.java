package com.adityachandel.booklore.service.metadata.parser;

import com.adityachandel.booklore.model.dto.Book;
import com.adityachandel.booklore.model.dto.BookMetadata;
import com.adityachandel.booklore.model.dto.request.FetchMetadataRequest;
import com.adityachandel.booklore.model.enums.MetadataProvider;
import com.adityachandel.booklore.service.metadata.parser.hardcover.GraphQLResponse;
import com.adityachandel.booklore.service.metadata.parser.hardcover.HardcoverBookDetails;
import com.adityachandel.booklore.service.metadata.parser.hardcover.HardcoverBookSearchService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.*;

/**
 * Unit tests for HardcoverParser.
 * 
 * These tests verify:
 * - Combined title+author search strategy for better reliability
 * - ISBN search behavior
 * - Author filtering logic
 * - Mood/genre/tag mapping with quality filtering
 * - Edge cases and error handling
 */
class HardcoverParserTest {

    @Mock
    private HardcoverBookSearchService hardcoverBookSearchService;

    private HardcoverParser parser;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        parser = new HardcoverParser(hardcoverBookSearchService);
    }

    @Nested
    @DisplayName("Search Strategy Tests")
    class SearchStrategyTests {

        @Test
        @DisplayName("Should search with combined title+author when both provided")
        void fetchMetadata_titleAndAuthor_searchesCombined() {
            Book book = Book.builder().title("Lamb").build();
            FetchMetadataRequest request = FetchMetadataRequest.builder()
                    .title("Lamb")
                    .author("Christopher Moore")
                    .build();

            GraphQLResponse.Hit hit = createHitWithAuthor("Lamb: The Gospel According to Biff", "Christopher Moore");
            when(hardcoverBookSearchService.searchBooks("Lamb Christopher Moore"))
                    .thenReturn(List.of(hit));

            List<BookMetadata> results = parser.fetchMetadata(book, request);

            verify(hardcoverBookSearchService).searchBooks("Lamb Christopher Moore");
            assertThat(results).hasSize(1);
            assertThat(results.get(0).getTitle()).isEqualTo("Lamb: The Gospel According to Biff");
        }

        @Test
        @DisplayName("Should fall back to title-only search when combined search returns empty")
        void fetchMetadata_combinedSearchEmpty_fallsBackToTitleOnly() {
            Book book = Book.builder().title("Some Book").build();
            FetchMetadataRequest request = FetchMetadataRequest.builder()
                    .title("Some Book")
                    .author("Unknown Author")
                    .build();

            when(hardcoverBookSearchService.searchBooks("Some Book Unknown Author"))
                    .thenReturn(Collections.emptyList());
            
            GraphQLResponse.Hit hit = createHitWithAuthor("Some Book", "Different Author");
            when(hardcoverBookSearchService.searchBooks("Some Book"))
                    .thenReturn(List.of(hit));

            List<BookMetadata> results = parser.fetchMetadata(book, request);

            verify(hardcoverBookSearchService).searchBooks("Some Book Unknown Author");
            verify(hardcoverBookSearchService).searchBooks("Some Book");
        }

        @Test
        @DisplayName("Should fall back to title-only search when combined search returns results but they are filtered out")
        void fetchMetadata_combinedSearchFilteredOut_fallsBackToTitleOnly() {
            Book book = Book.builder().title("Portrait of a Thief").build();
            FetchMetadataRequest request = FetchMetadataRequest.builder()
                    .title("Portrait of a Thief")
                    .author("Grace D. Li")
                    .build();

            // Simulate combined search returning a result that DOES NOT match the author (e.g. some other book matched the string)
            GraphQLResponse.Hit badHit = createHitWithAuthor("Portrait of something", "Random Person");
            when(hardcoverBookSearchService.searchBooks("Portrait of a Thief Grace D. Li"))
                    .thenReturn(List.of(badHit)); // Returns a hit, but fuzzy score will fail or simple check will fail

            // Fallback search should match
            GraphQLResponse.Hit goodHit = createHitWithAuthor("Portrait of a Thief", "Grace D. Li");
            when(hardcoverBookSearchService.searchBooks("Portrait of a Thief"))
                    .thenReturn(List.of(goodHit));

            List<BookMetadata> results = parser.fetchMetadata(book, request);

            verify(hardcoverBookSearchService).searchBooks("Portrait of a Thief Grace D. Li");
            verify(hardcoverBookSearchService).searchBooks("Portrait of a Thief");
            assertThat(results).hasSize(1);
            assertThat(results.get(0).getTitle()).isEqualTo("Portrait of a Thief");
        }

        @Test
        @DisplayName("Should search by ISBN when provided")
        void fetchMetadata_isbnProvided_searchesByIsbn() {
            Book book = Book.builder().title("Any Title").build();
            FetchMetadataRequest request = FetchMetadataRequest.builder()
                    .title("Any Title")
                    .isbn("978-0316769488")
                    .build();

            GraphQLResponse.Hit hit = createHitWithAuthor("The Catcher in the Rye", "J.D. Salinger");
            hit.getDocument().setIsbns(List.of("9780316769488", "0316769487"));
            when(hardcoverBookSearchService.searchBooks("978-0316769488"))
                    .thenReturn(List.of(hit));

            List<BookMetadata> results = parser.fetchMetadata(book, request);

            verify(hardcoverBookSearchService).searchBooks("978-0316769488");
            verify(hardcoverBookSearchService, never()).searchBooks(contains("title"));
        }

        @Test
        @DisplayName("Should search title-only when no author provided")
        void fetchMetadata_noAuthor_searchesTitleOnly() {
            Book book = Book.builder().title("The Prince").build();
            FetchMetadataRequest request = FetchMetadataRequest.builder()
                    .title("The Prince")
                    .build();

            GraphQLResponse.Hit hit = createHitWithAuthor("The Prince", "Niccolò Machiavelli");
            when(hardcoverBookSearchService.searchBooks("The Prince"))
                    .thenReturn(List.of(hit));

            List<BookMetadata> results = parser.fetchMetadata(book, request);

            verify(hardcoverBookSearchService).searchBooks("The Prince");
        }
    }

    @Nested
    @DisplayName("Author Filtering Tests")
    class AuthorFilteringTests {

        @Test
        @DisplayName("Should filter results by author name when author provided")
        void fetchMetadata_authorProvided_filtersResults() {
            Book book = Book.builder().title("The Prince").build();
            FetchMetadataRequest request = FetchMetadataRequest.builder()
                    .title("The Prince")
                    .author("Machiavelli")
                    .build();

            List<GraphQLResponse.Hit> hits = List.of(
                    createHitWithAuthor("The Prince", "Kiera Cass"),
                    createHitWithAuthor("The Prince", "Niccolò Machiavelli"),
                    createHitWithAuthor("The Prince", "Tiffany Reisz")
            );
            when(hardcoverBookSearchService.searchBooks("The Prince Machiavelli"))
                    .thenReturn(hits);

            List<BookMetadata> results = parser.fetchMetadata(book, request);

            assertThat(results).hasSize(1);
            assertThat(results.get(0).getAuthors()).contains("Niccolò Machiavelli");
        }

        @Test
        @DisplayName("Should use fuzzy matching for author names")
        void fetchMetadata_fuzzyAuthorMatch_includesPartialMatches() {
            Book book = Book.builder().title("Test Book").build();
            FetchMetadataRequest request = FetchMetadataRequest.builder()
                    .title("Test Book")
                    .author("Moore")
                    .build();

            GraphQLResponse.Hit hit = createHitWithAuthor("Test Book", "Christopher Moore");
            when(hardcoverBookSearchService.searchBooks("Test Book Moore"))
                    .thenReturn(List.of(hit));

            List<BookMetadata> results = parser.fetchMetadata(book, request);

            assertThat(results).hasSize(1);
        }

        @Test
        @DisplayName("Should not filter by author for ISBN searches")
        void fetchMetadata_isbnSearch_noAuthorFilter() {
            Book book = Book.builder().title("Any Book").build();
            FetchMetadataRequest request = FetchMetadataRequest.builder()
                    .title("Any Book")
                    .isbn("123456789X")
                    .author("Wrong Author")  // Should be ignored
                    .build();

            GraphQLResponse.Hit hit = createHitWithAuthor("Any Book", "Correct Author");
            when(hardcoverBookSearchService.searchBooks("123456789X"))
                    .thenReturn(List.of(hit));
            List<BookMetadata> results = parser.fetchMetadata(book, request);

            assertThat(results).hasSize(1);  // Should not filter out
        }
    }

    @Nested
    @DisplayName("Metadata Mapping Tests")
    class MetadataMappingTests {

        @Test
        @DisplayName("Should map all basic metadata fields correctly")
        void fetchMetadata_fullDocument_mapsAllFields() {
            Book book = Book.builder().title("Test").build();
            FetchMetadataRequest request = FetchMetadataRequest.builder()
                    .title("Test")
                    .isbn("9781234567897")
                    .build();

            GraphQLResponse.Hit hit = createFullyPopulatedHit();
            when(hardcoverBookSearchService.searchBooks("9781234567897"))
                    .thenReturn(List.of(hit));

            List<BookMetadata> results = parser.fetchMetadata(book, request);

            assertThat(results).hasSize(1);
            BookMetadata metadata = results.get(0);
            
            assertThat(metadata.getTitle()).isEqualTo("Test Book");
            assertThat(metadata.getSubtitle()).isEqualTo("A Subtitle");
            assertThat(metadata.getDescription()).isEqualTo("A description");
            assertThat(metadata.getHardcoverId()).isEqualTo("test-book-slug");
            assertThat(metadata.getHardcoverBookId()).isEqualTo("12345");
            assertThat(metadata.getHardcoverRating()).isEqualTo(4.25);
            assertThat(metadata.getHardcoverReviewCount()).isEqualTo(100);
            assertThat(metadata.getPageCount()).isEqualTo(350);
            assertThat(metadata.getAuthors()).contains("Test Author");
            assertThat(metadata.getSeriesName()).isEqualTo("Test Series");
            assertThat(metadata.getSeriesNumber()).isEqualTo(2.0f);
            assertThat(metadata.getSeriesTotal()).isEqualTo(5);
            assertThat(metadata.getIsbn13()).isEqualTo("9781234567897");
            assertThat(metadata.getIsbn10()).isEqualTo("123456789X");
            assertThat(metadata.getProvider()).isEqualTo(MetadataProvider.Hardcover);
        }

        @Test
        @DisplayName("Should map correct ISBN-13 from ISBN-10")
        void fetchMetadata_fullDocument_isbn10() {
            Book book = Book.builder().title("Test").build();
            FetchMetadataRequest request = FetchMetadataRequest.builder()
                    .title("Test")
                    .isbn("123456789X")
                    .build();

            GraphQLResponse.Hit hit = createFullyPopulatedHit();
            when(hardcoverBookSearchService.searchBooks("123456789X"))
                    .thenReturn(List.of(hit));

            List<BookMetadata> results = parser.fetchMetadata(book, request);

            assertThat(results.get(0).getIsbn10()).isEqualTo("123456789X");
            assertThat(results.get(0).getIsbn13()).isEqualTo("9781234567897");
        }

        @Test
        @DisplayName("ISBN-13 not starting with 978 should not have an ISBN-10")
        void fetchMetadata_fullDocument_noIsbn10() {
            Book book = Book.builder().title("Test").build();
            FetchMetadataRequest request = FetchMetadataRequest.builder()
                    .title("Test")
                    .isbn("9791111111112")
                    .build();

            GraphQLResponse.Hit hit = createFullyPopulatedHit();
            when(hardcoverBookSearchService.searchBooks("9791111111112"))
                    .thenReturn(List.of(hit));

            List<BookMetadata> results = parser.fetchMetadata(book, request);

            assertThat(results.get(0).getIsbn10()).isNull();
            assertThat(results.get(0).getIsbn13()).isEqualTo("9791111111112");
        }

        @Test
        @DisplayName("Should map thumbnail URL correctly")
        void fetchMetadata_withImage_mapsThumbnailUrl() {
            Book book = Book.builder().title("Test").build();
            FetchMetadataRequest request = FetchMetadataRequest.builder()
                    .title("Test")
                    .build();

            GraphQLResponse.Hit hit = createHitWithAuthor("Test", "Author");
            GraphQLResponse.Image image = new GraphQLResponse.Image();
            image.setUrl("https://example.com/cover.jpg");
            hit.getDocument().setImage(image);
            
            when(hardcoverBookSearchService.searchBooks("Test"))
                    .thenReturn(List.of(hit));

            List<BookMetadata> results = parser.fetchMetadata(book, request);

            assertThat(results.get(0).getThumbnailUrl()).isEqualTo("https://example.com/cover.jpg");
        }

        @Test
        @DisplayName("Should handle null image gracefully")
        void fetchMetadata_nullImage_handlesGracefully() {
            Book book = Book.builder().title("Test").build();
            FetchMetadataRequest request = FetchMetadataRequest.builder()
                    .title("Test")
                    .build();

            GraphQLResponse.Hit hit = createHitWithAuthor("Test", "Author");
            hit.getDocument().setImage(null);
            
            when(hardcoverBookSearchService.searchBooks("Test"))
                    .thenReturn(List.of(hit));

            List<BookMetadata> results = parser.fetchMetadata(book, request);

            assertThat(results.get(0).getThumbnailUrl()).isNull();
        }
    }

    @Nested
    @DisplayName("Mood and Genre Filtering Tests")
    class MoodFilteringTests {

        @Test
        @DisplayName("Should fetch detailed book info for mood filtering when book ID available")
        void fetchMetadata_withBookId_fetchesDetailedMoods() {
            Book book = Book.builder().title("Test").build();
            FetchMetadataRequest request = FetchMetadataRequest.builder()
                    .title("Test")
                    .build();

            GraphQLResponse.Hit hit = createHitWithAuthor("Test", "Author");
            hit.getDocument().setId("12345");
            hit.getDocument().setMoods(List.of("sad", "dark", "funny", "hopeful"));
            
            when(hardcoverBookSearchService.searchBooks("Test"))
                    .thenReturn(List.of(hit));

            HardcoverBookDetails details = createBookDetailsWithMoodCounts();
            when(hardcoverBookSearchService.fetchBookDetails(12345))
                    .thenReturn(details);

            List<BookMetadata> results = parser.fetchMetadata(book, request);

            verify(hardcoverBookSearchService).fetchBookDetails(12345);
            assertThat(results.get(0).getMoods()).isNotNull();
        }

        @Test
        @DisplayName("Should fall back to basic mood filtering when detail fetch fails")
        void fetchMetadata_detailFetchFails_fallsBackToBasicFilter() {
            Book book = Book.builder().title("Test").build();
            FetchMetadataRequest request = FetchMetadataRequest.builder()
                    .title("Test")
                    .build();

            GraphQLResponse.Hit hit = createHitWithAuthor("Test", "Author");
            hit.getDocument().setId("12345");
            hit.getDocument().setMoods(List.of("sad", "funny", "invalid-mood"));
            
            when(hardcoverBookSearchService.searchBooks("Test"))
                    .thenReturn(List.of(hit));
            when(hardcoverBookSearchService.fetchBookDetails(12345))
                    .thenReturn(null);  // Simulate failure

            List<BookMetadata> results = parser.fetchMetadata(book, request);

            assertThat(results.get(0).getMoods())
                    .containsAnyOf("Sad", "Funny")
                    .doesNotContain("Invalid-Mood");
        }

        @Test
        @DisplayName("Should handle books without moods")
        void fetchMetadata_noMoods_handlesGracefully() {
            Book book = Book.builder().title("Test").build();
            FetchMetadataRequest request = FetchMetadataRequest.builder()
                    .title("Test")
                    .build();

            GraphQLResponse.Hit hit = createHitWithAuthor("Test", "Author");
            hit.getDocument().setMoods(null);
            
            when(hardcoverBookSearchService.searchBooks("Test"))
                    .thenReturn(List.of(hit));

            List<BookMetadata> results = parser.fetchMetadata(book, request);

            assertThat(results).hasSize(1);
        }
    }

    @Nested
    @DisplayName("Edge Cases and Error Handling")
    class EdgeCaseTests {

        @Test
        @DisplayName("Should return empty list when search returns null")
        void fetchMetadata_nullResponse_returnsEmptyList() {
            Book book = Book.builder().title("Test").build();
            FetchMetadataRequest request = FetchMetadataRequest.builder()
                    .title("Test")
                    .build();

            when(hardcoverBookSearchService.searchBooks(anyString()))
                    .thenReturn(null);

            List<BookMetadata> results = parser.fetchMetadata(book, request);

            assertThat(results).isEmpty();
        }

        @Test
        @DisplayName("Should return empty list when search returns empty")
        void fetchMetadata_emptyResponse_returnsEmptyList() {
            Book book = Book.builder().title("Nonexistent Book").build();
            FetchMetadataRequest request = FetchMetadataRequest.builder()
                    .title("Nonexistent Book")
                    .build();

            when(hardcoverBookSearchService.searchBooks(anyString()))
                    .thenReturn(Collections.emptyList());

            List<BookMetadata> results = parser.fetchMetadata(book, request);

            assertThat(results).isEmpty();
        }

        @Test
        @DisplayName("Should handle invalid book ID gracefully")
        void fetchMetadata_invalidBookId_handlesGracefully() {
            Book book = Book.builder().title("Test").build();
            FetchMetadataRequest request = FetchMetadataRequest.builder()
                    .title("Test")
                    .build();

            GraphQLResponse.Hit hit = createHitWithAuthor("Test", "Author");
            hit.getDocument().setId("not-a-number");
            
            when(hardcoverBookSearchService.searchBooks("Test"))
                    .thenReturn(List.of(hit));

            List<BookMetadata> results = parser.fetchMetadata(book, request);

            assertThat(results).hasSize(1);
            assertThat(results.get(0).getHardcoverBookId()).isEqualTo("not-a-number");
        }

        @Test
        @DisplayName("Should handle null title in request")
        void fetchMetadata_nullTitle_returnsEmptyList() {
            Book book = Book.builder().build();
            FetchMetadataRequest request = FetchMetadataRequest.builder()
                    .author("Some Author")
                    .build();

            when(hardcoverBookSearchService.searchBooks(anyString()))
                    .thenReturn(Collections.emptyList());

            List<BookMetadata> results = parser.fetchMetadata(book, request);

            assertThat(results).isEmpty();
        }

        @Test
        @DisplayName("Should handle invalid date format gracefully")
        void fetchMetadata_invalidDate_handlesGracefully() {
            Book book = Book.builder().title("Test").build();
            FetchMetadataRequest request = FetchMetadataRequest.builder()
                    .title("Test")
                    .build();

            GraphQLResponse.Hit hit = createHitWithAuthor("Test", "Author");
            hit.getDocument().setReleaseDate("invalid-date");
            
            when(hardcoverBookSearchService.searchBooks("Test"))
                    .thenReturn(List.of(hit));

            List<BookMetadata> results = parser.fetchMetadata(book, request);

            assertThat(results).hasSize(1);
            assertThat(results.get(0).getPublishedDate()).isNull();
        }
    }

    @Nested
    @DisplayName("fetchTopMetadata Tests")
    class FetchTopMetadataTests {

        @Test
        @DisplayName("Should return first result")
        void fetchTopMetadata_hasResults_returnsFirst() {
            Book book = Book.builder().title("Test").build();
            FetchMetadataRequest request = FetchMetadataRequest.builder()
                    .title("Test")
                    .build();

            List<GraphQLResponse.Hit> hits = List.of(
                    createHitWithAuthor("First Book", "Author 1"),
                    createHitWithAuthor("Second Book", "Author 2")
            );
            when(hardcoverBookSearchService.searchBooks("Test"))
                    .thenReturn(hits);

            BookMetadata result = parser.fetchTopMetadata(book, request);

            assertThat(result).isNotNull();
            assertThat(result.getTitle()).isEqualTo("First Book");
        }

        @Test
        @DisplayName("Should return null when no results")
        void fetchTopMetadata_noResults_returnsNull() {
            Book book = Book.builder().title("Nonexistent").build();
            FetchMetadataRequest request = FetchMetadataRequest.builder()
                    .title("Nonexistent")
                    .build();

            when(hardcoverBookSearchService.searchBooks(anyString()))
                    .thenReturn(Collections.emptyList());

            BookMetadata result = parser.fetchTopMetadata(book, request);

            assertThat(result).isNull();
        }
    }

    private GraphQLResponse.Hit createHitWithAuthor(String title, String author) {
        GraphQLResponse.Document doc = new GraphQLResponse.Document();
        doc.setTitle(title);
        doc.setSlug(title.toLowerCase().replace(" ", "-"));
        doc.setAuthorNames(Set.of(author));
        doc.setId(String.valueOf(new Random().nextInt(100000)));
        
        GraphQLResponse.Hit hit = new GraphQLResponse.Hit();
        hit.setDocument(doc);
        return hit;
    }

    private GraphQLResponse.Hit createFullyPopulatedHit() {
        GraphQLResponse.Document doc = new GraphQLResponse.Document();
        doc.setId("12345");
        doc.setSlug("test-book-slug");
        doc.setTitle("Test Book");
        doc.setSubtitle("A Subtitle");
        doc.setDescription("A description");
        doc.setAuthorNames(Set.of("Test Author"));
        doc.setRating(4.25);
        doc.setRatingsCount(100);
        doc.setPages(350);
        doc.setReleaseDate("2023-01-15");
        doc.setIsbns(List.of("9781111111113", "1111111111", "9781234567897", "123456789X", "9791111111112"));
        doc.setGenres(List.of("Fiction", "Fantasy"));
        doc.setMoods(List.of("adventurous", "exciting"));
        doc.setTags(List.of("Epic"));
        
        // Series info
        GraphQLResponse.Series series = new GraphQLResponse.Series();
        series.setName("Test Series");
        series.setBooksCount(5);
        
        GraphQLResponse.FeaturedSeries featuredSeries = new GraphQLResponse.FeaturedSeries();
        featuredSeries.setSeries(series);
        featuredSeries.setPosition(2);
        doc.setFeaturedSeries(featuredSeries);
        
        // Image
        GraphQLResponse.Image image = new GraphQLResponse.Image();
        image.setUrl("https://example.com/cover.jpg");
        doc.setImage(image);
        
        GraphQLResponse.Hit hit = new GraphQLResponse.Hit();
        hit.setDocument(doc);
        return hit;
    }

    private HardcoverBookDetails createBookDetailsWithMoodCounts() {
        HardcoverBookDetails details = new HardcoverBookDetails();
        details.setId(12345);
        details.setTitle("Test Book");
        
        Map<String, List<HardcoverBookDetails.CachedTag>> cachedTags = new HashMap<>();
        
        List<HardcoverBookDetails.CachedTag> moods = new ArrayList<>();
        moods.add(createCachedTag("sad", 15));
        moods.add(createCachedTag("dark", 12));
        moods.add(createCachedTag("emotional", 8));
        moods.add(createCachedTag("funny", 2));  // Low count, should be filtered
        cachedTags.put("Mood", moods);
        
        List<HardcoverBookDetails.CachedTag> genres = new ArrayList<>();
        genres.add(createCachedTag("Fiction", 10));
        genres.add(createCachedTag("Drama", 8));
        cachedTags.put("Genre", genres);
        
        details.setCachedTags(cachedTags);
        return details;
    }

    private HardcoverBookDetails.CachedTag createCachedTag(String tag, int count) {
        HardcoverBookDetails.CachedTag cachedTag = new HardcoverBookDetails.CachedTag();
        cachedTag.setTag(tag);
        cachedTag.setCount(count);
        return cachedTag;
    }
}
