package org.booklore.util.kobo;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import static org.junit.jupiter.api.Assertions.*;

class KoboUrlBuilderTest {

    private KoboUrlBuilder koboUrlBuilder;
    private MockHttpServletRequest mockRequest;

    @BeforeEach
    void setUp() {
        mockRequest = new MockHttpServletRequest();
        mockRequest.setScheme("http");
        mockRequest.setServerName("localhost");
        mockRequest.setServerPort(6060);
        mockRequest.setContextPath("");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(mockRequest));

        koboUrlBuilder = new KoboUrlBuilder();
    }

    @AfterEach
    void tearDown() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void testDownloadUrl() {
        String token = "testToken";
        Long bookId = 123L;

        String result = koboUrlBuilder.downloadUrl(token, bookId);

        assertNotNull(result);
        assertTrue(result.contains("/api/kobo/" + token + "/v1/books/" + bookId + "/download"));
        assertTrue(result.startsWith("http://"));
    }

    @Test
    void testDownloadUrlPreservesPort() {
        String token = "testToken";
        Long bookId = 123L;

        String result = koboUrlBuilder.downloadUrl(token, bookId);

        assertTrue(result.startsWith("http://localhost:6060/"));
    }

    @Test
    void testImageUrlTemplate() {
        String token = "testToken";

        String result = koboUrlBuilder.imageUrlTemplate(token);

        assertNotNull(result);
        assertTrue(result.contains("/api/kobo/" + token + "/v1/books/"));
        assertTrue(result.contains("{ImageId}"));
        assertTrue(result.contains("{Width}"));
        assertTrue(result.contains("{Height}"));
        assertTrue(result.contains("image.jpg"));
    }

    @Test
    void testImageUrlQualityTemplate() {
        String token = "testToken";

        String result = koboUrlBuilder.imageUrlQualityTemplate(token);

        assertNotNull(result);
        assertTrue(result.contains("/api/kobo/" + token + "/v1/books/"));
        assertTrue(result.contains("{ImageId}"));
        assertTrue(result.contains("{Width}"));
        assertTrue(result.contains("{Height}"));
        assertTrue(result.contains("{Quality}"));
        assertTrue(result.contains("{IsGreyscale}"));
        assertTrue(result.contains("image.jpg"));
    }

    @Test
    void testLibrarySyncUrl() {
        String token = "testToken";

        String result = koboUrlBuilder.librarySyncUrl(token);

        assertNotNull(result);
        assertTrue(result.contains("/api/kobo/" + token + "/v1/library/sync"));
    }

    @Test
    void testUrlBuilderWithIpAddress() {
        mockRequest.setServerName("192.168.1.100");
        mockRequest.setServerPort(6060);

        String token = "testToken";
        Long bookId = 123L;

        String result = koboUrlBuilder.downloadUrl(token, bookId);

        assertTrue(result.startsWith("http://192.168.1.100:6060/"));
        assertTrue(result.contains("/api/kobo/" + token + "/v1/books/" + bookId + "/download"));
    }

    @Test
    void testUrlBuilderWithDomainName() {
        mockRequest.setScheme("https");
        mockRequest.setServerName("books.example.com");
        mockRequest.setServerPort(443);

        String token = "testToken";
        Long bookId = 123L;

        String result = koboUrlBuilder.downloadUrl(token, bookId);

        assertTrue(result.startsWith("https://books.example.com"));
        assertTrue(result.contains("/api/kobo/" + token + "/v1/books/" + bookId + "/download"));
    }
}
