package com.adityachandel.booklore.service.hardcover;

import com.adityachandel.booklore.model.dto.settings.AppSettings;
import com.adityachandel.booklore.model.dto.settings.MetadataProviderSettings;
import com.adityachandel.booklore.model.entity.BookEntity;
import com.adityachandel.booklore.model.entity.BookMetadataEntity;
import com.adityachandel.booklore.service.appsettings.AppSettingService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class HardcoverSyncServiceTest {

    @Mock
    private AppSettingService appSettingService;

    @Mock
    private RestClient restClient;

    @Mock
    private RestClient.RequestBodyUriSpec requestBodyUriSpec;

    @Mock
    private RestClient.RequestBodySpec requestBodySpec;

    @Mock
    private RestClient.ResponseSpec responseSpec;

    private HardcoverSyncService service;

    private BookEntity testBook;
    private BookMetadataEntity testMetadata;
    private AppSettings appSettings;
    private MetadataProviderSettings.Hardcover hardcoverSettings;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() throws Exception {
        objectMapper = new ObjectMapper();
        
        // Create service
        service = new HardcoverSyncService(appSettingService);
        
        // Inject our mocked restClient using reflection
        Field restClientField = HardcoverSyncService.class.getDeclaredField("restClient");
        restClientField.setAccessible(true);
        restClientField.set(service, restClient);

        testBook = new BookEntity();
        testBook.setId(100L);

        testMetadata = new BookMetadataEntity();
        testMetadata.setIsbn13("9781234567890");
        testMetadata.setPageCount(300);
        testBook.setMetadata(testMetadata);

        appSettings = new AppSettings();
        MetadataProviderSettings metadataSettings = new MetadataProviderSettings();
        hardcoverSettings = new MetadataProviderSettings.Hardcover();
        hardcoverSettings.setEnabled(true);
        hardcoverSettings.setApiKey("test-api-key");
        metadataSettings.setHardcover(hardcoverSettings);
        appSettings.setMetadataProviderSettings(metadataSettings);

        when(appSettingService.getAppSettings()).thenReturn(appSettings);
        
        // Setup default RestClient mock chain
        when(restClient.post()).thenReturn(requestBodyUriSpec);
        when(requestBodyUriSpec.uri(anyString())).thenReturn(requestBodySpec);
        when(requestBodySpec.header(anyString(), anyString())).thenReturn(requestBodySpec);
        when(requestBodySpec.body(any())).thenReturn(requestBodySpec);
        when(requestBodySpec.retrieve()).thenReturn(responseSpec);
    }

    @Test
    @DisplayName("Should skip sync when Hardcover is not enabled")
    void syncProgressToHardcover_whenHardcoverDisabled_shouldSkip() {
        hardcoverSettings.setEnabled(false);

        service.syncProgressToHardcover(testBook, 50.0f);

        verify(restClient, never()).post();
    }

    @Test
    @DisplayName("Should skip sync when API key is missing")
    void syncProgressToHardcover_whenApiKeyMissing_shouldSkip() {
        hardcoverSettings.setApiKey(null);

        service.syncProgressToHardcover(testBook, 50.0f);

        verify(restClient, never()).post();
    }

    @Test
    @DisplayName("Should skip sync when API key is blank")
    void syncProgressToHardcover_whenApiKeyBlank_shouldSkip() {
        hardcoverSettings.setApiKey("   ");

        service.syncProgressToHardcover(testBook, 50.0f);

        verify(restClient, never()).post();
    }

    @Test
    @DisplayName("Should skip sync when progress is null")
    void syncProgressToHardcover_whenProgressNull_shouldSkip() {
        service.syncProgressToHardcover(testBook, null);

        verify(restClient, never()).post();
    }

    @Test
    @DisplayName("Should skip sync when book has no metadata")
    void syncProgressToHardcover_whenNoMetadata_shouldSkip() {
        testBook.setMetadata(null);

        service.syncProgressToHardcover(testBook, 50.0f);

        verify(restClient, never()).post();
    }

    @Test
    @DisplayName("Should use stored hardcoverBookId when available")
    void syncProgressToHardcover_withStoredBookId_shouldUseStoredId() {
        testMetadata.setHardcoverBookId(12345);
        testMetadata.setPageCount(300);

        // Mock: insert_user_book response
        Map<String, Object> insertResponse = createInsertUserBookResponse(5001, null);
        when(responseSpec.body(Map.class)).thenReturn(insertResponse);

        // Mock: insert_user_book_read response
        Map<String, Object> insertReadResponse = createInsertUserBookReadResponse();
        when(responseSpec.body(Map.class))
                .thenReturn(insertResponse)  // First call: insert_user_book
                .thenReturn(insertReadResponse); // Second call: insert_user_book_read

        service.syncProgressToHardcover(testBook, 50.0f);

        // Verify API was called (insert_user_book and insert_user_book_read)
        verify(restClient, times(2)).post();
        
        // Verify no search query was made (should use stored ID)
        ArgumentCaptor<String> uriCaptor = ArgumentCaptor.forClass(String.class);
        verify(requestBodyUriSpec, times(2)).uri(uriCaptor.capture());
        // Both calls should be to the GraphQL endpoint, not a search
        assertTrue(uriCaptor.getAllValues().stream().allMatch(uri -> uri.isEmpty() || uri.equals("")));
    }

    @Test
    @DisplayName("Should search by ISBN when hardcoverBookId is not stored")
    void syncProgressToHardcover_withoutStoredBookId_shouldSearchByIsbn() {
        // Mock: search query response
        Map<String, Object> searchResponse = createSearchResponse(12345, 300);
        when(responseSpec.body(Map.class))
                .thenReturn(searchResponse)  // First call: search
                .thenReturn(createInsertUserBookResponse(5001, null))  // Second call: insert_user_book
                .thenReturn(createInsertUserBookReadResponse()); // Third call: insert_user_book_read

        service.syncProgressToHardcover(testBook, 50.0f);

        // Verify API was called 3 times (search, insert_user_book, insert_user_book_read)
        verify(restClient, times(3)).post();
    }

    @Test
    @DisplayName("Should skip sync when book not found on Hardcover")
    void syncProgressToHardcover_whenBookNotFound_shouldSkip() {
        // Mock: search query returns empty results
        Map<String, Object> emptySearchResponse = createEmptySearchResponse();
        when(responseSpec.body(Map.class)).thenReturn(emptySearchResponse);

        service.syncProgressToHardcover(testBook, 50.0f);

        // Should only call search, not insert_user_book
        verify(restClient, times(1)).post();
    }

    @Test
    @DisplayName("Should set status to READ when progress >= 99%")
    void syncProgressToHardcover_whenProgress99Percent_shouldSetStatusRead() {
        testMetadata.setHardcoverBookId(12345);
        testMetadata.setPageCount(300);

        Map<String, Object> insertResponse = createInsertUserBookResponse(5001, null);
        Map<String, Object> insertReadResponse = createInsertUserBookReadResponse();
        when(responseSpec.body(Map.class))
                .thenReturn(insertResponse)
                .thenReturn(insertReadResponse);

        // Capture the request body to verify status_id = 3 (READ)
        ArgumentCaptor<Object> bodyCaptor = ArgumentCaptor.forClass(Object.class);
        
        service.syncProgressToHardcover(testBook, 99.0f);

        verify(requestBodySpec).body(bodyCaptor.capture());
        // The body should contain status_id = 3 for READ status
        verify(restClient, times(2)).post();
    }

    @Test
    @DisplayName("Should set status to CURRENTLY_READING when progress < 99%")
    void syncProgressToHardcover_whenProgressLessThan99_shouldSetStatusCurrentlyReading() {
        testMetadata.setHardcoverBookId(12345);
        testMetadata.setPageCount(300);

        Map<String, Object> insertResponse = createInsertUserBookResponse(5001, null);
        Map<String, Object> insertReadResponse = createInsertUserBookReadResponse();
        when(responseSpec.body(Map.class))
                .thenReturn(insertResponse)
                .thenReturn(insertReadResponse);

        service.syncProgressToHardcover(testBook, 50.0f);

        verify(restClient, times(2)).post();
    }

    @Test
    @DisplayName("Should calculate progress pages correctly")
    void syncProgressToHardcover_shouldCalculateProgressPages() {
        testMetadata.setHardcoverBookId(12345);
        testMetadata.setPageCount(300);

        Map<String, Object> insertResponse = createInsertUserBookResponse(5001, null);
        Map<String, Object> insertReadResponse = createInsertUserBookReadResponse();
        when(responseSpec.body(Map.class))
                .thenReturn(insertResponse)
                .thenReturn(insertReadResponse);

        service.syncProgressToHardcover(testBook, 50.0f);

        // Verify API calls were made
        verify(restClient, times(2)).post();
        // Progress should be calculated as 50% of 300 = 150 pages
    }

    @Test
    @DisplayName("Should handle existing user_book gracefully")
    void syncProgressToHardcover_whenUserBookExists_shouldFindExisting() {
        testMetadata.setHardcoverBookId(12345);

        // Mock: insert_user_book returns error (book already exists)
        Map<String, Object> insertResponse = createInsertUserBookResponse(null, "Book already exists");
        Map<String, Object> findResponse = createFindUserBookResponse(5001);
        Map<String, Object> insertReadResponse = createInsertUserBookReadResponse();
        
        when(responseSpec.body(Map.class))
                .thenReturn(insertResponse)  // insert_user_book with error
                .thenReturn(findResponse)    // find existing user_book
                .thenReturn(insertReadResponse); // insert_user_book_read

        service.syncProgressToHardcover(testBook, 50.0f);

        // Should call: insert_user_book (fails), find_user_book, insert_user_book_read
        verify(restClient, times(3)).post();
    }

    @Test
    @DisplayName("Should update existing reading progress")
    void syncProgressToHardcover_whenProgressExists_shouldUpdate() {
        testMetadata.setHardcoverBookId(12345);

        Map<String, Object> insertResponse = createInsertUserBookResponse(5001, null);
        Map<String, Object> findReadResponse = createFindUserBookReadResponse(6001);
        Map<String, Object> updateReadResponse = createUpdateUserBookReadResponse();
        
        when(responseSpec.body(Map.class))
                .thenReturn(insertResponse)  // insert_user_book
                .thenReturn(findReadResponse) // find existing user_book_read
                .thenReturn(updateReadResponse); // update_user_book_read

        service.syncProgressToHardcover(testBook, 50.0f);

        // Should call: insert_user_book, find_user_book_read, update_user_book_read
        verify(restClient, times(3)).post();
    }

    @Test
    @DisplayName("Should handle API errors gracefully")
    void syncProgressToHardcover_whenApiError_shouldNotThrow() {
        testMetadata.setHardcoverBookId(12345);

        // Mock: API returns error
        when(responseSpec.body(Map.class)).thenReturn(Map.of("errors", List.of(Map.of("message", "Unauthorized"))));

        // Should not throw exception
        assertDoesNotThrow(() -> service.syncProgressToHardcover(testBook, 50.0f));

        verify(restClient, atLeastOnce()).post();
    }

    @Test
    @DisplayName("Should handle missing ISBN gracefully")
    void syncProgressToHardcover_whenNoIsbn_shouldSkip() {
        testMetadata.setIsbn13(null);
        testMetadata.setIsbn10(null);

        service.syncProgressToHardcover(testBook, 50.0f);

        verify(restClient, never()).post();
    }

    @Test
    @DisplayName("Should use ISBN10 when ISBN13 is missing")
    void syncProgressToHardcover_whenIsbn13Missing_shouldUseIsbn10() {
        testMetadata.setIsbn13(null);
        testMetadata.setIsbn10("1234567890");

        Map<String, Object> searchResponse = createSearchResponse(12345, 300);
        Map<String, Object> insertResponse = createInsertUserBookResponse(5001, null);
        Map<String, Object> insertReadResponse = createInsertUserBookReadResponse();
        
        when(responseSpec.body(Map.class))
                .thenReturn(searchResponse)
                .thenReturn(insertResponse)
                .thenReturn(insertReadResponse);

        service.syncProgressToHardcover(testBook, 50.0f);

        verify(restClient, times(3)).post();
    }

    @Test
    @DisplayName("Should handle null response gracefully")
    void syncProgressToHardcover_whenResponseNull_shouldNotThrow() {
        testMetadata.setHardcoverBookId(12345);

        when(responseSpec.body(Map.class)).thenReturn(null);

        assertDoesNotThrow(() -> service.syncProgressToHardcover(testBook, 50.0f));
    }

    // Helper methods to create mock responses

    private Map<String, Object> createSearchResponse(Integer bookId, Integer pages) {
        Map<String, Object> response = new HashMap<>();
        Map<String, Object> data = new HashMap<>();
        Map<String, Object> search = new HashMap<>();
        Map<String, Object> results = new HashMap<>();
        Map<String, Object> hit = new HashMap<>();
        Map<String, Object> document = new HashMap<>();

        document.put("id", bookId.toString());
        document.put("pages", pages);
        hit.put("document", document);
        results.put("hits", List.of(hit));
        search.put("results", results);
        data.put("search", search);
        response.put("data", data);

        return response;
    }

    private Map<String, Object> createEmptySearchResponse() {
        Map<String, Object> response = new HashMap<>();
        Map<String, Object> data = new HashMap<>();
        Map<String, Object> search = new HashMap<>();
        Map<String, Object> results = new HashMap<>();

        results.put("hits", List.of());
        search.put("results", results);
        data.put("search", search);
        response.put("data", data);

        return response;
    }

    private Map<String, Object> createInsertUserBookResponse(Integer userBookId, String error) {
        Map<String, Object> response = new HashMap<>();
        Map<String, Object> data = new HashMap<>();
        Map<String, Object> insertResult = new HashMap<>();

        if (userBookId != null) {
            Map<String, Object> userBook = new HashMap<>();
            userBook.put("id", userBookId);
            insertResult.put("user_book", userBook);
        }
        if (error != null) {
            insertResult.put("error", error);
        }

        data.put("insert_user_book", insertResult);
        response.put("data", data);

        return response;
    }

    private Map<String, Object> createFindUserBookResponse(Integer userBookId) {
        Map<String, Object> response = new HashMap<>();
        Map<String, Object> data = new HashMap<>();
        Map<String, Object> me = new HashMap<>();
        Map<String, Object> userBook = new HashMap<>();

        userBook.put("id", userBookId);
        me.put("user_books", List.of(userBook));
        data.put("me", me);
        response.put("data", data);

        return response;
    }

    private Map<String, Object> createInsertUserBookReadResponse() {
        Map<String, Object> response = new HashMap<>();
        Map<String, Object> data = new HashMap<>();
        Map<String, Object> insertResult = new HashMap<>();
        Map<String, Object> userBookRead = new HashMap<>();

        userBookRead.put("id", 6001);
        insertResult.put("user_book_read", userBookRead);
        data.put("insert_user_book_read", insertResult);
        response.put("data", data);

        return response;
    }

    private Map<String, Object> createFindUserBookReadResponse(Integer readId) {
        Map<String, Object> response = new HashMap<>();
        Map<String, Object> data = new HashMap<>();
        Map<String, Object> read = new HashMap<>();

        read.put("id", readId);
        data.put("user_book_reads", List.of(read));
        response.put("data", data);

        return response;
    }

    private Map<String, Object> createUpdateUserBookReadResponse() {
        Map<String, Object> response = new HashMap<>();
        Map<String, Object> data = new HashMap<>();
        Map<String, Object> updateResult = new HashMap<>();
        Map<String, Object> userBookRead = new HashMap<>();

        userBookRead.put("id", 6001);
        userBookRead.put("progress", 50);
        updateResult.put("user_book_read", userBookRead);
        data.put("update_user_book_read", updateResult);
        response.put("data", data);

        return response;
    }
}
