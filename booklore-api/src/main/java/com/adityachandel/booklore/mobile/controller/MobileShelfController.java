package com.adityachandel.booklore.mobile.controller;

import com.adityachandel.booklore.config.security.service.AuthenticationService;
import com.adityachandel.booklore.mobile.dto.MobileMagicShelfSummary;
import com.adityachandel.booklore.mobile.dto.MobileShelfSummary;
import com.adityachandel.booklore.mobile.mapper.MobileBookMapper;
import com.adityachandel.booklore.model.dto.BookLoreUser;
import com.adityachandel.booklore.model.entity.MagicShelfEntity;
import com.adityachandel.booklore.model.entity.ShelfEntity;
import com.adityachandel.booklore.repository.MagicShelfRepository;
import com.adityachandel.booklore.repository.ShelfRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.AllArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@AllArgsConstructor
@RestController
@RequestMapping("/api/mobile/v1/shelves")
@Tag(name = "Mobile Shelves", description = "Mobile-optimized endpoints for shelf operations")
public class MobileShelfController {

    private final AuthenticationService authenticationService;
    private final ShelfRepository shelfRepository;
    private final MagicShelfRepository magicShelfRepository;
    private final MobileBookMapper mobileBookMapper;

    @Operation(summary = "Get user's shelves",
            description = "Retrieve a list of shelves the current user owns or are public.")
    @ApiResponse(responseCode = "200", description = "Shelves retrieved successfully")
    @GetMapping
    public ResponseEntity<List<MobileShelfSummary>> getShelves() {
        BookLoreUser user = authenticationService.getAuthenticatedUser();
        Long userId = user.getId();

        List<ShelfEntity> shelves = shelfRepository.findByUserIdOrPublicShelfTrue(userId);

        List<MobileShelfSummary> summaries = shelves.stream()
                .map(mobileBookMapper::toShelfSummaryFromEntity)
                .collect(Collectors.toList());

        return ResponseEntity.ok(summaries);
    }

    @Operation(summary = "Get user's magic shelves",
            description = "Retrieve a list of magic shelves the current user owns or are public.")
    @ApiResponse(responseCode = "200", description = "Magic shelves retrieved successfully")
    @GetMapping("/magic")
    public ResponseEntity<List<MobileMagicShelfSummary>> getMagicShelves() {
        BookLoreUser user = authenticationService.getAuthenticatedUser();
        Long userId = user.getId();

        // Get user's own magic shelves
        List<MagicShelfEntity> userShelves = magicShelfRepository.findAllByUserId(userId);

        // Get public magic shelves
        List<MagicShelfEntity> publicShelves = magicShelfRepository.findAllByIsPublicIsTrue();

        // Combine and deduplicate (user's shelves that are also public shouldn't appear twice)
        Set<Long> seenIds = new HashSet<>();
        List<MagicShelfEntity> allShelves = new ArrayList<>();

        for (MagicShelfEntity shelf : userShelves) {
            if (seenIds.add(shelf.getId())) {
                allShelves.add(shelf);
            }
        }
        for (MagicShelfEntity shelf : publicShelves) {
            if (seenIds.add(shelf.getId())) {
                allShelves.add(shelf);
            }
        }

        List<MobileMagicShelfSummary> summaries = allShelves.stream()
                .map(mobileBookMapper::toMagicShelfSummary)
                .collect(Collectors.toList());

        return ResponseEntity.ok(summaries);
    }
}
