package com.adityachandel.booklore.controller;

import com.adityachandel.booklore.service.AuthorService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import lombok.AllArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "Authors", description = "Endpoints for retrieving authors related to books")
@RequestMapping("/api/v1/authors")
@RestController
@AllArgsConstructor
public class AuthorController {

    private final AuthorService authorService;

    @Operation(summary = "Get authors by book ID", description = "Retrieve a list of authors for a specific book.")
    @ApiResponse(responseCode = "200", description = "Authors returned successfully")
    @GetMapping("/book/{bookId}")
    public ResponseEntity<List<String>> getAuthorsByBookId(
            @Parameter(description = "ID of the book") @PathVariable long bookId) {
        return ResponseEntity.ok(authorService.getAuthorsByBookId(bookId));
    }
}
