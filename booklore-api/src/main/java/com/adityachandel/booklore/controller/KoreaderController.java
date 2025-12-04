package com.adityachandel.booklore.controller;

import com.adityachandel.booklore.config.security.userdetails.KoreaderUserDetails;
import com.adityachandel.booklore.model.dto.koreader.KoreaderStatisticsImport;
import com.adityachandel.booklore.model.dto.progress.KoreaderProgress;
import com.adityachandel.booklore.repository.ReadingSessionRepository;
import com.adityachandel.booklore.service.koreader.KoreaderService;
import com.adityachandel.booklore.service.koreader.KoreaderStatisticsImportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Slf4j
@RestController
@AllArgsConstructor
@RequestMapping("/api/koreader")
@Tag(name = "KoReader", description = "Endpoints for KoReader device integration and progress sync")
public class KoreaderController {

    private final KoreaderService koreaderService;
    private final KoreaderStatisticsImportService statisticsImportService;
    private final ReadingSessionRepository sessionRepository;

    @Operation(summary = "Authorize KoReader user", description = "Authorize a user for KoReader sync.")
    @ApiResponse(responseCode = "200", description = "User authorized successfully")
    @GetMapping("/users/auth")
    public ResponseEntity<Map<String, String>> authorizeUser() {
        return koreaderService
            .authorizeUser();
    }

    @Operation(summary = "Create KoReader user (disabled)", description = "Attempt to register a user via KoReader (always forbidden).")
    @ApiResponse(responseCode = "403", description = "User registration forbidden")
    @PostMapping("/users/create")
    public ResponseEntity<?> createUser(
            @Parameter(description = "User data") @RequestBody Map<String, Object> userData) {
        log.warn("Attempt to register user via Koreader blocked: {}", userData);
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "User registration via Koreader is disabled"));
    }

    @Operation(summary = "Get KoReader progress", description = "Retrieve reading progress for a book by its hash.")
    @ApiResponse(responseCode = "200", description = "Progress returned successfully")
    @GetMapping("/syncs/progress/{bookHash}")
    public ResponseEntity<KoreaderProgress> getProgress(
            @Parameter(description = "Book hash") @PathVariable String bookHash) {
        KoreaderProgress progress = koreaderService.getProgress(bookHash);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .body(progress);
    }

    @Operation(summary = "Update KoReader progress", description = "Update reading progress for a book.")
    @ApiResponse(responseCode = "200", description = "Progress updated successfully")
    @PutMapping("/syncs/progress")
    public ResponseEntity<?> updateProgress(
            @Parameter(description = "KoReader progress object") @Valid @RequestBody KoreaderProgress koreaderProgress) {
        koreaderService.saveProgress(koreaderProgress.getDocument(), koreaderProgress);
        return ResponseEntity.ok(Map.of("status", "progress updated"));
    }

    @Operation(summary = "Import KoReader statistics", description = "Import reading statistics and session history from KoReader database.")
    @ApiResponse(responseCode = "200", description = "Statistics imported successfully")
    @ApiResponse(responseCode = "400", description = "Invalid data format")
    @PostMapping("/statistics/import")
    public ResponseEntity<?> importStatistics(
            @Parameter(description = "KoReader statistics data") @Valid @RequestBody KoreaderStatisticsImport statisticsData) {

        // Get the authenticated user
        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        if (!(principal instanceof KoreaderUserDetails details)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "User not authenticated"));
        }

        Long userId = details.getBookLoreUserId();
        log.info("Importing KOReader statistics for user: {}", userId);

        try {
            KoreaderStatisticsImportService.ImportResult result =
                    statisticsImportService.importStatistics(userId, statisticsData);

            return ResponseEntity.ok(Map.of(
                    "status", "success",
                    "message", "Statistics imported successfully",
                    "imported", result.importedSessions,
                    "skipped", result.skippedSessions,
                    "duplicates", result.duplicateSessions,
                    "failed", result.failedSessions,
                    "total_processed", result.getTotalProcessed(),
                    "skipped_book_hashes", result.skippedBookHashes
            ));
        } catch (Exception e) {
            log.error("Failed to import statistics for user {}: {}", userId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to import statistics: " + e.getMessage()));
        }
    }

    @Operation(summary = "Get reading session stats", description = "Get count of stored reading sessions for debugging.")
    @ApiResponse(responseCode = "200", description = "Session stats returned")
    @GetMapping("/statistics/sessions/count")
    public ResponseEntity<?> getSessionCount() {
        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        if (!(principal instanceof KoreaderUserDetails details)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "User not authenticated"));
        }

        Long userId = details.getBookLoreUserId();
        long count = sessionRepository.countByUserId(userId);

        return ResponseEntity.ok(Map.of(
                "userId", userId,
                "sessionCount", count
        ));
    }

    @Operation(summary = "Upload SQLite statistics file", description = "Upload KOReader statistics.sqlite3 file for asynchronous processing.")
    @ApiResponse(responseCode = "202", description = "File accepted for processing")
    @ApiResponse(responseCode = "400", description = "Invalid file format")
    @PostMapping(value = "/statistics/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> uploadStatisticsFile(
            @Parameter(description = "SQLite statistics file") @RequestParam("file") org.springframework.web.multipart.MultipartFile file) {

        // Get the authenticated user
        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        if (!(principal instanceof KoreaderUserDetails details)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "User not authenticated"));
        }

        Long userId = details.getBookLoreUserId();
        log.info("Uploading statistics file for user: {}, filename: {}, size: {} bytes",
                userId, file.getOriginalFilename(), file.getSize());

        // Validate file
        if (file.isEmpty()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "File is empty"));
        }

        String filename = file.getOriginalFilename();
        if (filename == null || (!filename.endsWith(".sqlite3") && !filename.endsWith(".db"))) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Invalid file format. Expected .sqlite3 or .db file"));
        }

        try {
            // Save the file to a temporary location BEFORE async processing
            // (MultipartFile becomes invalid after the request completes)
            java.nio.file.Path tempFile = java.nio.file.Files.createTempFile("koreader-upload-", ".sqlite3");
            file.transferTo(tempFile.toFile());
            log.debug("Saved uploaded file to temporary location for async processing: {}", tempFile);

            // Start async processing with the saved file path and return immediately
            statisticsImportService.importFromSqliteFileAsync(userId, tempFile, filename);

            // Return 202 Accepted - processing will continue in background
            return ResponseEntity.status(HttpStatus.ACCEPTED).body(Map.of(
                    "status", "accepted",
                    "message", "Statistics file uploaded successfully and is being processed in the background"
            ));
        } catch (Exception e) {
            log.error("Failed to accept statistics file for user {}: {}", userId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to accept statistics file: " + e.getMessage()));
        }
    }

}
