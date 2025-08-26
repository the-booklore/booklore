package com.adityachandel.booklore.service.metadata.parser.itunes;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class iTunesApiServiceTest {

    private iTunesApiService iTunesApiService;

    @BeforeEach
    void setUp() {
        iTunesApiService = new iTunesApiService(new ObjectMapper());
    }

    @Test
    void shouldConvertToHighResolutionUrls() {
        // Given
        iTunesSearchResponse.iTunesItem item1 = new iTunesSearchResponse.iTunesItem();
        item1.setArtworkUrl100("https://is1-ssl.mzstatic.com/image/thumb/100x100bb.jpg");
        
        iTunesSearchResponse.iTunesItem item2 = new iTunesSearchResponse.iTunesItem();
        item2.setArtworkUrl100("https://is2-ssl.mzstatic.com/image/thumb/100x100.jpg");
        
        iTunesSearchResponse.iTunesItem item3 = new iTunesSearchResponse.iTunesItem();
        item3.setArtworkUrl100("https://is3-ssl.mzstatic.com/image/thumb/300x300.jpg");
        
        iTunesSearchResponse response = new iTunesSearchResponse();
        response.setResults(List.of(item1, item2, item3));

        // When
        List<String> highResUrls = iTunesApiService.getHighResolutionArtworkUrls(response);

        // Then
        assertEquals(3, highResUrls.size());
        assertEquals("https://is1-ssl.mzstatic.com/image/thumb/600x600bb.jpg", highResUrls.get(0));
        assertEquals("https://is2-ssl.mzstatic.com/image/thumb/600x600.jpg", highResUrls.get(1));
        assertEquals("https://is3-ssl.mzstatic.com/image/thumb/300x300.jpg", highResUrls.get(2)); // No conversion
    }

    @Test
    void shouldFilterOutNullAndEmptyArtworkUrls() {
        // Given
        iTunesSearchResponse.iTunesItem item1 = new iTunesSearchResponse.iTunesItem();
        item1.setArtworkUrl100("https://valid-url.jpg");
        
        iTunesSearchResponse.iTunesItem item2 = new iTunesSearchResponse.iTunesItem();
        item2.setArtworkUrl100(null);
        
        iTunesSearchResponse.iTunesItem item3 = new iTunesSearchResponse.iTunesItem();
        item3.setArtworkUrl100("");
        
        iTunesSearchResponse response = new iTunesSearchResponse();
        response.setResults(List.of(item1, item2, item3));

        // When
        List<String> highResUrls = iTunesApiService.getHighResolutionArtworkUrls(response);

        // Then
        assertEquals(1, highResUrls.size());
        assertEquals("https://valid-url.jpg", highResUrls.get(0));
    }

    @Test
    void shouldHandleEmptyResponse() {
        // Given
        iTunesSearchResponse response = new iTunesSearchResponse();
        response.setResults(List.of());

        // When
        List<String> highResUrls = iTunesApiService.getHighResolutionArtworkUrls(response);

        // Then
        assertTrue(highResUrls.isEmpty());
    }
}
