package org.booklore.mobile.controller;

import org.booklore.mobile.dto.MobileFilterOptions;
import org.booklore.mobile.service.MobileBookService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MobileFilterControllerTest {

    @Mock
    private MobileBookService mobileBookService;

    @InjectMocks
    private MobileFilterController controller;

    @Test
    void getFilterOptions_noParams_delegatesWithNulls() {
        MobileFilterOptions expected = buildOptions(List.of("Author A"), List.of("EPUB"), List.of("en"));
        when(mobileBookService.getFilterOptions(null, null, null)).thenReturn(expected);

        ResponseEntity<MobileFilterOptions> response = controller.getFilterOptions(null, null, null);

        assertEquals(200, response.getStatusCode().value());
        assertSame(expected, response.getBody());
        verify(mobileBookService).getFilterOptions(null, null, null);
    }

    @Test
    void getFilterOptions_withLibraryId_passesLibraryId() {
        MobileFilterOptions expected = buildOptions(List.of("Author B"), List.of("PDF"), List.of("fr"));
        when(mobileBookService.getFilterOptions(5L, null, null)).thenReturn(expected);

        ResponseEntity<MobileFilterOptions> response = controller.getFilterOptions(5L, null, null);

        assertEquals(200, response.getStatusCode().value());
        assertEquals(1, response.getBody().getAuthors().size());
        assertEquals("Author B", response.getBody().getAuthors().getFirst().getName());
        verify(mobileBookService).getFilterOptions(5L, null, null);
    }

    @Test
    void getFilterOptions_withShelfId_passesShelfId() {
        MobileFilterOptions expected = buildOptions(List.of(), List.of("EPUB"), List.of());
        when(mobileBookService.getFilterOptions(null, 10L, null)).thenReturn(expected);

        ResponseEntity<MobileFilterOptions> response = controller.getFilterOptions(null, 10L, null);

        assertEquals(200, response.getStatusCode().value());
        assertTrue(response.getBody().getAuthors().isEmpty());
        verify(mobileBookService).getFilterOptions(null, 10L, null);
    }

    @Test
    void getFilterOptions_withMagicShelfId_passesMagicShelfId() {
        MobileFilterOptions expected = buildOptions(List.of("Author C"), List.of("MOBI"), List.of("de"));
        when(mobileBookService.getFilterOptions(null, null, 7L)).thenReturn(expected);

        ResponseEntity<MobileFilterOptions> response = controller.getFilterOptions(null, null, 7L);

        assertEquals(200, response.getStatusCode().value());
        assertEquals("Author C", response.getBody().getAuthors().getFirst().getName());
        verify(mobileBookService).getFilterOptions(null, null, 7L);
    }

    private MobileFilterOptions buildOptions(List<String> authorNames, List<String> fileTypes, List<String> langCodes) {
        List<MobileFilterOptions.AuthorOption> authors = authorNames.stream()
                .map(name -> MobileFilterOptions.AuthorOption.builder().name(name).count(1L).build())
                .toList();
        List<MobileFilterOptions.LanguageOption> languages = langCodes.stream()
                .map(code -> MobileFilterOptions.LanguageOption.builder().code(code).label(code).count(1L).build())
                .toList();
        return MobileFilterOptions.builder()
                .authors(authors)
                .languages(languages)
                .fileTypes(fileTypes)
                .readStatuses(List.of("READ", "READING", "UNREAD"))
                .build();
    }
}
