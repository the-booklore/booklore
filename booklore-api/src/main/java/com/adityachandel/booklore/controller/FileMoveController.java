package com.adityachandel.booklore.controller;

import com.adityachandel.booklore.model.dto.request.FileMoveRequest;
import com.adityachandel.booklore.service.file.FileMoveService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.AllArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@AllArgsConstructor
@RestController
@RequestMapping("/api/v1/files")
@Tag(name = "File Move", description = "Endpoints for moving files within the library")
public class FileMoveController {

    private final FileMoveService fileMoveService;

    @Operation(summary = "Move files", description = "Bulk move files to a different location within the library.")
    @ApiResponse(responseCode = "200", description = "Files moved successfully")
    @PostMapping("/move")
    public ResponseEntity<?> moveFiles(
            @Parameter(description = "File move request") @RequestBody FileMoveRequest request) {
        fileMoveService.bulkMoveFiles(request);
        return ResponseEntity.ok().build();
    }
}
