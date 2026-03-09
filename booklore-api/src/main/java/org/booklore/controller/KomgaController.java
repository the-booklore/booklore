package org.booklore.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.booklore.config.JacksonConfig;
import org.booklore.config.security.service.AuthenticationService;
import org.booklore.config.security.userdetails.OpdsUserDetails;
import org.booklore.mapper.komga.KomgaMapper;
import org.booklore.model.dto.komga.KomgaBookDto;
import org.booklore.model.dto.komga.KomgaLibraryDto;
import org.booklore.model.dto.komga.KomgaPageableDto;
import org.booklore.model.dto.komga.KomgaSeriesDto;
import org.booklore.service.book.BookService;
import org.booklore.service.komga.KomgaService;
import org.booklore.service.opds.OpdsBookService;
import org.booklore.service.opds.OpdsUserV2Service;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.rememberme.TokenBasedRememberMeServices;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;

@Tag(name = "Komga API", description = "Komga-compatible API endpoints. " +
        "All endpoints support a 'clean' query parameter (default: false). " +
        "When present (?clean or ?clean=true), responses exclude fields ending with 'Lock', null values, and empty arrays, " +
        "resulting in smaller and cleaner JSON payloads.")
@Slf4j
@RestController
@RequestMapping(value = "/komga", produces = "application/json")
@RequiredArgsConstructor
public class KomgaController {

    private final KomgaService komgaService;
    private final BookService bookService;
    private final OpdsBookService opdsBookService;
    private final AuthenticationService authenticationService;
    private final OpdsUserV2Service opdsUserV2Service;
    private final KomgaMapper komgaMapper;
    private final TokenBasedRememberMeServices komgaRememberMeServices;

    // Inject the dedicated komga mapper bean
    private final @Qualifier(JacksonConfig.KOMGA_CLEAN_OBJECT_MAPPER) ObjectMapper komgaCleanObjectMapper;

    // Helper to serialize using the komga-clean mapper
    private ResponseEntity<String> writeJson(Object body) {
        try {
            String json = komgaCleanObjectMapper.writeValueAsString(body);
            return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(json);
        } catch (Exception e) {
            log.error("Failed to serialize Komga response", e);
            return ResponseEntity.status(500).build();
        }
    }

    // ==================== Fake SSE for Komelia ====================
    
    @Operation(summary = "SSE events")
    @GetMapping("/sse/v1/events")
    public SseEmitter getSseEvents() {
        return new SseEmitter();
    }

    // ==================== Read lists ====================
    
    @Operation(summary = "List all libraries")
    @GetMapping("/api/v1/readlists")
    public ResponseEntity<String> getReadlists(
            @Parameter(description = "Library ID filter") @RequestParam(name = "library_id", required = false) List<Long> libraryIds,
            @Parameter(description = "Search criteria") @RequestBody(required = false) String search,
            @Parameter(description = "Page number") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Page size") @RequestParam(defaultValue = "20") int size,
            @Parameter(description = "Return all books without paging") @RequestParam(defaultValue = "false") boolean unpaged) {
        KomgaPageableDto<Object> result = komgaService.getReadlists(libraryIds, search, page, size, unpaged);
        return writeJson(result);
    }

    // ==================== On Deck ====================
    
    @Operation(summary = "List all libraries")
    @GetMapping("/api/v1/books/ondeck")
    public ResponseEntity<String> getOnDeckBooks(
            @Parameter(description = "Library ID filter") @RequestParam(name = "library_id", required = false) List<Long> libraryIds,
            @Parameter(description = "Page number") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Page size") @RequestParam(defaultValue = "20") int size,
            @Parameter(description = "Return all books without paging") @RequestParam(defaultValue = "false") boolean unpaged) {
        KomgaPageableDto<Object> result = komgaService.getOnDeckBooks(libraryIds, page, size, unpaged);
        return writeJson(result);
    }

    // ==================== Libraries ====================
    
    @Operation(summary = "List all libraries")
    @GetMapping("/api/v1/libraries")
    public ResponseEntity<String> getAllLibraries() {
        List<KomgaLibraryDto> libraries = komgaService.getAllLibraries();
        return writeJson(libraries);
    }

    @Operation(summary = "Get library details")
    @GetMapping("/api/v1/libraries/{libraryId}")
    public ResponseEntity<String> getLibrary(
            @Parameter(description = "Library ID") @PathVariable Long libraryId) {
        return writeJson(komgaService.getLibraryById(libraryId));
    }

    // ==================== Series ====================
    
    @Operation(summary = "List series")
    @GetMapping("/api/v1/series/new")
    public ResponseEntity<String> getNewSeries(
            @Parameter(description = "Library ID filter") @RequestParam(name = "library_id", required = false) List<Long> libraryIds,
            @Parameter(description = "Page number") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Page size") @RequestParam(defaultValue = "20") int size,
            @Parameter(description = "Return all books without paging") @RequestParam(defaultValue = "false") boolean unpaged) {
        KomgaPageableDto<KomgaSeriesDto> result = komgaService.getNewSeries(libraryIds, page, size, unpaged);
        return writeJson(result);
    }
    
    @Operation(summary = "List series")
    @GetMapping("/api/v1/series/updated")
    public ResponseEntity<String> getUpdatedSeries(
            @Parameter(description = "Library ID filter") @RequestParam(name = "library_id", required = false) List<Long> libraryIds,
            @Parameter(description = "Page number") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Page size") @RequestParam(defaultValue = "20") int size,
            @Parameter(description = "Return all books without paging") @RequestParam(defaultValue = "false") boolean unpaged) {
        KomgaPageableDto<KomgaSeriesDto> result = komgaService.getUpdatedSeries(libraryIds, page, size, unpaged);
        return writeJson(result);
    }
    
    @Operation(summary = "List series")
    @GetMapping("/api/v1/series")
    public ResponseEntity<String> getAllSeries(
            @Parameter(description = "Library ID filter") @RequestParam(required = false, name = "library_id") Long libraryId,
            @Parameter(description = "Page number") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Page size") @RequestParam(defaultValue = "20") int size,
            @Parameter(description = "Return all books without paging") @RequestParam(defaultValue = "false") boolean unpaged) {
        KomgaPageableDto<KomgaSeriesDto> result = komgaService.getAllSeries(libraryId, page, size, unpaged);
        return writeJson(result);
    }

    @Operation(summary = "List series (POST with search)")
    @PostMapping("/api/v1/series/list")
    public ResponseEntity<String> getAllSeriesPost(
            @Parameter(description = "Library ID filter") @RequestParam(required = false, name = "library_id") Long libraryId,
            @Parameter(description = "Page number") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Page size") @RequestParam(defaultValue = "20") int size,
            @Parameter(description = "Return all series without paging") @RequestParam(defaultValue = "false") boolean unpaged,
            @Parameter(description = "Series search criteria") @RequestBody(required = false) Map<String, Object> searchCriteria) {
        KomgaPageableDto<KomgaSeriesDto> result = komgaService.getAllSeries(libraryId, page, size, unpaged);
        return writeJson(result);
    }

    @Operation(summary = "Get series details")
    @GetMapping("/api/v1/series/{seriesId}")
    public ResponseEntity<String> getSeries(
            @Parameter(description = "Series ID") @PathVariable String seriesId)  {
        return writeJson(komgaService.getSeriesById(seriesId));
    }

    @Operation(summary = "List books in series")
    @GetMapping("/api/v1/series/{seriesId}/books")
    public ResponseEntity<String> getSeriesBooks(
            @Parameter(description = "Series ID") @PathVariable String seriesId,
            @Parameter(description = "Page number") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Page size") @RequestParam(defaultValue = "20") int size,
            @Parameter(description = "Return all books without paging") @RequestParam(defaultValue = "false") boolean unpaged) {
        return writeJson(komgaService.getBooksBySeries(seriesId, page, size, unpaged));
    }

    @Operation(summary = "Get series thumbnail")
    @GetMapping("/api/v1/series/{seriesId}/thumbnail")
    public ResponseEntity<Resource> getSeriesThumbnail(
            @Parameter(description = "Series ID") @PathVariable String seriesId) {
        KomgaPageableDto<KomgaBookDto> books = komgaService.getBooksBySeries(seriesId, 0, 1, false);
        if (books.getContent().isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        
        Long firstBookId = Long.parseLong(books.getContent().get(0).getId());
        Resource coverImage = bookService.getBookThumbnail(firstBookId);
        return ResponseEntity.ok()
                .header("Content-Type", "image/jpeg")
                .body(coverImage);
    }

    // ==================== Books ====================
    
    @Operation(summary = "List books")
    @GetMapping("/api/v1/books")
    public ResponseEntity<String> getAllBooks(
            @Parameter(description = "Library ID filter") @RequestParam(required = false, name = "library_id") Long libraryId,
            @Parameter(description = "Page number") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Page size") @RequestParam(defaultValue = "20") int size) {
        KomgaPageableDto<KomgaBookDto> result = komgaService.getAllBooks(libraryId, page, size);
        return writeJson(result);
    }

    @Operation(summary = "List books (POST with search)")
    @PostMapping("/api/v1/books/list")
    public ResponseEntity<String> getAllBooksPost(
            @Parameter(description = "Library ID filter") @RequestParam(required = false, name = "library_id") Long libraryId,
            @Parameter(description = "Page number") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Page size") @RequestParam(defaultValue = "20") int size,
            @Parameter(description = "Return all books without paging") @RequestParam(defaultValue = "false") boolean unpaged,
            @Parameter(description = "Book search criteria") @RequestBody(required = false) Map<String, Object> searchCriteria) {
        // For now, we ignore the search criteria and just return all books with pagination
        // Future enhancement: implement search filtering based on searchCriteria
        KomgaPageableDto<KomgaBookDto> result = komgaService.getAllBooks(libraryId, page, size);
        return writeJson(result);
    }

    @Operation(summary = "Get book details")
    @GetMapping("/api/v1/books/{bookId}/readlists")
    public ResponseEntity<String> getBookReadlists(
            @Parameter(description = "Book ID") @PathVariable Long bookId) {
        // NO readlist in booklore
        return writeJson(List.of());
    }

    @Operation(summary = "Get book details")
    @GetMapping("/api/v1/books/{bookId}")
    public ResponseEntity<String> getBook(
            @Parameter(description = "Book ID") @PathVariable Long bookId) {
        return writeJson(komgaService.getBookById(bookId));
    }

    @Operation(summary = "Get book pages metadata")
    @GetMapping("/api/v1/books/{bookId}/pages")
    public ResponseEntity<String> getBookPages(
            @Parameter(description = "Book ID") @PathVariable Long bookId) {
        return writeJson(komgaService.getBookPages(bookId));
    }

    @Operation(summary = "Get book page image")
    @GetMapping("/api/v1/books/{bookId}/pages/{pageNumber}")
    public ResponseEntity<Resource> getBookPage(
            @Parameter(description = "Book ID") @PathVariable Long bookId,
            @Parameter(description = "Page number") @PathVariable Integer pageNumber,
            @Parameter(description = "Convert image format (e.g., 'png')") @RequestParam(required = false) String convert) {
        opdsBookService.validateBookContentAccess(bookId, getOpdsUserId());
        try {
            boolean convertToPng = "png".equalsIgnoreCase(convert);
            Resource pageImage = komgaService.getBookPageImage(bookId, pageNumber, convertToPng);
            // Note: When not converting, we assume JPEG as most CBZ files contain JPEG images,
            // but the actual format may vary (PNG, WebP, etc.)
            String contentType = convertToPng ? "image/png" : "image/jpeg";
            return ResponseEntity.ok()
                    .header("Content-Type", contentType)
                    .body(pageImage);
        } catch (Exception e) {
            log.error("Failed to get page {} from book {}", pageNumber, bookId, e);
            return ResponseEntity.notFound().build();
        }
    }

    @Operation(summary = "Download book file")
    @GetMapping("/api/v1/books/{bookId}/file")
    public ResponseEntity<Resource> downloadBook(
            @Parameter(description = "Book ID") @PathVariable Long bookId) {
        opdsBookService.validateBookContentAccess(bookId, getOpdsUserId());
        return bookService.downloadBook(bookId);
    }

    @Operation(summary = "Get book thumbnail")
    @GetMapping("/api/v1/books/{bookId}/thumbnail")
    public ResponseEntity<Resource> getBookThumbnail(
            @Parameter(description = "Book ID") @PathVariable Long bookId) {
        opdsBookService.validateBookContentAccess(bookId, getOpdsUserId());
        Resource coverImage = bookService.getBookThumbnail(bookId);
        return ResponseEntity.ok()
                .header("Content-Type", "image/jpeg")
                .body(coverImage);
    }

    // ==================== Users ====================
    
    @Operation(summary = "Get current user details")
    @GetMapping("/api/v2/users/me")
    public ResponseEntity<String> getCurrentUser(
            Authentication authentication,
            @Parameter(description = "Enable remember-me cookie") @RequestParam(name = "remember-me", required = false, defaultValue = "false") boolean rememberMe,
            HttpServletRequest request,
            HttpServletResponse response) {
        if (authentication == null || authentication.getName() == null) {
            return ResponseEntity.status(401).build();
        }
        
        String username = authentication.getName();
        var opdsUser = opdsUserV2Service.findByUsername(username);
        
        if (opdsUser == null) {
            return ResponseEntity.notFound().build();
        }
        
        // If remember-me is requested, create the remember-me cookie
        if (rememberMe) {
            try {
                komgaRememberMeServices.loginSuccess(request, response, authentication);
                log.debug("Remember-me cookie set for user: {}", username);
            } catch (Exception e) {
                log.error("Failed to set remember-me cookie for user: {}", username, e);
            }
        }
        
        return writeJson(komgaMapper.toKomgaUserDto(opdsUser));
    }
    
    // ==================== Collections ====================
    
    @Operation(summary = "List collections")
    @GetMapping("/api/v1/collections")
    public ResponseEntity<String> getCollections(
            Authentication authentication,
            @Parameter(description = "Page number") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Page size") @RequestParam(defaultValue = "20") int size,
            @Parameter(description = "Return all collections without paging") @RequestParam(defaultValue = "false") boolean unpaged) {
        
        // Get OPDS user from authentication
        Long userId = null;
        if (authentication != null && authentication.getName() != null) {
            String username = authentication.getName();
            var opdsUser = opdsUserV2Service.findByUsername(username);
            if (opdsUser != null) {
                userId = opdsUser.getUser().getId();
            }
        }
        
        if (userId == null) {
            return ResponseEntity.status(401).build();
        }
        
        return writeJson(komgaService.getCollections(userId, page, size, unpaged));
    }
    
    // ==================== Genres ====================
    
    @Operation(summary = "List genres")
    @GetMapping("/api/v1/genres")
    public ResponseEntity<String> getGenres(
            @Parameter(description = "Library ID filter") @RequestParam(name = "library_id", required = false) List<Long> libraryIds,
            @Parameter(description = "Collection ID filter") @RequestParam(name = "collection_id", required = false) Long collectionId) {
        return writeJson(komgaService.getGenres(libraryIds, collectionId));
    }
    
    // ==================== Tags ====================
    
    @Operation(summary = "List tags")
    @GetMapping("/api/v1/tags")
    public ResponseEntity<String> getTags(
            @Parameter(description = "Library ID filter") @RequestParam(name = "library_id", required = false) List<Long> libraryIds,
            @Parameter(description = "Collection ID filter") @RequestParam(name = "collection_id", required = false) Long collectionId) {
        return writeJson(komgaService.getTags(libraryIds, collectionId));
    }
    
    @Operation(summary = "List series tags")
    @GetMapping("/api/v1/tags/series")
    public ResponseEntity<String> getTags(
            @Parameter(description = "Library ID filter") @RequestParam(name = "library_id", required = false) Long libraryId,
            @Parameter(description = "Collection ID filter") @RequestParam(name = "collection_id", required = false) Long collectionId) {
        return writeJson(komgaService.getTags(List.of(libraryId), collectionId));
    }
    
    // ==================== Publishers ====================
    
    @Operation(summary = "List publishers")
    @GetMapping("/api/v1/publishers")
    public ResponseEntity<String> getPublishers(
            @Parameter(description = "Library ID filter") @RequestParam(name = "library_id", required = false) List<Long> libraryIds,
            @Parameter(description = "Collection ID filter") @RequestParam(name = "collection_id", required = false) Long collectionId) {
        return writeJson(komgaService.getPublishers(libraryIds, collectionId));
    }
    
    // ==================== Authors ====================
    
    @Operation(summary = "List authors")
    @GetMapping("/api/v1/authors")
    public ResponseEntity<String> getAuthorsV1(
            @Parameter(description = "Search query") @RequestParam(required = false) String search,
            @Parameter(description = "Author role filter") @RequestParam(required = false) String role,
            @Parameter(description = "Library ID filter") @RequestParam(name = "library_id", required = false) List<Long> libraryIds,
            @Parameter(description = "Collection ID filter") @RequestParam(name = "collection_id", required = false) Long collectionId,
            @Parameter(description = "Series ID filter") @RequestParam(name = "series_id", required = false) String seriesId,
            @Parameter(description = "Readlist ID filter") @RequestParam(name = "readlist_id", required = false) Long readlistId,
            @Parameter(description = "Page number") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Page size") @RequestParam(defaultValue = "20") int size,
            @Parameter(description = "Return all authors without paging") @RequestParam(defaultValue = "true") boolean unpaged) {

        KomgaPageableDto<KomgaAuthorDto> result = komgaService.getAuthors(search, role, libraryIds, collectionId, seriesId, readlistId, page, size, unpaged);
        // The komga API does not returned paged content
        // which can be slow. So for unpaged we return it as Komga API expects it.
        // otherwise we return it paged.
        if (unpaged) {
            return writeJson(result.getContent());
        } else {
            return writeJson(result);
        }
    }

    @Operation(summary = "List authors (v2)")
    @GetMapping("/api/v2/authors")
    public ResponseEntity<String> getAuthorsV2(
            @Parameter(description = "Search query") @RequestParam(required = false) String search,
            @Parameter(description = "Author role filter") @RequestParam(required = false) String role,
            @Parameter(description = "Library ID filter") @RequestParam(name = "library_id", required = false) List<Long> libraryIds,
            @Parameter(description = "Collection ID filter") @RequestParam(name = "collection_id", required = false) Long collectionId,
            @Parameter(description = "Series ID filter") @RequestParam(name = "series_id", required = false) String seriesId,
            @Parameter(description = "Readlist ID filter") @RequestParam(name = "readlist_id", required = false) Long readlistId,
            @Parameter(description = "Page number") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Page size") @RequestParam(defaultValue = "20") int size,
            @Parameter(description = "Return all authors without paging") @RequestParam(defaultValue = "false") boolean unpaged) {

        KomgaPageableDto<KomgaAuthorDto> result = komgaService.getAuthors(search, role, libraryIds, collectionId, seriesId, readlistId, page, size, unpaged);
        // v2 returns pageable response by default
        return writeJson(result);
    }

    private Long getOpdsUserId() {
        OpdsUserDetails details = authenticationService.getOpdsUser();
        return details != null && details.getOpdsUserV2() != null
                ? details.getOpdsUserV2().getUserId()
                : null;
    }
}