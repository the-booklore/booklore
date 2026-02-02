package org.booklore.controller;

import org.booklore.config.security.annotation.CheckBookAccess;
import org.booklore.model.dto.request.CbzConversionRequest;
import org.booklore.model.dto.response.CbzConversionResponse;
import org.booklore.model.enums.EReaderProfile;
import org.booklore.service.conversion.StandaloneCbzConversionService;
import com.github.junrar.exception.RarException;
import freemarker.template.TemplateException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * REST controller for standalone CBZ to EPUB conversion.
 * Allows users to convert comic book archives to EPUB format with custom resolution settings.
 */
@Tag(name = "Book Conversion", description = "Endpoints for converting books between formats")
@Slf4j
@RestController
@RequestMapping("/api/v1/conversion")
@RequiredArgsConstructor
public class BookConversionController {
    
    private final StandaloneCbzConversionService conversionService;
    
    /**
     * Get available e-reader device profiles
     */
    @Operation(summary = "Get device profiles", 
            description = "Retrieve list of available e-reader device profiles with their screen resolutions.")
    @ApiResponse(responseCode = "200", description = "Device profiles retrieved successfully")
    @GetMapping("/profiles")
    public ResponseEntity<List<DeviceProfileDto>> getDeviceProfiles() {
        List<DeviceProfileDto> profiles = Arrays.stream(EReaderProfile.values())
                .map(profile -> new DeviceProfileDto(
                        profile.name(),
                        profile.getDisplayName(),
                        profile.getWidth(),
                        profile.getHeight(),
                        profile.supportsCustomResolution()
                ))
                .collect(Collectors.toList());
        
        return ResponseEntity.ok(profiles);
    }
    
    /**
     * Convert CBZ to EPUB with specified resolution
     */
    @Operation(summary = "Convert CBZ to EPUB", 
            description = "Convert a CBZ/CBR/CB7 book to EPUB format with custom resolution settings. " +
                         "The converted EPUB will be created as a new book in the same library. " +
                         "Requires library management permission or admin.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Conversion completed successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid request or book is not CBX format"),
            @ApiResponse(responseCode = "403", description = "Forbidden - requires library management permission"),
            @ApiResponse(responseCode = "404", description = "Book not found")
    })
    @PostMapping("/cbz-to-epub")
    @PreAuthorize("@securityUtil.canManageLibrary() or @securityUtil.isAdmin()")
    @CheckBookAccess(bookIdParam = "request.bookId", checkFromRequestBody = true)
    public ResponseEntity<CbzConversionResponse> convertCbzToEpub(
            @Parameter(description = "Conversion request with device profile and optional custom dimensions")
            @RequestBody @Valid CbzConversionRequest request) throws IOException, TemplateException, RarException {
        
        log.info("Received CBZ to EPUB conversion request for book {}", request.getBookId());
        
        CbzConversionResponse response = conversionService.convertCbzToEpub(request);
        
        log.info("Successfully converted CBZ to EPUB: {}", response.getFileName());
        
        return ResponseEntity.ok(response);
    }
    
    /**
     * DTO for device profile information
     */
    public record DeviceProfileDto(
            String value,
            String displayName,
            int width,
            int height,
            boolean supportsCustom
    ) {}
}
