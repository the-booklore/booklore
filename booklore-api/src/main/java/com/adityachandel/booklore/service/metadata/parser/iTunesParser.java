package com.adityachandel.booklore.service.metadata.parser;

import com.adityachandel.booklore.model.dto.Book;
import com.adityachandel.booklore.model.dto.BookMetadata;
import com.adityachandel.booklore.model.dto.request.FetchMetadataRequest;
import com.adityachandel.booklore.model.enums.MetadataProvider;
import com.adityachandel.booklore.service.appsettings.AppSettingService;
import com.adityachandel.booklore.service.metadata.parser.itunes.iTunesApiService;
import com.adityachandel.booklore.service.metadata.parser.itunes.iTunesSearchRequest;
import com.adityachandel.booklore.service.metadata.parser.itunes.iTunesSearchResponse;
import com.adityachandel.booklore.util.BookUtils;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@AllArgsConstructor
public class iTunesParser implements BookParser {
    
    private final iTunesApiService iTunesApiService;
    private final AppSettingService appSettingService;
    
    @Override
    public List<BookMetadata> fetchMetadata(Book book, FetchMetadataRequest fetchMetadataRequest) {
        String title = fetchMetadataRequest.getTitle();
        
        // Use filename as fallback if title is null or empty
        if (title == null || title.trim().isEmpty()) {
            if (book.getFileName() != null && !book.getFileName().trim().isEmpty()) {
                title = BookUtils.cleanAndTruncateSearchTerm(BookUtils.cleanFileName(book.getFileName()));
                log.info("iTunes: Using cleaned filename as search term: {}", title);
            } else {
                log.warn("iTunes: Both title and filename are null or empty, skipping search");
                return List.of();
            }
        }
        
        log.info("iTunes: Fetching cover metadata for book {}", title);
        
        var iTunesSettings = appSettingService.getAppSettings().getMetadataProviderSettings().getITunes();
        if (!iTunesSettings.isEnabled()) {
            log.debug("iTunes provider is disabled");
            return List.of();
        }
        
        List<BookMetadata> results = new ArrayList<>();
        String country = iTunesSettings.getCountry();
        
        // Strategy 1: Primary search with title + author and ebook entity
        if (fetchMetadataRequest.getAuthor() != null && !fetchMetadataRequest.getAuthor().isBlank()) {
            String fullTerm = title + " " + fetchMetadataRequest.getAuthor();
            results.addAll(searchForCovers(fullTerm, "ebook", country));
        }
        
        // Strategy 2: Fallback search with title only and ebook entity
        if (results.isEmpty()) {
            results.addAll(searchForCovers(title, "ebook", country));
        }
        
        // Strategy 3: Alternative search with title + author and audiobook entity
        if (results.isEmpty() && fetchMetadataRequest.getAuthor() != null && !fetchMetadataRequest.getAuthor().isBlank()) {
            String fullTerm = title + " " + fetchMetadataRequest.getAuthor();
            results.addAll(searchForCovers(fullTerm, "audiobook", country));
        }
        
        // Strategy 4: Alternative search with title only and audiobook entity
        if (results.isEmpty()) {
            results.addAll(searchForCovers(title, "audiobook", country));
        }
        
        log.info("iTunes: Found {} cover results for '{}'", results.size(), title);
        return results;
    }
    
    @Override
    public BookMetadata fetchTopMetadata(Book book, FetchMetadataRequest fetchMetadataRequest) {
        List<BookMetadata> bookMetadata = fetchMetadata(book, fetchMetadataRequest);
        return bookMetadata.isEmpty() ? null : bookMetadata.getFirst();
    }
    
    private List<BookMetadata> searchForCovers(String searchTerm, String entity, String country) {
        iTunesSearchRequest request = iTunesSearchRequest.builder()
                .term(searchTerm)
                .entity(entity)
                .country(country)
                .limit(10)
                .build();
        
        Optional<iTunesSearchResponse> response = iTunesApiService.search(request);
        if (response.isEmpty()) {
            return List.of();
        }
        
        List<String> artworkUrls = iTunesApiService.getHighResolutionArtworkUrls(response.get());
        
        return artworkUrls.stream()
                .map(this::createCoverOnlyMetadata)
                .toList();
    }
    
    private BookMetadata createCoverOnlyMetadata(String artworkUrl) {
        BookMetadata metadata = new BookMetadata();
        metadata.setThumbnailUrl(artworkUrl);
        metadata.setProvider(MetadataProvider.iTunes);
        return metadata;
    }
}
