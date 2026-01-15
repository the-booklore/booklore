package com.adityachandel.booklore.service.metadata.parser;

import com.adityachandel.booklore.model.dto.BookMetadata;
import com.adityachandel.booklore.service.appsettings.AppSettingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for the metadata selection scoring logic in AmazonBookParser.
 * Tests the ability to correctly distinguish between manga and light novel versions
 * of the same series, as well as volume number matching.
 */
class AmazonBookParserScoringTest {

    @Mock
    private AppSettingService appSettingService;

    private AmazonBookParser parser;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        parser = new AmazonBookParser(appSettingService);
    }

    @Test
    @DisplayName("Should select manga when query contains 'Manga'")
    void shouldSelectMangaOverLightNovel() {
        // Given
        String query = "The Apothecary Diaries Manga, Vol. 03";

        BookMetadata manga = BookMetadata.builder()
                .title("The Apothecary Diaries 03 (Manga)")
                .categories(Set.of("Manga", "Comics & Graphic Novels"))
                .build();

        BookMetadata lightNovel = BookMetadata.builder()
                .title("The Apothecary Diaries: Volume 3 (Light Novel)")
                .categories(Set.of("Light Novels", "Fantasy"))
                .build();

        // Light novel comes first in the list (simulating Amazon's return order)
        List<BookMetadata> candidates = List.of(lightNovel, manga);

        // When
        BookMetadata result = parser.selectBestMatch(candidates, query);

        // Then
        assertNotNull(result);
        assertTrue(result.getTitle().contains("Manga"),
                "Should select the manga version, got: " + result.getTitle());
    }

    @Test
    @DisplayName("Should select light novel when query contains 'Light Novel'")
    void shouldSelectLightNovelOverManga() {
        // Given
        String query = "The Apothecary Diaries Light Novel Vol 3";

        BookMetadata manga = BookMetadata.builder()
                .title("The Apothecary Diaries 03 (Manga)")
                .categories(Set.of("Manga"))
                .build();

        BookMetadata lightNovel = BookMetadata.builder()
                .title("The Apothecary Diaries: Volume 3 (Light Novel)")
                .categories(Set.of("Light Novels"))
                .build();

        // Manga comes first in the list
        List<BookMetadata> candidates = List.of(manga, lightNovel);

        // When
        BookMetadata result = parser.selectBestMatch(candidates, query);

        // Then
        assertNotNull(result);
        assertTrue(result.getTitle().contains("Light Novel"),
                "Should select the light novel version, got: " + result.getTitle());
    }

    @Test
    @DisplayName("Should detect manga from categories even if not in title")
    void shouldDetectMangaFromCategories() {
        // Given
        String query = "Overlord Manga Vol 1";

        BookMetadata mangaWithCategory = BookMetadata.builder()
                .title("Overlord, Vol. 1")
                .categories(Set.of("Manga", "Action & Adventure Manga"))
                .build();

        BookMetadata lightNovelWithCategory = BookMetadata.builder()
                .title("Overlord, Vol. 1")
                .categories(Set.of("Light Novels", "Fantasy"))
                .build();

        List<BookMetadata> candidates = List.of(lightNovelWithCategory, mangaWithCategory);

        // When
        BookMetadata result = parser.selectBestMatch(candidates, query);

        // Then
        assertNotNull(result);
        assertTrue(result.getCategories().stream().anyMatch(c -> c.toLowerCase().contains("manga")),
                "Should select the version with Manga category");
    }

    @Test
    @DisplayName("Should match correct volume number")
    void shouldMatchVolumeNumber() {
        // Given
        String query = "Series Name Vol 03";

        BookMetadata vol1 = BookMetadata.builder()
                .title("Series Name 01")
                .categories(Set.of())
                .build();

        BookMetadata vol3 = BookMetadata.builder()
                .title("Series Name 03")
                .categories(Set.of())
                .build();

        // Vol 1 comes first
        List<BookMetadata> candidates = List.of(vol1, vol3);

        // When
        BookMetadata result = parser.selectBestMatch(candidates, query);

        // Then
        assertNotNull(result);
        assertTrue(result.getTitle().contains("03"),
                "Should select volume 3, got: " + result.getTitle());
    }

    @Test
    @DisplayName("Should handle various volume number formats")
    void shouldHandleVariousVolumeFormats() {
        // Given - "volume 3" format in query
        String query = "Series Name Volume 3";

        BookMetadata vol1 = BookMetadata.builder()
                .title("Series Name Vol. 1")
                .categories(Set.of())
                .build();

        BookMetadata vol3 = BookMetadata.builder()
                .title("Series Name Vol. 3")
                .categories(Set.of())
                .build();

        List<BookMetadata> candidates = List.of(vol1, vol3);

        // When
        BookMetadata result = parser.selectBestMatch(candidates, query);

        // Then
        assertNotNull(result);
        assertTrue(result.getTitle().contains("3"),
                "Should match volume 3 despite different format, got: " + result.getTitle());
    }

    @Test
    @DisplayName("Should penalize wrong format match")
    void shouldPenalizeWrongFormatMatch() {
        // Given - query asks for manga, but first result is light novel
        String query = "Sword Art Online Manga Vol 1";

        BookMetadata lightNovel = BookMetadata.builder()
                .title("Sword Art Online 1 (Light Novel)")
                .categories(Set.of("Light Novels"))
                .build();

        BookMetadata manga = BookMetadata.builder()
                .title("Sword Art Online: Aincrad 01 (Manga)")
                .categories(Set.of("Manga"))
                .build();

        List<BookMetadata> candidates = List.of(lightNovel, manga);

        // When
        int lightNovelScore = parser.scoreResult(lightNovel, query);
        int mangaScore = parser.scoreResult(manga, query);

        // Then
        assertTrue(mangaScore > lightNovelScore,
                "Manga score (" + mangaScore + ") should be higher than light novel score (" + lightNovelScore + ")");
    }

    @Test
    @DisplayName("Should handle null categories gracefully")
    void shouldHandleNullCategories() {
        // Given
        String query = "Some Book Manga";

        BookMetadata withNullCategories = BookMetadata.builder()
                .title("Some Book (Manga)")
                .categories(null)
                .build();

        List<BookMetadata> candidates = List.of(withNullCategories);

        // When & Then - should not throw
        assertDoesNotThrow(() -> parser.selectBestMatch(candidates, query));
        assertDoesNotThrow(() -> parser.scoreResult(withNullCategories, query));
    }

    @Test
    @DisplayName("Should handle empty candidate list")
    void shouldHandleEmptyCandidates() {
        // When
        BookMetadata result = parser.selectBestMatch(List.of(), "query");

        // Then
        assertNull(result);
    }

    @Test
    @DisplayName("Should handle null candidate list")
    void shouldHandleNullCandidates() {
        // When
        BookMetadata result = parser.selectBestMatch(null, "query");

        // Then
        assertNull(result);
    }

    @Test
    @DisplayName("Should return single candidate without scoring")
    void shouldReturnSingleCandidate() {
        // Given
        BookMetadata single = BookMetadata.builder()
                .title("Only Book")
                .build();

        // When
        BookMetadata result = parser.selectBestMatch(List.of(single), "query");

        // Then
        assertNotNull(result);
        assertEquals("Only Book", result.getTitle());
    }

    @Test
    @DisplayName("Should prefer title word overlap when no format keywords")
    void shouldPreferTitleWordOverlapWithoutFormatKeywords() {
        // Given - no format keywords, should use title similarity
        String query = "The Apothecary Diaries Vol 3";

        BookMetadata exactMatch = BookMetadata.builder()
                .title("The Apothecary Diaries: Volume 3")
                .categories(Set.of())
                .build();

        BookMetadata partialMatch = BookMetadata.builder()
                .title("Apothecary Stories Vol 3")
                .categories(Set.of())
                .build();

        List<BookMetadata> candidates = List.of(partialMatch, exactMatch);

        // When
        BookMetadata result = parser.selectBestMatch(candidates, query);

        // Then
        assertNotNull(result);
        assertTrue(result.getTitle().contains("Apothecary Diaries"),
                "Should prefer better title match, got: " + result.getTitle());
    }

    @Test
    @DisplayName("Score calculation should match expected values for manga vs light novel")
    void scoreCalculationShouldMatchExpectedValues() {
        // Given - The Apothecary Diaries example from the original issue
        String query = "The Apothecary Diaries Manga, Vol. 03";

        BookMetadata manga = BookMetadata.builder()
                .title("The Apothecary Diaries 03 (Manga)")
                .categories(Set.of("Manga"))
                .build();

        BookMetadata lightNovel = BookMetadata.builder()
                .title("The Apothecary Diaries: Volume 3")
                .categories(Set.of("Light Novels"))
                .build();

        // When
        int mangaScore = parser.scoreResult(manga, query);
        int lightNovelScore = parser.scoreResult(lightNovel, query);

        // Then
        // Manga should get: +100 (format match) + 50 (volume match) + title overlap
        // Light Novel should get: -50 (format mismatch) + 50 (volume match) + title overlap
        assertTrue(mangaScore >= 150, "Manga should score at least 150, got: " + mangaScore);
        assertTrue(lightNovelScore < mangaScore, "Light novel score should be lower than manga");
        assertTrue(mangaScore - lightNovelScore >= 100,
                "Difference should be at least 100 (format bonus vs penalty), got: " + (mangaScore - lightNovelScore));
    }
}
