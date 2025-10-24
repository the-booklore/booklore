package com.adityachandel.booklore.controller;

import com.adityachandel.booklore.config.security.service.AuthenticationService;
import com.adityachandel.booklore.model.dto.BookLoreUser;
import com.adityachandel.booklore.model.dto.UploadResponse;
import com.adityachandel.booklore.model.dto.UrlRequest;
import com.adityachandel.booklore.service.BackgroundUploadService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@Tag(name = "Background Upload", description = "Endpoints for uploading and managing background images")
@RestController
@RequestMapping("/api/v1/background")
@RequiredArgsConstructor
public class BackgroundUploadController {

    private final BackgroundUploadService backgroundUploadService;
    private final AuthenticationService authenticationService;

    @Operation(summary = "Upload background image file", description = "Upload a new background image file for the authenticated user.")
    @ApiResponse(responseCode = "200", description = "Background image uploaded successfully")
    @PostMapping("/upload")
    public ResponseEntity<UploadResponse> uploadFile(
            @Parameter(description = "Background image file") @RequestParam("file") MultipartFile file) {
        try {
            BookLoreUser authenticatedUser = authenticationService.getAuthenticatedUser();
            UploadResponse response = backgroundUploadService.uploadBackgroundFile(file, authenticatedUser.getId());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
    }

    @Operation(summary = "Upload background image from URL", description = "Upload a new background image from a URL for the authenticated user.")
    @ApiResponse(responseCode = "200", description = "Background image uploaded successfully")
    @PostMapping("/url")
    public ResponseEntity<UploadResponse> uploadUrl(
            @Parameter(description = "URL request containing the image URL") @RequestBody UrlRequest request) {
        try {
            BookLoreUser authenticatedUser = authenticationService.getAuthenticatedUser();
            UploadResponse response = backgroundUploadService.uploadBackgroundFromUrl(request.getUrl(), authenticatedUser.getId());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
    }

    @Operation(summary = "Reset background to default", description = "Reset the background image to the default for the authenticated user.")
    @ApiResponse(responseCode = "200", description = "Background reset to default successfully")
    @DeleteMapping
    public ResponseEntity<Void> resetToDefault() {
        try {
            BookLoreUser authenticatedUser = authenticationService.getAuthenticatedUser();
            backgroundUploadService.resetToDefault(authenticatedUser.getId());
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
    }
}