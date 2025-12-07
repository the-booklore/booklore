package com.adityachandel.booklore.model.entity;

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
}
