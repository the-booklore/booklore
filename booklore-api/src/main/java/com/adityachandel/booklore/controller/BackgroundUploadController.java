package com.adityachandel.booklore.controller;

import com.adityachandel.booklore.config.security.service.AuthenticationService;
import com.adityachandel.booklore.model.dto.BookLoreUser;
import com.adityachandel.booklore.model.dto.UploadResponse;
import com.adityachandel.booklore.model.dto.UrlRequest;
import com.adityachandel.booklore.service.BackgroundUploadService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/background")
@RequiredArgsConstructor
public class BackgroundUploadController {

    private final BackgroundUploadService backgroundUploadService;
    private final AuthenticationService authenticationService;

    @PostMapping("/upload")
    public ResponseEntity<UploadResponse> uploadFile(@RequestParam("file") MultipartFile file) {
        try {
            BookLoreUser authenticatedUser = authenticationService.getAuthenticatedUser();
            UploadResponse response = backgroundUploadService.uploadBackgroundFile(file, authenticatedUser.getId());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
    }

    @PostMapping("/url")
    public ResponseEntity<UploadResponse> uploadUrl(@RequestBody UrlRequest request) {
        try {
            BookLoreUser authenticatedUser = authenticationService.getAuthenticatedUser();
            UploadResponse response = backgroundUploadService.uploadBackgroundFromUrl(request.getUrl(), authenticatedUser.getId());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
    }

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