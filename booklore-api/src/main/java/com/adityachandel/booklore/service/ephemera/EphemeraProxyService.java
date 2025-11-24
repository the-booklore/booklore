package com.adityachandel.booklore.service.ephemera;

import com.adityachandel.booklore.util.RequestUtils;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Collections;

@Slf4j
@Component
@RequiredArgsConstructor
public class EphemeraProxyService {

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(30))
            .build();
    private final ObjectMapper objectMapper;
    private final EphemeraProperties ephemeraProperties;

    public ResponseEntity<?> proxyCurrentRequest(Object body) {
        HttpServletRequest request = RequestUtils.getCurrentRequest();
        String path = request.getRequestURI().replaceFirst("^/api/ephemera", "");
        
        return executeProxyRequest(request, body, path);
    }

    private ResponseEntity<?> executeProxyRequest(HttpServletRequest request, Object body, String path) {
        try {
            String ephemeraBaseUrl = ephemeraProperties.getBaseUrl();
            if (!ephemeraBaseUrl.endsWith("/")) {
                ephemeraBaseUrl += "/";
            }

            String queryString = request.getQueryString();
            String targetUrl = ephemeraBaseUrl + (path.startsWith("/") ? path.substring(1) : path);
            
            if (queryString != null && !queryString.isBlank()) {
                targetUrl += "?" + queryString;
            }

            URI uri = URI.create(targetUrl);
            log.debug("Ephemera proxy URL: {}", uri);

            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(uri)
                    .timeout(Duration.ofSeconds(30))
                    .method(request.getMethod(), createBodyPublisher(request, body));

            // Forward relevant headers
            Collections.list(request.getHeaderNames()).forEach(headerName -> {
                if (shouldForwardHeader(headerName)) {
                    Collections.list(request.getHeaders(headerName))
                            .forEach(value -> builder.header(headerName, value));
                }
            });

            HttpRequest httpRequest = builder.build();
            HttpResponse<byte[]> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofByteArray());

            return buildResponseEntity(response);

        } catch (Exception e) {
            log.error("Failed to proxy request to Ephemera", e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to proxy request to Ephemera", e);
        }
    }

    private HttpRequest.BodyPublisher createBodyPublisher(HttpServletRequest request, Object body) {
        if (body == null) {
            return HttpRequest.BodyPublishers.noBody();
        }
        
        try {
            String contentType = request.getContentType();
            if (contentType != null && contentType.contains("application/json")) {
                String bodyString = objectMapper.writeValueAsString(body);
                return HttpRequest.BodyPublishers.ofString(bodyString);
            } else {
                // For non-JSON bodies, pass through as bytes
                if (body instanceof byte[] bytes) {
                    return HttpRequest.BodyPublishers.ofByteArray(bytes);
                } else {
                    String bodyString = body.toString();
                    return HttpRequest.BodyPublishers.ofString(bodyString);
                }
            }
        } catch (Exception e) {
            log.warn("Failed to serialize request body, using empty body", e);
            return HttpRequest.BodyPublishers.noBody();
        }
    }

    private ResponseEntity<?> buildResponseEntity(HttpResponse<byte[]> response) {
        HttpHeaders responseHeaders = new HttpHeaders();
        response.headers().map().forEach((key, values) -> {
            if (shouldForwardResponseHeader(key)) {
                responseHeaders.put(key, values);
            }
        });

        byte[] responseBody = response.body();
        MediaType contentType = determineContentType(responseHeaders, responseBody);

        if (contentType != null && contentType.getType().equals("text")) {
            return new ResponseEntity<>(new String(responseBody), responseHeaders, HttpStatus.valueOf(response.statusCode()));
        } else {
            Resource resource = new ByteArrayResource(responseBody);
            return new ResponseEntity<>(resource, responseHeaders, HttpStatus.valueOf(response.statusCode()));
        }
    }

    private MediaType determineContentType(HttpHeaders headers, byte[] body) {
        String contentTypeHeader = headers.getFirst(HttpHeaders.CONTENT_TYPE);
        if (contentTypeHeader != null) {
            return MediaType.parseMediaType(contentTypeHeader);
        }
        return MediaType.APPLICATION_OCTET_STREAM;
    }

    private boolean shouldForwardHeader(String headerName) {
        String lowerHeader = headerName.toLowerCase();
        return !lowerHeader.equals("host") && 
               !lowerHeader.equals("content-length") &&
               !lowerHeader.startsWith("sec-") &&
               !lowerHeader.startsWith("proxy-");
    }

    private boolean shouldForwardResponseHeader(String headerName) {
        String lowerHeader = headerName.toLowerCase();
        return !lowerHeader.equals("content-length") &&
               !lowerHeader.startsWith("access-control-");
    }
}