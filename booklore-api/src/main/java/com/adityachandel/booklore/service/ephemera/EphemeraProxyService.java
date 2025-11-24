package com.adityachandel.booklore.service.ephemera;

import com.adityachandel.booklore.model.dto.BookLoreUser;
import com.adityachandel.booklore.util.RequestUtils;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Slf4j
@Component
public class EphemeraProxyService {

    private static final Set<String> FORWARDED_REQUEST_HEADERS = Set.of(
            HttpHeaders.ACCEPT.toLowerCase(Locale.ROOT),
            HttpHeaders.ACCEPT_LANGUAGE.toLowerCase(Locale.ROOT),
            HttpHeaders.CONTENT_TYPE.toLowerCase(Locale.ROOT),
            HttpHeaders.USER_AGENT.toLowerCase(Locale.ROOT),
            HttpHeaders.COOKIE.toLowerCase(Locale.ROOT),
            "x-requested-with"
    );

    private static final Set<String> RESPONSE_HEADER_WHITELIST = Set.of(
            HttpHeaders.CONTENT_TYPE.toLowerCase(Locale.ROOT),
            HttpHeaders.CONTENT_DISPOSITION.toLowerCase(Locale.ROOT),
            HttpHeaders.CACHE_CONTROL.toLowerCase(Locale.ROOT),
            HttpHeaders.PRAGMA.toLowerCase(Locale.ROOT),
            HttpHeaders.EXPIRES.toLowerCase(Locale.ROOT)
    );

    private final EphemeraProperties properties;
    private final HttpClient httpClient;
    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    public EphemeraProxyService(EphemeraProperties properties) {
        this.properties = properties;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(properties.getConnectTimeoutMs()))
                .build();
    }

    public ResponseEntity<byte[]> forward(BookLoreUser user) {
        HttpServletRequest request = RequestUtils.getCurrentRequest();
        byte[] body = readBody(request);
        validateRequest(request);

        URI targetUri = buildTargetUri(request);
        HttpRequest outboundRequest = buildOutboundRequest(request, body, user, targetUri);

        try {
            HttpResponse<byte[]> response = httpClient.send(outboundRequest, HttpResponse.BodyHandlers.ofByteArray());
            HttpHeaders headers = extractResponseHeaders(response);
            return ResponseEntity.status(response.statusCode()).headers(headers).body(response.body());
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            log.error("Ephemera proxy interrupted", ie);
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Ephemera proxy interrupted", ie);
        } catch (IOException e) {
            log.error("Failed to proxy Ephemera request", e);
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Failed to reach Ephemera service", e);
        }
    }

    private void validateRequest(HttpServletRequest request) {
        String method = request.getMethod();
        List<String> allowedMethods = properties.getAllowedMethods();
        if (allowedMethods.stream().noneMatch(m -> m.equalsIgnoreCase(method))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "HTTP method not allowed for Ephemera");
        }

        String relativePath = resolveRelativePath(request);
        boolean allowedPath = properties.getAllowedPaths().stream()
                .anyMatch(pattern -> pathMatcher.match(pattern, relativePath));

        if (!allowedPath) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Ephemera path is not whitelisted");
        }
    }

    private HttpRequest buildOutboundRequest(HttpServletRequest request, byte[] body, BookLoreUser user, URI targetUri) {
        HttpRequest.BodyPublisher publisher = createBodyPublisher(request.getMethod(), body);
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(targetUri)
                .timeout(Duration.ofMillis(properties.getReadTimeoutMs()))
                .method(request.getMethod(), publisher);

        Collections.list(request.getHeaderNames()).forEach(header -> {
            if (shouldForwardHeader(header)) {
                Collections.list(request.getHeaders(header)).forEach(value -> builder.header(header, value));
            }
        });

        if (properties.isInjectUserHeaders() && user != null) {
            builder.header("X-Booklore-User", user.getUsername());
            if (user.getEmail() != null) {
                builder.header("X-Booklore-User-Email", user.getEmail());
            }
        }

        return builder.build();
    }

    private HttpHeaders extractResponseHeaders(HttpResponse<byte[]> response) {
        HttpHeaders headers = new HttpHeaders();
        response.headers().map().forEach((name, values) -> {
            if (name != null && RESPONSE_HEADER_WHITELIST.contains(name.toLowerCase(Locale.ROOT))) {
                headers.put(name, values);
            }
        });
        return headers;
    }

    private HttpRequest.BodyPublisher createBodyPublisher(String method, byte[] body) {
        if (body == null || body.length == 0 || "GET".equalsIgnoreCase(method) || "DELETE".equalsIgnoreCase(method)) {
            return HttpRequest.BodyPublishers.noBody();
        }
        return HttpRequest.BodyPublishers.ofByteArray(body);
    }

    private boolean shouldForwardHeader(String headerName) {
        if (headerName == null) {
            return false;
        }
        String lower = headerName.toLowerCase(Locale.ROOT);
        return FORWARDED_REQUEST_HEADERS.contains(lower);
    }

    private URI buildTargetUri(HttpServletRequest request) {
        try {
            String baseUrl = properties.getBaseUrl();
            String relativePath = resolveRelativePath(request);
            StringBuilder uriBuilder = new StringBuilder();
            uriBuilder.append(baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl);
            uriBuilder.append(relativePath.startsWith("/") ? relativePath : "/" + relativePath);
            if (request.getQueryString() != null && !request.getQueryString().isBlank()) {
                uriBuilder.append("?").append(request.getQueryString());
            }
            return new URI(uriBuilder.toString());
        } catch (URISyntaxException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid Ephemera target URI", e);
        }
    }

    private String resolveRelativePath(HttpServletRequest request) {
        String path = request.getRequestURI().replaceFirst("^/api/v1/ephemera", "");
        if (path.isEmpty()) {
            return "/";
        }
        return path.startsWith("/") ? path : "/" + path;
    }

    private byte[] readBody(HttpServletRequest request) {
        try {
            byte[] bytes = request.getInputStream().readAllBytes();
            return bytes.length == 0 ? null : bytes;
        } catch (IOException e) {
            log.warn("Unable to read Ephemera request body, sending empty body", e);
            return null;
        }
    }
}

