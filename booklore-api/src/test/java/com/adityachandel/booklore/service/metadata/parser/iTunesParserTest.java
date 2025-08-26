package com.adityachandel.booklore.service.metadata.parser;

import com.adityachandel.booklore.model.dto.Book;
import com.adityachandel.booklore.model.dto.BookMetadata;
import com.adityachandel.booklore.model.dto.request.FetchMetadataRequest;
import com.adityachandel.booklore.model.dto.settings.AppSettings;
import com.adityachandel.booklore.model.dto.settings.MetadataProviderSettings;
import com.adityachandel.booklore.model.enums.MetadataProvider;
import com.adityachandel.booklore.service.appsettings.AppSettingService;
import com.adityachandel.booklore.service.metadata.parser.itunes.iTunesApiService;
import com.adityachandel.booklore.service.metadata.parser.itunes.iTunesSearchRequest;
import com.adityachandel.booklore.service.metadata.parser.itunes.iTunesSearchResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class iTunesParserTest {

    @Mock
    private iTunesApiService iTunesApiService;
    
    @Mock
    private AppSettingService appSettingService;
    
    private iTunesParser iTunesParser;
    
    private Book testBook;
    private FetchMetadataRequest testRequest;

    @BeforeEach
    void setUp() {
        iTunesParser = new iTunesParser(iTunesApiService, appSettingService);
        
        testBook = Book.builder().build();
        testRequest = FetchMetadataRequest.builder()
                .title("The Great Gatsby")
                .author("F. Scott Fitzgerald")
                .build();
    }

    @Test
    void shouldReturnEmptyListWhenProviderDisabled() {
        // Given
        MetadataProviderSettings.iTunes iTunesSettings = new MetadataProviderSettings.iTunes();
        iTunesSettings.setEnabled(false);
        
        MetadataProviderSettings providerSettings = new MetadataProviderSettings();
        providerSettings.setITunes(iTunesSettings);
        
        AppSettings appSettings = new AppSettings();
        appSettings.setMetadataProviderSettings(providerSettings);
        
        when(appSettingService.getAppSettings()).thenReturn(appSettings);

        // When
        List<BookMetadata> result = iTunesParser.fetchMetadata(testBook, testRequest);

        // Then
        assertTrue(result.isEmpty());
        verify(iTunesApiService, never()).search(any());
    }

    @Test
    void shouldReturnCoverMetadataWhenResultsFound() {
        // Given
        MetadataProviderSettings.iTunes iTunesSettings = new MetadataProviderSettings.iTunes();
        iTunesSettings.setEnabled(true);
        iTunesSettings.setCountry("us");
        
        MetadataProviderSettings providerSettings = new MetadataProviderSettings();
        providerSettings.setITunes(iTunesSettings);
        
        AppSettings appSettings = new AppSettings();
        appSettings.setMetadataProviderSettings(providerSettings);
        
        when(appSettingService.getAppSettings()).thenReturn(appSettings);

        // Mock iTunes API response
        iTunesSearchResponse.iTunesItem item = new iTunesSearchResponse.iTunesItem();
        item.setArtworkUrl100("https://is1-ssl.mzstatic.com/image/thumb/100x100bb.jpg");
        item.setTrackName("The Great Gatsby");
        item.setArtistName("F. Scott Fitzgerald");
        
        iTunesSearchResponse response = new iTunesSearchResponse();
        response.setResultCount(1);
        response.setResults(List.of(item));
        
        when(iTunesApiService.search(any(iTunesSearchRequest.class))).thenReturn(Optional.of(response));
        when(iTunesApiService.getHighResolutionArtworkUrls(response))
                .thenReturn(List.of("https://is1-ssl.mzstatic.com/image/thumb/600x600bb.jpg"));

        // When
        List<BookMetadata> result = iTunesParser.fetchMetadata(testBook, testRequest);

        // Then
        assertFalse(result.isEmpty());
        BookMetadata metadata = result.get(0);
        assertEquals("https://is1-ssl.mzstatic.com/image/thumb/600x600bb.jpg", metadata.getThumbnailUrl());
        assertEquals(MetadataProvider.iTunes, metadata.getProvider());
        
        // Should only have thumbnail URL populated (cover-only provider)
        assertNull(metadata.getTitle());
        assertNull(metadata.getAuthors());
        assertNull(metadata.getDescription());
    }

    @Test
    void shouldFallbackToAudiobookEntityWhenEbookReturnsNoResults() {
        // Given
        MetadataProviderSettings.iTunes iTunesSettings = new MetadataProviderSettings.iTunes();
        iTunesSettings.setEnabled(true);
        iTunesSettings.setCountry("us");
        
        MetadataProviderSettings providerSettings = new MetadataProviderSettings();
        providerSettings.setITunes(iTunesSettings);
        
        AppSettings appSettings = new AppSettings();
        appSettings.setMetadataProviderSettings(providerSettings);
        
        when(appSettingService.getAppSettings()).thenReturn(appSettings);

        // Mock empty results for first few searches, successful for audiobook
        iTunesSearchResponse emptyResponse = new iTunesSearchResponse();
        emptyResponse.setResultCount(0);
        emptyResponse.setResults(List.of());
        
        iTunesSearchResponse.iTunesItem item = new iTunesSearchResponse.iTunesItem();
        item.setArtworkUrl100("https://audiobook-cover.jpg");
        
        iTunesSearchResponse audiobookResponse = new iTunesSearchResponse();
        audiobookResponse.setResultCount(1);
        audiobookResponse.setResults(List.of(item));
        
        // Return empty first, then successful result
        when(iTunesApiService.search(any(iTunesSearchRequest.class)))
                .thenReturn(Optional.of(emptyResponse))
                .thenReturn(Optional.of(emptyResponse))
                .thenReturn(Optional.of(emptyResponse))
                .thenReturn(Optional.of(audiobookResponse));
        
        when(iTunesApiService.getHighResolutionArtworkUrls(emptyResponse))
                .thenReturn(List.of());
        when(iTunesApiService.getHighResolutionArtworkUrls(audiobookResponse))
                .thenReturn(List.of("https://audiobook-cover-600x600.jpg"));

        // When
        List<BookMetadata> result = iTunesParser.fetchMetadata(testBook, testRequest);

        // Then
        assertFalse(result.isEmpty());
        assertEquals("https://audiobook-cover-600x600.jpg", result.get(0).getThumbnailUrl());
        verify(iTunesApiService, times(4)).search(any());
    }

    @Test
    void shouldReturnTopMetadataFromList() {
        // Given
        MetadataProviderSettings.iTunes iTunesSettings = new MetadataProviderSettings.iTunes();
        iTunesSettings.setEnabled(true);
        iTunesSettings.setCountry("us");
        
        MetadataProviderSettings providerSettings = new MetadataProviderSettings();
        providerSettings.setITunes(iTunesSettings);
        
        AppSettings appSettings = new AppSettings();
        appSettings.setMetadataProviderSettings(providerSettings);
        
        when(appSettingService.getAppSettings()).thenReturn(appSettings);

        // Mock multiple results
        iTunesSearchResponse.iTunesItem item1 = new iTunesSearchResponse.iTunesItem();
        item1.setArtworkUrl100("https://first-cover.jpg");
        
        iTunesSearchResponse.iTunesItem item2 = new iTunesSearchResponse.iTunesItem();
        item2.setArtworkUrl100("https://second-cover.jpg");
        
        iTunesSearchResponse response = new iTunesSearchResponse();
        response.setResultCount(2);
        response.setResults(List.of(item1, item2));
        
        when(iTunesApiService.search(any(iTunesSearchRequest.class))).thenReturn(Optional.of(response));
        when(iTunesApiService.getHighResolutionArtworkUrls(response))
                .thenReturn(List.of("https://first-cover-600x600.jpg", "https://second-cover-600x600.jpg"));

        // When
        BookMetadata topResult = iTunesParser.fetchTopMetadata(testBook, testRequest);

        // Then
        assertNotNull(topResult);
        assertEquals("https://first-cover-600x600.jpg", topResult.getThumbnailUrl());
        assertEquals(MetadataProvider.iTunes, topResult.getProvider());
    }

    @Test
    void shouldReturnNullWhenNoResultsFound() {
        // Given
        MetadataProviderSettings.iTunes iTunesSettings = new MetadataProviderSettings.iTunes();
        iTunesSettings.setEnabled(true);
        iTunesSettings.setCountry("us");
        
        MetadataProviderSettings providerSettings = new MetadataProviderSettings();
        providerSettings.setITunes(iTunesSettings);
        
        AppSettings appSettings = new AppSettings();
        appSettings.setMetadataProviderSettings(providerSettings);
        
        when(appSettingService.getAppSettings()).thenReturn(appSettings);
        when(iTunesApiService.search(any(iTunesSearchRequest.class))).thenReturn(Optional.empty());

        // When
        BookMetadata result = iTunesParser.fetchTopMetadata(testBook, testRequest);

        // Then
        assertNull(result);
    }
}
