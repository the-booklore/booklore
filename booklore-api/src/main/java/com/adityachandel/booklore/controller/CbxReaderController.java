package com.adityachandel.booklore.controller;

import com.adityachandel.booklore.service.reader.CbxReaderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/cbx")
@RequiredArgsConstructor
@Tag(name = "CBX Reader", description = "Endpoints for reading CBX format books")
public class CbxReaderController {

    private final CbxReaderService cbxReaderService;

    @Operation(summary = "List pages in a CBX book", description = "Retrieve a list of available page numbers for a CBX book.")
    @ApiResponse(responseCode = "200", description = "Page numbers returned successfully")
    @GetMapping("/{bookId}/pages")
    public List<Integer> listPages(
            @Parameter(description = "ID of the book") @PathVariable Long bookId) {
        return cbxReaderService.getAvailablePages(bookId);
    }
}