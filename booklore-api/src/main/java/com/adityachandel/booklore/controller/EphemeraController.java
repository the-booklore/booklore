package com.adityachandel.booklore.controller;

import com.adityachandel.booklore.service.ephemera.EphemeraProxyService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RequiredArgsConstructor
@RestController
@RequestMapping("/api/ephemera")
@Tag(name = "Ephemera Integration", description = "Endpoints for proxying requests to the Ephemera book processing application")
public class EphemeraController {

    private final EphemeraProxyService ephemeraProxyService;

    @Operation(summary = "Proxy request to Ephemera", description = "Proxies all requests to the internal Ephemera application")
    @ApiResponse(responseCode = "200", description = "Request proxied successfully")
    @PreAuthorize("hasPermission(null, 'MANAGE_LIBRARY') or hasRole('ADMIN')")
    @RequestMapping(value = "/**", method = {RequestMethod.GET, RequestMethod.POST, RequestMethod.PUT, RequestMethod.DELETE, RequestMethod.PATCH, RequestMethod.HEAD, RequestMethod.OPTIONS})
    public ResponseEntity<?> catchAll(HttpServletRequest request, @RequestBody(required = false) Object body) {
        log.debug("Proxying Ephemera request: {} {}", request.getMethod(), request.getRequestURI());
        return ephemeraProxyService.proxyCurrentRequest(body);
    }
}