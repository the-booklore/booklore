package org.booklore.mapper;

import org.booklore.model.dto.BookMetadata;
import org.booklore.model.dto.CoverImage;
import org.booklore.model.dto.external.ExternalBookMetadata;
import org.booklore.model.dto.external.ExternalCoverImage;
import org.booklore.model.dto.external.ExternalSeriesInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class ExternalMetadataMapperTest {

    private ExternalMetadataMapper mapper;

    @BeforeEach
    void setUp() {
        mapper = new ExternalMetadataMapper();
    }

    @Nested
    @DisplayName("Series Selection Heuristics")
    class SeriesSelectionTests {

        @Test
        @DisplayName("Single series is always selected regardless of book title")
        void pickBestSeries_singleEntry_alwaysSelected() {
            ExternalSeriesInfo series = ExternalSeriesInfo.builder()
                    .name("The Expanse").number(3f).total(9).build();

            ExternalSeriesInfo result = mapper.pickBestSeries(List.of(series), "Completely Unrelated Title");

            assertThat(result).isSameAs(series);
        }

        @Test
        @DisplayName("Prefers series whose name is a substring of the book title")
        void pickBestSeries_multipleEntries_prefersSubstringMatch() {
            ExternalSeriesInfo expanse = ExternalSeriesInfo.builder()
                    .name("The Expanse").number(1f).build();
            ExternalSeriesInfo otherSeries = ExternalSeriesInfo.builder()
                    .name("Rocinante Files").number(1f).build();

            ExternalSeriesInfo result = mapper.pickBestSeries(
                    List.of(otherSeries, expanse),
                    "The Expanse: Leviathan Wakes"
            );

            assertThat(result.getName()).isEqualTo("The Expanse");
        }

        @Test
        @DisplayName("When book title is contained within a series name, that series scores higher than unrelated ones")
        void pickBestSeries_titleInsideSeriesName_scoresHigher() {
            ExternalSeriesInfo broad = ExternalSeriesInfo.builder()
                    .name("Harry Potter and the Wizarding World").number(1f).build();
            ExternalSeriesInfo unrelated = ExternalSeriesInfo.builder()
                    .name("ABC").number(1f).build();

            ExternalSeriesInfo result = mapper.pickBestSeries(
                    List.of(unrelated, broad),
                    "Harry Potter"
            );

            assertThat(result.getName()).isEqualTo("Harry Potter and the Wizarding World");
        }

        @Test
        @DisplayName("Falls back to first series when no title is provided")
        void pickBestSeries_nullTitle_fallsToFirst() {
            ExternalSeriesInfo first = ExternalSeriesInfo.builder().name("Series A").build();
            ExternalSeriesInfo second = ExternalSeriesInfo.builder().name("Series B").build();

            ExternalSeriesInfo result = mapper.pickBestSeries(List.of(first, second), null);

            assertThat(result).isSameAs(first);
        }

        @Test
        @DisplayName("Falls back to first series when title is blank")
        void pickBestSeries_blankTitle_fallsToFirst() {
            ExternalSeriesInfo first = ExternalSeriesInfo.builder().name("Series A").build();
            ExternalSeriesInfo second = ExternalSeriesInfo.builder().name("Series B").build();

            ExternalSeriesInfo result = mapper.pickBestSeries(List.of(first, second), "   ");

            assertThat(result).isSameAs(first);
        }

        @Test
        @DisplayName("Skips series entries with null names during scoring")
        void pickBestSeries_seriesWithNullName_skippedDuringScoringFallsToFirst() {
            ExternalSeriesInfo nullName = ExternalSeriesInfo.builder().name(null).number(1f).build();
            ExternalSeriesInfo valid = ExternalSeriesInfo.builder().name("Matching Series").number(2f).build();

            ExternalSeriesInfo result = mapper.pickBestSeries(
                    List.of(nullName, valid),
                    "Matching Series Book 2"
            );

            assertThat(result.getName()).isEqualTo("Matching Series");
        }

        @Test
        @DisplayName("Uses longest common substring when neither name is a substring of the other")
        void pickBestSeries_partialOverlap_usesLongestCommonSubstring() {
            // "Dark Tower" shares 10 chars with "The Dark Tower Saga" title
            // "Gunslinger" shares fewer chars with that title
            ExternalSeriesInfo darkTower = ExternalSeriesInfo.builder()
                    .name("Dark Tower Collection").number(1f).build();
            ExternalSeriesInfo gunslinger = ExternalSeriesInfo.builder()
                    .name("XYZ Unrelated").number(1f).build();

            ExternalSeriesInfo result = mapper.pickBestSeries(
                    List.of(gunslinger, darkTower),
                    "The Dark Tower Saga"
            );

            assertThat(result.getName()).isEqualTo("Dark Tower Collection");
        }
    }

    @Nested
    @DisplayName("Date Parsing")
    class DateParsingTests {

        @Test
        @DisplayName("Parses full ISO date correctly")
        void toBookMetadata_fullIsoDate_parsedCorrectly() {
            ExternalBookMetadata external = ExternalBookMetadata.builder()
                    .title("Test").publishedDate("2023-06-15").build();

            BookMetadata result = mapper.toBookMetadata(external, null);

            assertThat(result.getPublishedDate()).isEqualTo(LocalDate.of(2023, 6, 15));
        }

        @Test
        @DisplayName("Pads year-only date to January 1st")
        void toBookMetadata_yearOnly_paddedToJanFirst() {
            ExternalBookMetadata external = ExternalBookMetadata.builder()
                    .title("Test").publishedDate("2010").build();

            BookMetadata result = mapper.toBookMetadata(external, null);

            assertThat(result.getPublishedDate()).isEqualTo(LocalDate.of(2010, 1, 1));
        }

        @Test
        @DisplayName("Pads year-month date to first of month")
        void toBookMetadata_yearMonth_paddedToFirstOfMonth() {
            ExternalBookMetadata external = ExternalBookMetadata.builder()
                    .title("Test").publishedDate("2010-08").build();

            BookMetadata result = mapper.toBookMetadata(external, null);

            assertThat(result.getPublishedDate()).isEqualTo(LocalDate.of(2010, 8, 1));
        }

        @Test
        @DisplayName("Returns null for garbage date strings instead of throwing")
        void toBookMetadata_unparsableDate_returnsNullDate() {
            ExternalBookMetadata external = ExternalBookMetadata.builder()
                    .title("Test").publishedDate("not-a-date").build();

            BookMetadata result = mapper.toBookMetadata(external, null);

            assertThat(result.getPublishedDate()).isNull();
        }

        @Test
        @DisplayName("Trims whitespace around date before parsing")
        void toBookMetadata_dateWithWhitespace_trimmedAndParsed() {
            ExternalBookMetadata external = ExternalBookMetadata.builder()
                    .title("Test").publishedDate("  2020-03-14  ").build();

            BookMetadata result = mapper.toBookMetadata(external, null);

            assertThat(result.getPublishedDate()).isEqualTo(LocalDate.of(2020, 3, 14));
        }
    }

    @Nested
    @DisplayName("Metadata Field Mapping")
    class MetadataFieldMappingTests {

        @Test
        @DisplayName("Maps all scalar fields from external metadata to internal DTO")
        void toBookMetadata_allFieldsPopulated_mappedCorrectly() {
            ExternalBookMetadata external = ExternalBookMetadata.builder()
                    .title("Dune")
                    .subtitle("Book One")
                    .publisher("Ace")
                    .publishedDate("1965-08-01")
                    .description("A desert planet story")
                    .pageCount(412)
                    .language("en")
                    .isbn13("9780441172719")
                    .isbn10("0441172717")
                    .asin("B00B7NPRY8")
                    .coverUrl("https://example.com/cover.jpg")
                    .rating(4.5)
                    .authors(List.of("Frank Herbert"))
                    .categories(List.of("Science Fiction", "Fantasy"))
                    .moods(List.of("Epic", "Atmospheric"))
                    .tags(List.of("desert", "politics"))
                    .build();

            BookMetadata result = mapper.toBookMetadata(external, "Dune");

            assertThat(result.getTitle()).isEqualTo("Dune");
            assertThat(result.getSubtitle()).isEqualTo("Book One");
            assertThat(result.getPublisher()).isEqualTo("Ace");
            assertThat(result.getPageCount()).isEqualTo(412);
            assertThat(result.getLanguage()).isEqualTo("en");
            assertThat(result.getIsbn13()).isEqualTo("9780441172719");
            assertThat(result.getIsbn10()).isEqualTo("0441172717");
            assertThat(result.getAsin()).isEqualTo("B00B7NPRY8");
            assertThat(result.getThumbnailUrl()).isEqualTo("https://example.com/cover.jpg");
            assertThat(result.getRating()).isEqualTo(4.5);
            assertThat(result.getAuthors()).containsExactly("Frank Herbert");
            assertThat(result.getCategories()).containsExactlyInAnyOrder("Science Fiction", "Fantasy");
            assertThat(result.getMoods()).containsExactlyInAnyOrder("Epic", "Atmospheric");
            assertThat(result.getTags()).containsExactlyInAnyOrder("desert", "politics");
        }

        @Test
        @DisplayName("Lists are converted to Sets to deduplicate")
        void toBookMetadata_duplicateAuthors_deduplicatedInSet() {
            ExternalBookMetadata external = ExternalBookMetadata.builder()
                    .title("Test")
                    .authors(List.of("Author A", "Author A", "Author B"))
                    .build();

            BookMetadata result = mapper.toBookMetadata(external, null);

            assertThat(result.getAuthors()).hasSize(2);
            assertThat(result.getAuthors()).containsExactlyInAnyOrder("Author A", "Author B");
        }

        @Test
        @DisplayName("Series fields are mapped when single series is present")
        void toBookMetadata_withSeries_seriesFieldsMapped() {
            ExternalSeriesInfo series = ExternalSeriesInfo.builder()
                    .name("Wheel of Time").number(4f).total(14).build();
            ExternalBookMetadata external = ExternalBookMetadata.builder()
                    .title("The Shadow Rising")
                    .series(List.of(series))
                    .build();

            BookMetadata result = mapper.toBookMetadata(external, "The Shadow Rising");

            assertThat(result.getSeriesName()).isEqualTo("Wheel of Time");
            assertThat(result.getSeriesNumber()).isEqualTo(4f);
            assertThat(result.getSeriesTotal()).isEqualTo(14);
        }

        @Test
        @DisplayName("externalUrl is always set to null on mapped metadata")
        void toBookMetadata_externalUrlAlwaysNull() {
            ExternalBookMetadata external = ExternalBookMetadata.builder()
                    .title("Test").build();

            BookMetadata result = mapper.toBookMetadata(external, null);

            // The mapper doesn't set externalUrl — it's left at builder default (null)
            assertThat(result.getExternalUrl()).isNull();
        }

        @Test
        @DisplayName("Null collection fields result in null on internal DTO (not empty sets)")
        void toBookMetadata_nullCollections_stayNull() {
            ExternalBookMetadata external = ExternalBookMetadata.builder()
                    .title("Test")
                    .authors(null)
                    .categories(null)
                    .moods(null)
                    .tags(null)
                    .build();

            BookMetadata result = mapper.toBookMetadata(external, null);

            assertThat(result.getAuthors()).isNull();
            assertThat(result.getMoods()).isNull();
            assertThat(result.getTags()).isNull();
        }
    }

    @Nested
    @DisplayName("Cover Image Mapping")
    class CoverImageMappingTests {

        @Test
        @DisplayName("Maps cover image URL, dimensions, and index correctly")
        void toCoverImage_populatedFields_mappedCorrectly() {
            ExternalCoverImage external = ExternalCoverImage.builder()
                    .url("https://example.com/cover.jpg")
                    .width(600)
                    .height(900)
                    .build();

            CoverImage result = mapper.toCoverImage(external, 3);

            assertThat(result.getUrl()).isEqualTo("https://example.com/cover.jpg");
            assertThat(result.getWidth()).isEqualTo(600);
            assertThat(result.getHeight()).isEqualTo(900);
            assertThat(result.getIndex()).isEqualTo(3);
        }

        @Test
        @DisplayName("Null width/height default to 0")
        void toCoverImage_nullDimensions_defaultToZero() {
            ExternalCoverImage external = ExternalCoverImage.builder()
                    .url("https://example.com/cover.jpg")
                    .width(null)
                    .height(null)
                    .build();

            CoverImage result = mapper.toCoverImage(external, 0);

            assertThat(result.getWidth()).isZero();
            assertThat(result.getHeight()).isZero();
        }

        @Test
        @DisplayName("Null external cover image returns null")
        void toCoverImage_nullInput_returnsNull() {
            CoverImage result = mapper.toCoverImage(null, 0);

            assertThat(result).isNull();
        }
    }
}
