package com.adityachandel.booklore.model.entity;

import com.adityachandel.booklore.util.BookUtils;
import org.junit.jupiter.api.Test;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;

class BookMetadataEntityTest {

    @Test
    void updateSearchText_populatesSearchText() {
        BookMetadataEntity metadata = new BookMetadataEntity();
        metadata.setTitle("Jo Nesbø Book");
        metadata.setSubtitle("Murder Mystery");
        metadata.setAuthors(Set.of(AuthorEntity.builder().name("Jo Nesbø").build()));

        metadata.updateSearchText();

        String searchText = metadata.getSearchText();
        assertNotNull(searchText);
        assertTrue(searchText.contains("jo nesbo book"));
        assertTrue(searchText.contains("murder mystery"));
        assertTrue(searchText.contains("jo nesbo"));
    }

    @Test
    void updateSearchText_normalizesAuthorWithDiacritics() {
        BookMetadataEntity metadata = new BookMetadataEntity();
        metadata.setTitle("The Snowman");
        metadata.setAuthors(Set.of(AuthorEntity.builder().name("Jo Nesbø").build()));

        metadata.updateSearchText();

        String searchText = metadata.getSearchText();
        assertNotNull(searchText);
        // Verify that 'ø' is normalized to 'o'
        assertTrue(searchText.contains("nesbo"), "Should contain 'nesbo': " + searchText);
        assertFalse(searchText.contains("ø"), "Should not contain 'ø': " + searchText);
    }

    @Test
    void updateSearchText_handlesFrenchGermanAndSpanishDiacritics() {
        BookMetadataEntity metadata = new BookMetadataEntity();
        metadata.setTitle("Müller's Café");
        metadata.setSubtitle("À la française");
        metadata.setSeriesName("José's Stories");
        metadata.setAuthors(Set.of(
            AuthorEntity.builder().name("François Müller").build(),
            AuthorEntity.builder().name("José García").build()
        ));

        metadata.updateSearchText();

        String searchText = metadata.getSearchText();
        assertNotNull(searchText);
        
        assertTrue(searchText.contains("muller"), "Should contain 'muller': " + searchText);
        assertTrue(searchText.contains("cafe"), "Should contain 'cafe': " + searchText);
        assertTrue(searchText.contains("a la francaise"), "Should contain 'a la francaise': " + searchText);
        assertTrue(searchText.contains("jose"), "Should contain 'jose': " + searchText);
        assertTrue(searchText.contains("garcia"), "Should contain 'garcia': " + searchText);
        assertTrue(searchText.contains("francois muller"), "Should contain 'francois muller': " + searchText);
        
        assertFalse(searchText.contains("ü"), "Should not contain 'ü': " + searchText);
        assertFalse(searchText.contains("é"), "Should not contain 'é': " + searchText);
        assertFalse(searchText.contains("à"), "Should not contain 'à': " + searchText);
        assertFalse(searchText.contains("í"), "Should not contain 'í': " + searchText);
    }

    @Test
    void searchSimulation_withDiacritics() {
        BookMetadataEntity metadata = new BookMetadataEntity();
        metadata.setTitle("The Bat");
        metadata.setAuthors(Set.of(AuthorEntity.builder().name("Jo Nesbø").build()));
        metadata.updateSearchText();
        
        String storedSearchText = metadata.getSearchText();
        
        String searchQuery1 = BookUtils.normalizeForSearch("nesbo"); // without ø
        String searchQuery2 = BookUtils.normalizeForSearch("Nesbø"); // with ø
        String searchQuery3 = BookUtils.normalizeForSearch("NESBO"); // uppercase
        String searchQuery4 = BookUtils.normalizeForSearch("Jo Nesbø"); // full name with ø
        
        assertEquals(searchQuery1, searchQuery2, "Queries with and without diacritics should match");
        assertEquals(searchQuery1, searchQuery3, "Case should not matter");
        
        // Simulate LIKE '%query%' - all searches should find the book
        assertTrue(storedSearchText.contains(searchQuery1), 
            "Search 'nesbo' should match stored text: " + storedSearchText);
        assertTrue(storedSearchText.contains(searchQuery2), 
            "Search 'Nesbø' should match stored text: " + storedSearchText);
        assertTrue(storedSearchText.contains(searchQuery3), 
            "Search 'NESBO' should match stored text: " + storedSearchText);
        assertTrue(storedSearchText.contains(searchQuery4), 
            "Search 'Jo Nesbø' should match stored text: " + storedSearchText);
    }
}
