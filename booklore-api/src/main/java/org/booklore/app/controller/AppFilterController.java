package org.booklore.app.controller;

import org.booklore.app.dto.AppFilterOptions;
import org.booklore.app.service.AppBookService;
import lombok.AllArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@AllArgsConstructor
@RestController
@RequestMapping("/api/v1/app")
public class AppFilterController {

    private final AppBookService mobileBookService;

    @GetMapping("/filter-options")
    public ResponseEntity<AppFilterOptions> getFilterOptions(
            @RequestParam(required = false) Long libraryId,
            @RequestParam(required = false) Long shelfId,
            @RequestParam(required = false) Long magicShelfId) {
        return ResponseEntity.ok(mobileBookService.getFilterOptions(libraryId, shelfId, magicShelfId));
    }
}
