package org.booklore.service.metadata.parser.custom;

import lombok.extern.slf4j.Slf4j;
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
import org.booklore.service.metadata.BookCoverProvider;
import org.booklore.service.metadata.parser.BookParser;
import org.booklore.service.metadata.parser.DetailedMetadataProvider;

import java.util.ArrayList;
import java.util.List;

/**
 * A {@link BookParser} implementation that delegates to an external metadata provider
 * conforming to the Book Metadata Provider API spec.
 * <p>
 * Each instance wraps a {@link CustomProviderClient} for one configured external provider.
 * Also implements {@link DetailedMetadataProvider} and {@link BookCoverProvider} when the
 * external provider's capabilities indicate support.
 */
@Slf4j
public class CustomBookParser implements BookParser, DetailedMetadataProvider, BookCoverProvider {

    private final CustomProviderClient client;
    private final ExternalMetadataMapper mapper;
    private final CustomMetadataProviderConfig config;

    public CustomBookParser(CustomProviderClient client, ExternalMetadataMapper mapper) {
        this.client = client;
        this.mapper = mapper;
        this.config = client.getConfig();
    }

    @Override
    public List<BookMetadata> fetchMetadata(Book book, FetchMetadataRequest request) {
        if (!supportsMetadata()) {
            return List.of();
        }

        String bookTitle = resolveBookTitle(book, request);
        List<ExternalBookMetadata> results = performSearch(book, request);

        if (results == null || results.isEmpty()) {
            return List.of();
        }

        List<BookMetadata> mapped = new ArrayList<>();
        for (ExternalBookMetadata external : results) {
            BookMetadata metadata = mapper.toBookMetadata(external, bookTitle);
            if (metadata != null) {
                metadata.setExternalUrl(null);
                mapped.add(metadata);
            }
        }
        return mapped;
    }

    @Override
    public BookMetadata fetchTopMetadata(Book book, FetchMetadataRequest request) {
        List<BookMetadata> results = fetchMetadata(book, request);
        return results.isEmpty() ? null : results.getFirst();
    }

    @Override
    public BookMetadata fetchDetailedMetadata(String providerItemId) {
        if (!supportsMetadata()) {
            return null;
        }

        ExternalBookMetadata external = client.getMetadataById(providerItemId);
        if (external == null) {
            return null;
        }
        return mapper.toBookMetadata(external, null);
    }

    @Override
    public List<CoverImage> getCovers(CoverFetchRequest request) {
        if (!supportsCovers()) {
            return List.of();
        }

        List<ExternalCoverImage> results = client.searchCovers(
                null,
                request.getTitle(),
                request.getAuthor(),
                request.getIsbn(),
                null,
                null,
                "large"
        );

        if (results == null || results.isEmpty()) {
            return List.of();
        }

        List<CoverImage> covers = new ArrayList<>();
        for (int i = 0; i < results.size(); i++) {
            CoverImage cover = mapper.toCoverImage(results.get(i), i);
            if (cover != null) {
                covers.add(cover);
            }
        }
        return covers;
    }

    /**
     * Performs a search against the external provider using the best available identifiers
     * from the book and the request, respecting provider capabilities.
     */
    private List<ExternalBookMetadata> performSearch(Book book, FetchMetadataRequest request) {
        String isbn = request.getIsbn();
        String title = request.getTitle();
        String author = request.getAuthor();
        String asin = request.getAsin();

        // Fall back to book metadata if request fields are empty
        if (book != null && book.getMetadata() != null) {
            BookMetadata existing = book.getMetadata();
            if (isBlank(isbn)) {
                isbn = existing.getIsbn13() != null ? existing.getIsbn13() : existing.getIsbn10();
            }
            if (isBlank(title)) {
                title = existing.getTitle();
            }
            if (isBlank(author) && existing.getAuthors() != null && !existing.getAuthors().isEmpty()) {
                author = String.join(", ", existing.getAuthors());
            }
            if (isBlank(asin)) {
                asin = existing.getAsin();
            }
        }

        // Try ISBN search first if supported
        if (!isBlank(isbn) && supportsIsbnSearch()) {
            String isbn13 = null;
            String isbn10 = null;
            if (isbn.length() == 13) {
                isbn13 = isbn;
            } else if (isbn.length() == 10) {
                isbn10 = isbn;
            }
            List<ExternalBookMetadata> results = client.searchMetadata(null, null, null, isbn13, isbn10, null, null, 10);
            if (results != null && !results.isEmpty()) {
                return results;
            }
        }

        // Try title + author search
        if (!isBlank(title) && supportsTitleAuthorSearch()) {
            List<ExternalBookMetadata> results = client.searchMetadata(null, title, author, null, null, asin, null, 10);
            if (results != null && !results.isEmpty()) {
                return results;
            }
        }

        // Fallback: general query search
        String query = buildGeneralQuery(title, author);
        if (!isBlank(query)) {
            return client.searchMetadata(query, null, null, null, null, null, null, 10);
        }

        return List.of();
    }

    private String resolveBookTitle(Book book, FetchMetadataRequest request) {
        if (!isBlank(request.getTitle())) {
            return request.getTitle();
        }
        if (book != null && book.getMetadata() != null && !isBlank(book.getMetadata().getTitle())) {
            return book.getMetadata().getTitle();
        }
        return null;
    }

    private String buildGeneralQuery(String title, String author) {
        if (isBlank(title)) return null;
        if (isBlank(author)) return title;
        return title + " " + author;
    }

    private boolean supportsMetadata() {
        ExternalProviderCapabilities.Capabilities caps = config.getCapabilities();
        return caps == null || caps.getSupportsMetadata() == null || caps.getSupportsMetadata();
    }

    private boolean supportsCovers() {
        ExternalProviderCapabilities.Capabilities caps = config.getCapabilities();
        return caps != null && caps.getSupportsCovers() != null && caps.getSupportsCovers();
    }

    private boolean supportsIsbnSearch() {
        ExternalProviderCapabilities.Capabilities caps = config.getCapabilities();
        return caps == null || caps.getSupportsIsbnSearch() == null || caps.getSupportsIsbnSearch();
    }

    private boolean supportsTitleAuthorSearch() {
        ExternalProviderCapabilities.Capabilities caps = config.getCapabilities();
        return caps == null || caps.getSupportsTitleAuthorSearch() == null || caps.getSupportsTitleAuthorSearch();
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    public String getProviderId() {
        return config.getId();
    }

    public String getProviderName() {
        return config.getName();
    }
}
