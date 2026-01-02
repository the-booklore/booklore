package com.adityachandel.booklore.service.opds;

import com.adityachandel.booklore.config.security.service.AuthenticationService;
import com.adityachandel.booklore.config.security.userdetails.OpdsUserDetails;
import com.adityachandel.booklore.model.dto.Book;
import com.adityachandel.booklore.model.dto.BookMetadata;
import com.adityachandel.booklore.model.dto.OpdsUserV2;
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
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class OpdsFeedServiceMimeTypeTest {

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
        
        mockAuthenticatedUser();
        mockRequest();
    }

    private void mockAuthenticatedUser() {
        OpdsUserDetails userDetails = mock(OpdsUserDetails.class);
        OpdsUserV2 v2 = mock(OpdsUserV2.class);
        when(userDetails.getOpdsUserV2()).thenReturn(v2);
        when(v2.getUserId()).thenReturn(1L);
        when(v2.getSortOrder()).thenReturn(OpdsSortOrder.RECENT);
        when(authenticationService.getOpdsUser()).thenReturn(userDetails);
    }

    private void mockRequest() {
        when(request.getParameter(any())).thenReturn(null);
        when(request.getRequestURI()).thenReturn("/api/v1/opds/catalog");
        when(request.getQueryString()).thenReturn(null);
    }

    private void mockBooksPage(Book book) {
        Page<Book> page = new PageImpl<>(List.of(book), PageRequest.of(0, 50), 1);
        when(opdsBookService.getBooksPage(eq(1L), any(), any(), any(), eq(0), eq(50))).thenReturn(page);
        when(opdsBookService.applySortOrder(any(), any())).thenReturn(page);
    }

    @Test
    void testMimeTypeForEpub() {
        Book book = createBook(BookFileType.EPUB, "book.epub");
        mockBooksPage(book);
        String xml = opdsFeedService.generateCatalogFeed(request);
        assertThat(xml).contains("type=\"application/epub+zip\"");
    }

    @Test
    void testMimeTypeForPdf() {
        Book book = createBook(BookFileType.PDF, "document.pdf");
        mockBooksPage(book);
        String xml = opdsFeedService.generateCatalogFeed(request);
        assertThat(xml).contains("type=\"application/pdf\"");
    }

    @Test
    void testMimeTypeForCbz() {
        Book book = createBook(BookFileType.CBX, "comic.cbz");
        mockBooksPage(book);
        String xml = opdsFeedService.generateCatalogFeed(request);
        assertThat(xml).contains("type=\"application/vnd.comicbook+zip\"");
    }

    @Test
    void testMimeTypeForCbr() {
        Book book = createBook(BookFileType.CBX, "comic.cbr");
        mockBooksPage(book);
        String xml = opdsFeedService.generateCatalogFeed(request);
        assertThat(xml).contains("type=\"application/vnd.comicbook-rar\"");
    }

    @Test
    void testMimeTypeForCb7() {
        Book book = createBook(BookFileType.CBX, "comic.cb7");
        mockBooksPage(book);
        String xml = opdsFeedService.generateCatalogFeed(request);
        assertThat(xml).contains("type=\"application/x-7z-compressed\"");
    }

    @Test
    void testMimeTypeForCbt() {
        Book book = createBook(BookFileType.CBX, "comic.cbt");
        mockBooksPage(book);
        String xml = opdsFeedService.generateCatalogFeed(request);
        assertThat(xml).contains("type=\"application/x-tar\"");
    }

    @Test
    void testMimeTypeForFb2() {
        Book book = createBook(BookFileType.FB2, "book.fb2");
        mockBooksPage(book);
        String xml = opdsFeedService.generateCatalogFeed(request);
        assertThat(xml).contains("type=\"application/x-fictionbook+xml\"");
    }

    private Book createBook(BookFileType type, String fileName) {
        return Book.builder()
                .id(1L)
                .bookType(type)
                .fileName(fileName)
                .addedOn(Instant.now())
                .metadata(BookMetadata.builder().title("Test Book").build())
                .build();
    }
}
