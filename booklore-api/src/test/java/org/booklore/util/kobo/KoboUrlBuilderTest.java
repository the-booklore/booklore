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
        assertFalse(result.contains(":443"));
        assertTrue(result.contains("/api/kobo/" + token + "/v1/books/" + bookId + "/download"));
    }

    @Test
    void testHostHeaderWithoutPort_restoresLocalPort() {
        // Kobo devices send "Host: 192.168.1.100" without port.
        // Tomcat resolves missing port to 80 (HTTP default), which Spring strips.
        // The fix should restore the actual listening port from request.getLocalPort().
        mockRequest.setServerName("192.168.1.100");
        mockRequest.setServerPort(80);
        mockRequest.setLocalPort(6060);

        String result = koboUrlBuilder.downloadUrl("testToken", 123L);

        assertTrue(result.startsWith("http://192.168.1.100:6060/"));
        assertTrue(result.contains("/api/kobo/testToken/v1/books/123/download"));
    }

    @Test
    void testHostHeaderWithoutPort_imageUrls() {
        mockRequest.setServerName("192.168.1.100");
        mockRequest.setServerPort(80);
        mockRequest.setLocalPort(6060);

        assertTrue(koboUrlBuilder.imageUrlTemplate("t").startsWith("http://192.168.1.100:6060/"));
        assertTrue(koboUrlBuilder.imageUrlQualityTemplate("t").startsWith("http://192.168.1.100:6060/"));
        assertTrue(koboUrlBuilder.librarySyncUrl("t").startsWith("http://192.168.1.100:6060/"));
    }

    @Test
    void testReverseProxy_xForwardedProto_doesNotOverridePort() {
        mockRequest.setScheme("https");
        mockRequest.setServerName("books.example.com");
        mockRequest.setServerPort(443);
        mockRequest.setLocalPort(6060);
        mockRequest.addHeader("X-Forwarded-Proto", "https");

        String result = koboUrlBuilder.downloadUrl("testToken", 123L);

        assertTrue(result.startsWith("https://books.example.com/"));
        assertFalse(result.contains(":6060"));
    }

    @Test
    void testReverseProxy_xForwardedHost_doesNotOverridePort() {
        mockRequest.setScheme("https");
        mockRequest.setServerName("books.example.com");
        mockRequest.setServerPort(443);
        mockRequest.setLocalPort(6060);
        mockRequest.addHeader("X-Forwarded-Host", "books.example.com");

        String result = koboUrlBuilder.downloadUrl("testToken", 123L);

        assertTrue(result.startsWith("https://books.example.com/"));
        assertFalse(result.contains(":6060"));
    }

    @Test
    void testReverseProxy_xForwardedPort_doesNotOverridePort() {
        mockRequest.setScheme("https");
        mockRequest.setServerName("books.example.com");
        mockRequest.setServerPort(443);
        mockRequest.setLocalPort(6060);
        mockRequest.addHeader("X-Forwarded-Port", "443");

        String result = koboUrlBuilder.downloadUrl("testToken", 123L);

        assertTrue(result.startsWith("https://books.example.com/"));
        assertFalse(result.contains(":6060"));
    }

    @Test
    void testReverseProxy_forwardedHeader_doesNotOverridePort() {
        mockRequest.setScheme("https");
        mockRequest.setServerName("books.example.com");
        mockRequest.setServerPort(443);
        mockRequest.setLocalPort(6060);
        mockRequest.addHeader("Forwarded", "proto=https;host=books.example.com");

        String result = koboUrlBuilder.downloadUrl("testToken", 123L);

        assertTrue(result.startsWith("https://books.example.com/"));
        assertFalse(result.contains(":6060"));
    }

    @Test
    void testReverseProxy_nonStandardPort_preserved() {
        mockRequest.setScheme("https");
        mockRequest.setServerName("books.example.com");
        mockRequest.setServerPort(8443);
        mockRequest.setLocalPort(6060);
        mockRequest.addHeader("X-Forwarded-Port", "8443");

        String result = koboUrlBuilder.downloadUrl("testToken", 123L);

        assertTrue(result.startsWith("https://books.example.com:8443/"));
    }

    @Test
    void testServerOnPort80_noPortAdded() {
        mockRequest.setServerName("192.168.1.100");
        mockRequest.setServerPort(80);
        mockRequest.setLocalPort(80);

        String result = koboUrlBuilder.downloadUrl("testToken", 123L);

        assertTrue(result.startsWith("http://192.168.1.100/"));
        assertFalse(result.contains(":80"));
    }

    @Test
    void testServerOnPort443_noPortAdded() {
        mockRequest.setScheme("https");
        mockRequest.setServerName("192.168.1.100");
        mockRequest.setServerPort(443);
        mockRequest.setLocalPort(443);

        String result = koboUrlBuilder.downloadUrl("testToken", 123L);

        assertTrue(result.startsWith("https://192.168.1.100/"));
        assertFalse(result.contains(":443"));
    }
}
