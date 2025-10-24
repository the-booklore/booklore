package com.adityachandel.booklore.controller;

import com.adityachandel.booklore.model.dto.BookdropFile;
import com.adityachandel.booklore.model.dto.BookdropFileNotification;
import com.adityachandel.booklore.model.dto.request.BookdropFinalizeRequest;
import com.adityachandel.booklore.model.dto.request.BookdropSelectionRequest;
import com.adityachandel.booklore.model.dto.response.BookdropFinalizeResult;
import com.adityachandel.booklore.service.bookdrop.BookDropService;
import com.adityachandel.booklore.service.bookdrop.BookdropMonitoringService;
import com.adityachandel.booklore.service.monitoring.MonitoringService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import lombok.AllArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Bookdrop", description = "Endpoints for managing bookdrop files and imports")
@AllArgsConstructor
@RestController
@RequestMapping("/api/v1/bookdrop")
public class BookdropFileController {

    private final BookDropService bookDropService;
    private final BookdropMonitoringService monitoringService;

    @Operation(summary = "Get bookdrop notification summary", description = "Retrieve a summary of bookdrop file notifications.")
    @ApiResponse(responseCode = "200", description = "Notification summary returned successfully")
    @GetMapping("/notification")
    public BookdropFileNotification getSummary() {
        return bookDropService.getFileNotificationSummary();
    }

    @Operation(summary = "Get bookdrop files by status", description = "Retrieve a paginated list of bookdrop files filtered by status.")
    @ApiResponse(responseCode = "200", description = "Bookdrop files returned successfully")
    @GetMapping("/files")
    public Page<BookdropFile> getFilesByStatus(
            @Parameter(description = "Status to filter files by") @RequestParam(required = false) String status,
            Pageable pageable) {
        return bookDropService.getFilesByStatus(status, pageable);
    }

    @Operation(summary = "Discard selected bookdrop files", description = "Discard selected bookdrop files based on selection criteria.")
    @ApiResponse(responseCode = "200", description = "Files discarded successfully")
    @PostMapping("/files/discard")
    public ResponseEntity<Void> discardSelectedFiles(
            @Parameter(description = "Selection request for files to discard") @RequestBody BookdropSelectionRequest request) {
        bookDropService.discardSelectedFiles(request.isSelectAll(), request.getExcludedIds(), request.getSelectedIds());
        return ResponseEntity.ok().build();
    }

    @Operation(summary = "Finalize bookdrop import", description = "Finalize the import of selected bookdrop files.")
    @ApiResponse(responseCode = "200", description = "Import finalized successfully")
    @PostMapping("/imports/finalize")
    public ResponseEntity<BookdropFinalizeResult> finalizeImport(
            @Parameter(description = "Finalize import request") @RequestBody BookdropFinalizeRequest request) {
        BookdropFinalizeResult result = bookDropService.finalizeImport(request);
        return ResponseEntity.ok(result);
    }

    @Operation(summary = "Rescan bookdrop folder", description = "Trigger a rescan of the bookdrop folder for new files.")
    @ApiResponse(responseCode = "200", description = "Bookdrop folder rescanned successfully")
    @PostMapping("/rescan")
    public ResponseEntity<Void> rescanBookdrop() {
        monitoringService.rescanBookdropFolder();
        return ResponseEntity.ok().build();
    }
}