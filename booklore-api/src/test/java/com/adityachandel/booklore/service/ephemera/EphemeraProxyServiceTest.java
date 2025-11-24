package com.adityachandel.booklore.service.ephemera;

import com.adityachandel.booklore.model.dto.BookLoreUser;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EphemeraProxyServiceTest {

    private MockWebServer mockWebServer;
    private EphemeraProxyService ephemeraProxyService;
    private EphemeraProperties properties;

    @BeforeEach
    void setUp() throws IOException {
        mockWebServer = new MockWebServer();
        mockWebServer.start();

        properties = new EphemeraProperties();
        properties.setBaseUrl(mockWebServer.url("/").toString());
        properties.setAllowedPaths(List.of("/**"));
        properties.setAllowedMethods(List.of("GET", "POST"));
        properties.setConnectTimeoutMs(1000);
        properties.setReadTimeoutMs(1000);

        ephemeraProxyService = new EphemeraProxyService(properties);
    }

    @AfterEach
    void tearDown() throws IOException {
        RequestContextHolder.resetRequestAttributes();
        mockWebServer.shutdown();
    }

    @Test
    void forwardsRequestWithUserHeaders() throws Exception {
        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .setBody("{\"status\":\"ok\"}")
                .addHeader(HttpHeaders.CONTENT_TYPE, "application/json"));

        MockHttpServletRequest servletRequest = new MockHttpServletRequest();
        servletRequest.setMethod("GET");
        servletRequest.setRequestURI("/api/v1/ephemera/api/status");
        servletRequest.setQueryString("foo=bar");
        servletRequest.addHeader(HttpHeaders.ACCEPT, "application/json");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(servletRequest));

        ResponseEntity<byte[]> response = ephemeraProxyService.forward(sampleUser());

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(new String(response.getBody())).contains("ok");

        RecordedRequest recordedRequest = mockWebServer.takeRequest(1, TimeUnit.SECONDS);
        assertThat(recordedRequest.getPath()).isEqualTo("/api/status?foo=bar");
        assertThat(recordedRequest.getHeader("X-Booklore-User")).isEqualTo("test-user");
    }

    @Test
    void rejectsDisallowedMethod() {
        properties.setAllowedMethods(List.of("GET"));
        MockHttpServletRequest servletRequest = new MockHttpServletRequest();
        servletRequest.setMethod("POST");
        servletRequest.setRequestURI("/api/v1/ephemera/api/books");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(servletRequest));

        assertThatThrownBy(() -> ephemeraProxyService.forward(sampleUser()))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("method not allowed");
    }

    @Test
    void rejectsDisallowedPath() {
        properties.setAllowedPaths(List.of("/status/**"));
        MockHttpServletRequest servletRequest = new MockHttpServletRequest();
        servletRequest.setMethod("GET");
        servletRequest.setRequestURI("/api/v1/ephemera/api/private");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(servletRequest));

        assertThatThrownBy(() -> ephemeraProxyService.forward(sampleUser()))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("not whitelisted");
    }

    private BookLoreUser sampleUser() {
        BookLoreUser user = new BookLoreUser();
        BookLoreUser.UserPermissions permissions = new BookLoreUser.UserPermissions();
        permissions.setAdmin(true);
        user.setPermissions(permissions);
        user.setUsername("test-user");
        user.setEmail("test@example.com");
        return user;
    }
}

