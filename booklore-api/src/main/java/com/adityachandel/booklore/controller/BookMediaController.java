package com.adityachandel.booklore.controller;

import com.adityachandel.booklore.service.book.BookService;
import com.adityachandel.booklore.service.bookdrop.BookDropService;
import com.adityachandel.booklore.service.reader.CbxReaderService;
import com.adityachandel.booklore.service.reader.PdfReaderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import lombok.AllArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;

@Tag(name = "Book Media", description = "Endpoints for retrieving book media such as covers, thumbnails, and pages")
@AllArgsConstructor
@RestController
@RequestMapping("/api/v1/media")
public class BookMediaController {

    private final BookService bookService;
    private final PdfReaderService pdfReaderService;
    private final CbxReaderService cbxReaderService;
    private final BookDropService bookDropService;

    @Operation(summary = "Get book thumbnail", description = "Retrieve the thumbnail image for a specific book.")
    @ApiResponse(responseCode = "200", description = "Book thumbnail returned successfully")
    @GetMapping("/book/{bookId}/thumbnail")
    public ResponseEntity<Resource> getBookThumbnail(@Parameter(description = "ID of the book") @PathVariable long bookId) {
        return ResponseEntity.ok(bookService.getBookThumbnail(bookId));
    }

    @Operation(summary = "Get book cover", description = "Retrieve the cover image for a specific book.")
    @ApiResponse(responseCode = "200", description = "Book cover returned successfully")
    @GetMapping("/book/{bookId}/cover")
    public ResponseEntity<Resource> getBookCover(@Parameter(description = "ID of the book") @PathVariable long bookId) {
        return ResponseEntity.ok(bookService.getBookCover(bookId));
    }

    @Operation(summary = "Get PDF page as image", description = "Retrieve a specific page from a PDF book as an image.")
    @ApiResponse(responseCode = "200", description = "PDF page image returned successfully")
    @GetMapping("/book/{bookId}/pdf/pages/{pageNumber}")
    public void getPdfPage(
            @Parameter(description = "ID of the book") @PathVariable Long bookId,
            @Parameter(description = "Page number to retrieve") @PathVariable int pageNumber,
            @Parameter(description = "Optional book type for alternative format (e.g., PDF, CBX)") @RequestParam(required = false) String bookType,
            HttpServletResponse response) throws IOException {
        response.setContentType(MediaType.IMAGE_JPEG_VALUE);
        pdfReaderService.streamPageImage(bookId, bookType, pageNumber, response.getOutputStream());
    }

    @Operation(summary = "Get CBX page as image", description = "Retrieve a specific page from a CBX book as an image.")
    @ApiResponse(responseCode = "200", description = "CBX page image returned successfully")
    @GetMapping("/book/{bookId}/cbx/pages/{pageNumber}")
    public void getCbxPage(
            @Parameter(description = "ID of the book") @PathVariable Long bookId,
            @Parameter(description = "Page number to retrieve") @PathVariable int pageNumber,
            @Parameter(description = "Optional book type for alternative format (e.g., PDF, CBX)") @RequestParam(required = false) String bookType,
            HttpServletResponse response) throws IOException {
        response.setContentType(MediaType.IMAGE_JPEG_VALUE);
        cbxReaderService.streamPageImage(bookId, bookType, pageNumber, response.getOutputStream());
    }

    @Operation(summary = "Get bookdrop cover", description = "Retrieve the cover image for a specific bookdrop file.")
    @ApiResponse(responseCode = "200", description = "Bookdrop cover returned successfully")
    @GetMapping("/bookdrop/{bookdropId}/cover")
    public ResponseEntity<Resource> getBookdropCover(@Parameter(description = "ID of the bookdrop file") @PathVariable long bookdropId) {
        Resource file = bookDropService.getBookdropCover(bookdropId);
        String contentDisposition = "inline; filename=\"cover.jpg\"; filename*=UTF-8''cover.jpg";
        return (file != null)
                ? ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, contentDisposition)
                .contentType(MediaType.IMAGE_JPEG)
                .body(file)
                : ResponseEntity.noContent().build();
    }
}