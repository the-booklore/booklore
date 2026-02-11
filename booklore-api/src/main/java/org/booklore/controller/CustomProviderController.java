package org.booklore.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.AllArgsConstructor;
import org.booklore.model.dto.BookMetadata;
import org.booklore.model.dto.external.ExternalProviderCapabilities;
import org.booklore.model.dto.settings.CustomMetadataProviderConfig;
import org.booklore.service.metadata.BookMetadataService;
import org.booklore.service.metadata.parser.custom.CustomProviderRegistry;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Custom Metadata Providers", description = "Endpoints for managing and using custom external metadata providers")
@AllArgsConstructor
@RestController
@RequestMapping("/api/v1/custom-providers")
public class CustomProviderController {

    private final CustomProviderRegistry customProviderRegistry;
    private final BookMetadataService bookMetadataService;

    @Operation(summary = "Validate a custom provider", description = "Test connection to a custom metadata provider by fetching its capabilities. Requires admin.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Provider is reachable; capabilities returned"),
        @ApiResponse(responseCode = "502", description = "Provider is unreachable or returned an error")
    })
    @PostMapping("/validate")
    @PreAuthorize("@securityUtil.isAdmin()")
    public ResponseEntity<ExternalProviderCapabilities> validateProvider(
            @Parameter(description = "Provider configuration to validate") @RequestBody CustomMetadataProviderConfig config) {
        ExternalProviderCapabilities capabilities = customProviderRegistry.validateProvider(config);
        if (capabilities == null) {
            return ResponseEntity.status(502).build();
        }
        return ResponseEntity.ok(capabilities);
    }

    @Operation(summary = "Refresh the custom provider registry", description = "Reload all custom provider instances from the current settings. Requires admin.")
    @ApiResponse(responseCode = "204", description = "Registry refreshed successfully")
    @PostMapping("/refresh")
    @PreAuthorize("@securityUtil.isAdmin()")
    public ResponseEntity<Void> refreshRegistry() {
        customProviderRegistry.refresh();
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Get detailed metadata from a custom provider", description = "Fetch full metadata details for a specific item from a custom provider. Requires metadata edit permission or admin.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Detailed metadata returned successfully"),
        @ApiResponse(responseCode = "404", description = "Provider or item not found")
    })
    @GetMapping("/{customProviderId}/metadata/{providerItemId}")
    @PreAuthorize("@securityUtil.canEditMetadata() or @securityUtil.isAdmin()")
    public ResponseEntity<BookMetadata> getDetailedCustomProviderMetadata(
            @Parameter(description = "Custom provider config ID (UUID)") @PathVariable String customProviderId,
            @Parameter(description = "Provider-specific item ID") @PathVariable String providerItemId) {
        BookMetadata metadata = bookMetadataService.getDetailedCustomProviderMetadata(customProviderId, providerItemId);
        if (metadata == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(metadata);
    }
}
