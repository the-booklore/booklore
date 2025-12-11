package com.adityachandel.booklore.service.opds;

import com.adityachandel.booklore.config.security.service.AuthenticationService;
import com.adityachandel.booklore.config.security.userdetails.OpdsUserDetails;
import com.adityachandel.booklore.model.dto.Book;
import com.adityachandel.booklore.model.dto.BookMetadata;
import com.adityachandel.booklore.model.dto.Library;
import com.adityachandel.booklore.model.dto.OpdsUserV2;
import com.adityachandel.booklore.model.entity.ShelfEntity;
import com.adityachandel.booklore.model.enums.BookFileType;
import com.adityachandel.booklore.model.enums.OpdsSortOrder;
import com.adityachandel.booklore.service.MagicShelfService;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class OpdsFeedServiceTest {

    private static final Instant FIXED_INSTANT = Instant.parse("2025-01-01T12:00:00Z");
    private static final Long TEST_USER_ID = 42L;

    private AuthenticationService authenticationService;
    private OpdsBookService opdsBookService;
    private MagicShelfService magicShelfService;
    private MagicShelfBookService magicShelfBookService;
    private OpdsFeedService opdsFeedService;
    private HttpServletRequest request;

    @BeforeEach
    void setUp() {
        authenticationService = mock(AuthenticationService.class);
        opdsBookService = mock(OpdsBookService.class);
        magicShelfService = mock(MagicShelfService.class);
        magicShelfBookService = mock(MagicShelfBookService.class);
        opdsFeedService = new OpdsFeedService(authenticationService, opdsBookService, magicShelfService, magicShelfBookService);
        request = mock(HttpServletRequest.class);
    }

    private OpdsUserDetails mockAuthenticatedUser() {
        OpdsUserDetails userDetails = mock(OpdsUserDetails.class);
        OpdsUserV2 v2 = mock(OpdsUserV2.class);
        when(userDetails.getOpdsUserV2()).thenReturn(v2);
        when(v2.getUserId()).thenReturn(TEST_USER_ID);
        when(v2.getSortOrder()).thenReturn(OpdsSortOrder.RECENT);
        when(authenticationService.getOpdsUser()).thenReturn(userDetails);
        return userDetails;
    }

    @Test
    void generateRootNavigation_shouldContainAllSections() {
        String xml = opdsFeedService.generateRootNavigation(request);
        assertThat(xml).contains("All Books");
        assertThat(xml).contains("Recently Added");
        assertThat(xml).contains("Libraries");
        assertThat(xml).contains("Shelves");
        assertThat(xml).contains("Magic Shelves");
        assertThat(xml).contains("Surprise Me");
        assertThat(xml).contains("<?xml");
        assertThat(xml).contains("</feed>");
    }

    @Test
    void generateLibrariesNavigation_shouldListLibraries() {
        mockAuthenticatedUser();

        Library lib = Library.builder().id(1L).name("Test Library").watch(false).build();
        when(opdsBookService.getAccessibleLibraries(TEST_USER_ID)).thenReturn(List.of(lib));

        String xml = opdsFeedService.generateLibrariesNavigation(request);
        assertThat(xml).contains("Test Library");
        assertThat(xml).contains("urn:booklore:library:1");
        assertThat(xml).contains("</feed>");
        verify(opdsBookService).getAccessibleLibraries(TEST_USER_ID);
    }

    @Test
    void generateLibrariesNavigation_shouldHandleNoLibraries() {
        mockAuthenticatedUser();
        when(opdsBookService.getAccessibleLibraries(TEST_USER_ID)).thenReturn(Collections.emptyList());

        String xml = opdsFeedService.generateLibrariesNavigation(request);
        assertThat(xml).contains("</feed>");
    }

    @Test
    void generateShelvesNavigation_shouldListShelves() {
        mockAuthenticatedUser();

        ShelfEntity shelfEntity = ShelfEntity.builder().id(5L).name("Favorites").build();
        when(opdsBookService.getUserShelves(TEST_USER_ID)).thenReturn(Collections.singletonList(shelfEntity));

        String xml = opdsFeedService.generateShelvesNavigation(request);
        assertThat(xml).contains("Favorites");
        assertThat(xml).contains("urn:booklore:shelf:5");
        assertThat(xml).contains("</feed>");
        verify(opdsBookService).getUserShelves(TEST_USER_ID);
    }

    @Test
    void generateShelvesNavigation_shouldHandleNoShelves() {
        mockAuthenticatedUser();
        when(opdsBookService.getUserShelves(TEST_USER_ID)).thenReturn(Collections.emptyList());

        String xml = opdsFeedService.generateShelvesNavigation(request);
        assertThat(xml).contains("</feed>");
    }

    @Test
    void generateShelvesNavigation_shouldHandleNullUserDetails() {
        when(authenticationService.getOpdsUser()).thenReturn(null);
        String xml = opdsFeedService.generateShelvesNavigation(request);
        assertThat(xml).contains("</feed>");
        verify(opdsBookService, never()).getUserShelves(any());
    }

    @Test
    void generateCatalogFeed_shouldReturnFeedWithBooks() {
        mockAuthenticatedUser();

        when(request.getParameter("libraryId")).thenReturn(null);
        when(request.getParameter("shelfId")).thenReturn(null);
        when(request.getParameter("magicShelfId")).thenReturn(null);
        when(request.getParameter("q")).thenReturn(null);
        when(request.getParameter("page")).thenReturn(null);
        when(request.getParameter("size")).thenReturn(null);
        when(request.getRequestURI()).thenReturn("/api/v1/opds/catalog");
        when(request.getQueryString()).thenReturn(null);

        Book book = Book.builder()
                .id(10L)
                .bookType(BookFileType.EPUB)
                .addedOn(FIXED_INSTANT)
                .metadata(BookMetadata.builder()
                        .title("Book Title")
                        .authors(Set.of("Author A"))
                        .publisher("Publisher X")
                        .language("en")
                        .categories(Set.of("Fiction"))
                        .description("A book description")
                        .isbn10("1234567890")
                        .build())
                .build();

        Page<Book> page = new PageImpl<>(List.of(book), PageRequest.of(0, 50), 1);
        when(opdsBookService.getBooksPage(eq(TEST_USER_ID), any(), any(), any(), eq(0), eq(50))).thenReturn(page);
        when(opdsBookService.applySortOrder(any(), any())).thenReturn(page);

        String xml = opdsFeedService.generateCatalogFeed(request);
        assertThat(xml).contains("Book Title");
        assertThat(xml).contains("Author A");
        assertThat(xml).contains("Publisher X");
        assertThat(xml).contains("urn:booklore:book:10");
        assertThat(xml).contains("application/epub+zip");
        assertThat(xml).contains("</feed>");
        verify(opdsBookService).getBooksPage(TEST_USER_ID, null, null, null, 0, 50);
    }

    @Test
    void generateCatalogFeed_shouldHandleEmptyPage() {
        mockAuthenticatedUser();

        when(request.getParameter(anyString())).thenReturn(null);
        when(request.getRequestURI()).thenReturn("/api/v1/opds/catalog");
        when(request.getQueryString()).thenReturn(null);

        Page<Book> page = new PageImpl<>(Collections.emptyList(), PageRequest.of(0, 50), 0);
        when(opdsBookService.getBooksPage(any(), any(), any(), any(), anyInt(), anyInt())).thenReturn(page);
        when(opdsBookService.applySortOrder(any(), any())).thenReturn(page);

        String xml = opdsFeedService.generateCatalogFeed(request);
        assertThat(xml).contains("</feed>");
    }

    @Test
    void generateRecentFeed_shouldReturnFeedWithBooks() {
        mockAuthenticatedUser();

        when(request.getParameter("page")).thenReturn(null);
        when(request.getParameter("size")).thenReturn(null);
        when(request.getRequestURI()).thenReturn("/api/v1/opds/recent");
        when(request.getQueryString()).thenReturn(null);

        Book book = Book.builder()
                .id(11L)
                .bookType(BookFileType.PDF)
                .addedOn(FIXED_INSTANT)
                .metadata(BookMetadata.builder().title("Recent Book").build())
                .build();

        Page<Book> page = new PageImpl<>(List.of(book), PageRequest.of(0, 50), 1);
        when(opdsBookService.getRecentBooksPage(eq(TEST_USER_ID), eq(0), eq(50))).thenReturn(page);
        when(opdsBookService.applySortOrder(any(), any())).thenReturn(page);

        String xml = opdsFeedService.generateRecentFeed(request);
        assertThat(xml).contains("Recent Book");
        assertThat(xml).contains("application/pdf");
        assertThat(xml).contains("</feed>");
        verify(opdsBookService).getRecentBooksPage(TEST_USER_ID, 0, 50);
    }

    @Test
    void generateRecentFeed_shouldHandleEmptyPage() {
        mockAuthenticatedUser();

        when(request.getParameter(anyString())).thenReturn(null);
        when(request.getRequestURI()).thenReturn("/api/v1/opds/recent");
        when(request.getQueryString()).thenReturn(null);

        Page<Book> page = new PageImpl<>(Collections.emptyList(), PageRequest.of(0, 50), 0);
        when(opdsBookService.getRecentBooksPage(any(), anyInt(), anyInt())).thenReturn(page);
        when(opdsBookService.applySortOrder(any(), any())).thenReturn(page);

        String xml = opdsFeedService.generateRecentFeed(request);
        assertThat(xml).contains("</feed>");
    }

    @Test
    void generateSurpriseFeed_shouldReturnFeedWithBooks() {
        mockAuthenticatedUser();

        Book book = Book.builder()
                .id(12L)
                .bookType(BookFileType.EPUB)
                .addedOn(FIXED_INSTANT)
                .metadata(BookMetadata.builder().title("Surprise Book").build())
                .build();

        when(opdsBookService.getRandomBooks(TEST_USER_ID, 25)).thenReturn(List.of(book));

        String xml = opdsFeedService.generateSurpriseFeed(request);
        assertThat(xml).contains("Surprise Book");
        assertThat(xml).contains("urn:booklore:book:12");
        assertThat(xml).contains("</feed>");
        verify(opdsBookService).getRandomBooks(TEST_USER_ID, 25);
    }

    @Test
    void generateSurpriseFeed_shouldHandleNoBooks() {
        mockAuthenticatedUser();
        when(opdsBookService.getRandomBooks(TEST_USER_ID, 25)).thenReturn(Collections.emptyList());

        String xml = opdsFeedService.generateSurpriseFeed(request);
        assertThat(xml).contains("</feed>");
    }

    @Test
    void getOpenSearchDescription_shouldReturnValidXml() {
        String xml = opdsFeedService.getOpenSearchDescription();
        assertThat(xml).contains("<OpenSearchDescription");
        assertThat(xml).contains("Search Booklore catalog");
        assertThat(xml).contains("</OpenSearchDescription>");
    }

    @Test
    void escapeXml_shouldEscapeSpecialCharacters() throws Exception {
        var method = OpdsFeedService.class.getDeclaredMethod("escapeXml", String.class);
        method.setAccessible(true);
        String input = "a&b<c>d\"e'f";
        String expected = "a&amp;b&lt;c&gt;d&quot;e&apos;f";
        String result = (String) method.invoke(opdsFeedService, input);
        assertThat(result).isEqualTo(expected);
    }

    @Test
    void parseLongParam_shouldHandleValidAndInvalidValues() throws Exception {
        var method = OpdsFeedService.class.getDeclaredMethod("parseLongParam", HttpServletRequest.class, String.class, Long.class);
        method.setAccessible(true);

        when(request.getParameter("valid")).thenReturn("123");
        when(request.getParameter("invalid")).thenReturn("abc");
        when(request.getParameter("missing")).thenReturn(null);

        Long valid = (Long) method.invoke(opdsFeedService, request, "valid", 42L);
        Long invalid = (Long) method.invoke(opdsFeedService, request, "invalid", 42L);
        Long missing = (Long) method.invoke(opdsFeedService, request, "missing", 42L);

        assertThat(valid).isEqualTo(123L);
        assertThat(invalid).isEqualTo(42L);
        assertThat(missing).isEqualTo(42L);
    }

    @Test
    void getUserId_shouldReturnUserId() throws Exception {
        mockAuthenticatedUser();

        var method = OpdsFeedService.class.getDeclaredMethod("getUserId");
        method.setAccessible(true);
        Long userId = (Long) method.invoke(opdsFeedService);

        assertThat(userId).isEqualTo(TEST_USER_ID);
    }

    @Test
    void getUserId_shouldReturnNullWhenNotAuthenticated() throws Exception {
        when(authenticationService.getOpdsUser()).thenReturn(null);

        var method = OpdsFeedService.class.getDeclaredMethod("getUserId");
        method.setAccessible(true);
        Long userId = (Long) method.invoke(opdsFeedService);

        assertThat(userId).isNull();
    }
}
