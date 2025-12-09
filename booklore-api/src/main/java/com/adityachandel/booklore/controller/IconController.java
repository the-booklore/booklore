package com.adityachandel.booklore.controller;

import com.adityachandel.booklore.model.dto.request.SvgIconCreateRequest;
import com.adityachandel.booklore.service.IconService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Icons", description = "Endpoints for managing SVG icons")
@AllArgsConstructor
@RestController
@RequestMapping("/api/v1/icons")
public class IconController {

    private final IconService iconService;

    @Operation(summary = "Save an SVG icon", description = "Saves an SVG icon to the system.")
    @ApiResponse(responseCode = "200", description = "SVG icon saved successfully")
    @PostMapping
    public ResponseEntity<?> saveSvgIcon(@Valid @RequestBody SvgIconCreateRequest svgIconCreateRequest) {
        iconService.saveSvgIcon(svgIconCreateRequest);
        return ResponseEntity.ok().build();
    }

    @Operation(summary = "Get paginated icon names", description = "Retrieve a paginated list of icon names (default 50 per page).")
    @ApiResponse(responseCode = "200", description = "Icon names retrieved successfully")
    @GetMapping
    public ResponseEntity<Page<String>> getIconNames(
            @Parameter(description = "Page number") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Page size") @RequestParam(defaultValue = "50") int size) {
        Page<String> response = iconService.getIconNames(page, size);
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Delete an SVG icon", description = "Deletes an SVG icon by its name.")
    @ApiResponse(responseCode = "200", description = "SVG icon deleted successfully")
    @DeleteMapping("/{svgName}")
    public ResponseEntity<?> deleteSvgIcon(@Parameter(description = "SVG icon name") @PathVariable String svgName) {
        iconService.deleteSvgIcon(svgName);
        return ResponseEntity.ok().build();
    }
}
