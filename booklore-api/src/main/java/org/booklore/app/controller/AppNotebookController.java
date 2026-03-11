package org.booklore.app.controller;

import org.booklore.app.dto.AppNotebookBookSummary;
import org.booklore.app.dto.AppNotebookEntry;
import org.booklore.app.dto.AppNotebookUpdateRequest;
import org.booklore.app.dto.AppPageResponse;
import org.booklore.app.service.AppNotebookService;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Set;

@AllArgsConstructor
@RestController
@RequestMapping("/api/v1/app/notebook")
public class AppNotebookController {

    private final AppNotebookService mobileNotebookService;

    @GetMapping("/books")
    public ResponseEntity<AppPageResponse<AppNotebookBookSummary>> getBooksWithAnnotations(
            @RequestParam(required = false, defaultValue = "0") Integer page,
            @RequestParam(required = false, defaultValue = "20") Integer size,
            @RequestParam(required = false) String search) {

        return ResponseEntity.ok(mobileNotebookService.getBooksWithAnnotations(page, size, search));
    }

    @GetMapping("/books/{bookId}/entries")
    public ResponseEntity<AppPageResponse<AppNotebookEntry>> getEntriesForBook(
            @PathVariable Long bookId,
            @RequestParam(required = false, defaultValue = "0") Integer page,
            @RequestParam(required = false, defaultValue = "20") Integer size,
            @RequestParam(required = false) Set<String> types,
            @RequestParam(required = false) String search,
            @RequestParam(required = false, defaultValue = "date_desc") String sort) {

        return ResponseEntity.ok(mobileNotebookService.getEntriesForBook(bookId, page, size, types, search, sort));
    }

    @PutMapping("/entries/{entryId}")
    public ResponseEntity<AppNotebookEntry> updateEntry(
            @PathVariable Long entryId,
            @RequestParam String type,
            @Valid @RequestBody AppNotebookUpdateRequest request) {

        return ResponseEntity.ok(mobileNotebookService.updateEntry(entryId, type, request));
    }

    @DeleteMapping("/entries/{entryId}")
    public ResponseEntity<Void> deleteEntry(
            @PathVariable Long entryId,
            @RequestParam String type) {

        mobileNotebookService.deleteEntry(entryId, type);
        return ResponseEntity.noContent().build();
    }
}
