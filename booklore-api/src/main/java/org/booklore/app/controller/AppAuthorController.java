package org.booklore.app.controller;

import org.booklore.app.dto.AppAuthorDetail;
import org.booklore.app.dto.AppAuthorSummary;
import org.booklore.app.dto.AppPageResponse;
import org.booklore.app.service.AppAuthorService;
import lombok.AllArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@AllArgsConstructor
@RestController
@RequestMapping("/api/v1/app/authors")
public class AppAuthorController {

    private final AppAuthorService mobileAuthorService;

    @GetMapping
    public ResponseEntity<AppPageResponse<AppAuthorSummary>> getAuthors(
            @RequestParam(required = false, defaultValue = "0") Integer page,
            @RequestParam(required = false, defaultValue = "30") Integer size,
            @RequestParam(required = false, defaultValue = "name") String sort,
            @RequestParam(required = false, defaultValue = "asc") String dir,
            @RequestParam(required = false) Long libraryId,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Boolean hasPhoto) {

        return ResponseEntity.ok(mobileAuthorService.getAuthors(page, size, sort, dir, libraryId, search, hasPhoto));
    }

    @GetMapping("/{authorId}")
    public ResponseEntity<AppAuthorDetail> getAuthorDetail(
            @PathVariable Long authorId) {

        return ResponseEntity.ok(mobileAuthorService.getAuthorDetail(authorId));
    }
}
