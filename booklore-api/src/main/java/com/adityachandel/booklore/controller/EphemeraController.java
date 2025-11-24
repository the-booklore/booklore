package com.adityachandel.booklore.controller;

import com.adityachandel.booklore.model.dto.BookLoreUser;
import com.adityachandel.booklore.service.ephemera.EphemeraProxyService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/ephemera")
@Tag(name = "Ephemera Integration", description = "Secure proxy for the Ephemera book processing service")
public class EphemeraController {

    private final EphemeraProxyService ephemeraProxyService;

    @Operation(summary = "Forward request to Ephemera", description = "Routes authenticated Booklore traffic to the internal Ephemera service")
    @ApiResponse(responseCode = "200", description = "Ephemera responded successfully")
    @PreAuthorize("@securityUtil.canManipulateLibrary() or @securityUtil.isAdmin()")
    @RequestMapping(
            value = "/**",
            method = {
                    RequestMethod.GET,
                    RequestMethod.POST,
                    RequestMethod.PUT,
                    RequestMethod.DELETE,
                    RequestMethod.PATCH,
                    RequestMethod.HEAD,
                    RequestMethod.OPTIONS
            }
    )
    public ResponseEntity<byte[]> proxyRequest(Authentication authentication) {
        BookLoreUser user = authentication != null && authentication.getPrincipal() instanceof BookLoreUser principal
                ? principal
                : null;
        if (user == null) {
            log.warn("Ephemera proxy invoked without BookLoreUser principal");
        }
        return ephemeraProxyService.forward(user);
    }
}

