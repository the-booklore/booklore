package org.booklore.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.Collections;
import java.util.Set;

/**
 * Transparent proxy for Kobo reading services requests.
 * <p>
 * Kobo devices send reading services requests (annotations, user storage, etc.)
 * directly to the host, ignoring any path in {@code reading_services_host}.
 * These arrive as {@code /api/v3/...} and {@code /api/UserStorage/...} and must
 * be proxied to {@code readingservices.kobo.com}.
 */
@Slf4j
@RestController
public class KoboReadingServicesController {

    private static final String READING_SERVICES_BASE = "https://readingservices.kobo.com";

    private static final Set<String> SKIP_REQUEST_HEADERS = Set.of(
            "host", "cookie", "content-length", "connection",
            "accept-encoding",  // Prevent gzip — Java HttpClient doesn't auto-decompress
            "expect",           // Java HttpClient restricted header
            "upgrade"           // Java HttpClient restricted header
    );

    private static final Set<String> SKIP_RESPONSE_HEADERS = Set.of(
            "connection", "content-encoding", "content-length", "transfer-encoding"
    );

    private final HttpClient httpClient;

    public KoboReadingServicesController() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    @RequestMapping({"/api/v3/**", "/api/UserStorage/**"})
    public void proxy(HttpServletRequest request, HttpServletResponse response) throws IOException {
        String path = request.getRequestURI();
        String queryString = request.getQueryString();
        String url = READING_SERVICES_BASE + path;
        if (queryString != null && !queryString.isEmpty()) {
            url += "?" + queryString;
        }

        String method = request.getMethod();
        byte[] bodyBytes = request.getInputStream().readAllBytes();
        boolean hasBody = bodyBytes.length > 0
                && !"GET".equalsIgnoreCase(method)
                && !"HEAD".equalsIgnoreCase(method);

        HttpRequest.BodyPublisher bodyPublisher = hasBody
                ? HttpRequest.BodyPublishers.ofByteArray(bodyBytes)
                : HttpRequest.BodyPublishers.noBody();

        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(30))
                .method(method, bodyPublisher);

        Collections.list(request.getHeaderNames()).forEach(name -> {
            if (!SKIP_REQUEST_HEADERS.contains(name.toLowerCase())) {
                Collections.list(request.getHeaders(name))
                        .forEach(value -> builder.header(name, value));
            }
        });

        try {
            log.debug("[ReadingServices] Proxying {} {} -> {}", method, path, url);
            HttpResponse<byte[]> koboResponse = httpClient.send(builder.build(),
                    HttpResponse.BodyHandlers.ofByteArray());

            response.setStatus(koboResponse.statusCode());

            koboResponse.headers().map().forEach((key, values) -> {
                if (!SKIP_RESPONSE_HEADERS.contains(key.toLowerCase())) {
                    values.forEach(value -> response.addHeader(key, value));
                }
            });

            byte[] responseBody = koboResponse.body();
            if (responseBody != null && responseBody.length > 0) {
                response.getOutputStream().write(responseBody);
            }

            if (koboResponse.statusCode() >= 400) {
                log.warn("[ReadingServices] Kobo returned {} for {} {}",
                        koboResponse.statusCode(), method, path);
            }
        } catch (HttpTimeoutException e) {
            log.error("[ReadingServices] Timeout proxying {} {}", method, path);
            response.sendError(HttpServletResponse.SC_GATEWAY_TIMEOUT, "Timeout connecting to Kobo reading services");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("[ReadingServices] Interrupted proxying {} {}", method, path);
            response.sendError(HttpServletResponse.SC_BAD_GATEWAY, "Interrupted");
        } catch (Exception e) {
            log.error("[ReadingServices] Failed to proxy {} {}: {}", method, path, e.getMessage());
            response.sendError(HttpServletResponse.SC_BAD_GATEWAY, "Failed to proxy to Kobo reading services");
        }
    }
}
