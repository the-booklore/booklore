package com.adityachandel.booklore.controller;

import com.adityachandel.booklore.config.security.annotation.CheckBookAccess;
import com.adityachandel.booklore.model.dto.response.AudiobookInfo;
import com.adityachandel.booklore.service.reader.AudiobookReaderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.nio.file.Path;

@Slf4j
@RestController
@RequestMapping("/api/v1/audiobook")
@RequiredArgsConstructor
@Tag(name = "Audiobook Reader", description = "Endpoints for streaming audiobooks with HTTP Range support")
public class AudiobookReaderController {

    private final AudiobookReaderService audiobookReaderService;

    @Operation(summary = "Get audiobook info",
            description = "Retrieve metadata including duration, chapters/tracks, and audio details for an audiobook.")
    @ApiResponse(responseCode = "200", description = "Audiobook info returned successfully")
    @CheckBookAccess(bookIdParam = "bookId")
    @GetMapping("/{bookId}/info")
    public ResponseEntity<AudiobookInfo> getAudiobookInfo(
            @Parameter(description = "ID of the book") @PathVariable Long bookId,
            @Parameter(description = "Optional book type for alternative format (e.g., AUDIOBOOK)") @RequestParam(required = false) String bookType) {
        return ResponseEntity.ok(audiobookReaderService.getAudiobookInfo(bookId, bookType));
    }

    @Operation(summary = "Stream audiobook audio",
            description = "Stream the audiobook audio file with HTTP Range support for seeking. " +
                    "Uses token query parameter for authentication to support HTML5 audio element.")
    @ApiResponse(responseCode = "200", description = "Full audio file returned")
    @ApiResponse(responseCode = "206", description = "Partial content returned (range request)")
    @ApiResponse(responseCode = "416", description = "Range not satisfiable")
    @GetMapping("/{bookId}/stream")
    public void streamAudiobook(
            @Parameter(description = "ID of the book") @PathVariable Long bookId,
            @Parameter(description = "Optional book type for alternative format") @RequestParam(required = false) String bookType,
            @Parameter(description = "Track index for folder-based audiobooks (0-indexed)") @RequestParam(required = false) Integer trackIndex,
            HttpServletRequest request,
            HttpServletResponse response) throws IOException {

        Path audioPath = audiobookReaderService.getAudioFilePath(bookId, bookType, trackIndex);
        streamWithRangeSupport(audioPath, request, response);
    }

    @Operation(summary = "Stream specific track",
            description = "Stream a specific track from a folder-based audiobook.")
    @ApiResponse(responseCode = "200", description = "Full track file returned")
    @ApiResponse(responseCode = "206", description = "Partial content returned (range request)")
    @ApiResponse(responseCode = "416", description = "Range not satisfiable")
    @GetMapping("/{bookId}/track/{trackIndex}/stream")
    public void streamTrack(
            @Parameter(description = "ID of the book") @PathVariable Long bookId,
            @Parameter(description = "Track index (0-indexed)") @PathVariable Integer trackIndex,
            @Parameter(description = "Optional book type for alternative format") @RequestParam(required = false) String bookType,
            HttpServletRequest request,
            HttpServletResponse response) throws IOException {

        Path audioPath = audiobookReaderService.getAudioFilePath(bookId, bookType, trackIndex);
        streamWithRangeSupport(audioPath, request, response);
    }

    @Operation(summary = "Get embedded cover art",
            description = "Extract and return the embedded cover art from the audiobook file. " +
                    "Uses token query parameter for authentication.")
    @ApiResponse(responseCode = "200", description = "Cover art returned successfully")
    @ApiResponse(responseCode = "404", description = "No embedded cover art found")
    @GetMapping("/{bookId}/cover")
    public ResponseEntity<byte[]> getEmbeddedCover(
            @Parameter(description = "ID of the book") @PathVariable Long bookId,
            @Parameter(description = "Optional book type for alternative format") @RequestParam(required = false) String bookType) {

        byte[] coverData = audiobookReaderService.getEmbeddedCoverArt(bookId, bookType);
        if (coverData == null) {
            return ResponseEntity.notFound().build();
        }

        String mimeType = audiobookReaderService.getCoverArtMimeType(bookId, bookType);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType(mimeType));
        headers.setContentLength(coverData.length);
        headers.setCacheControl("public, max-age=86400");

        return new ResponseEntity<>(coverData, headers, HttpStatus.OK);
    }

    /**
     * Stream a file with HTTP Range support for seeking/scrubbing.
     * Implements HTTP 206 Partial Content for range requests.
     */
    private void streamWithRangeSupport(Path filePath, HttpServletRequest request, HttpServletResponse response) throws IOException {
        if (!Files.exists(filePath)) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND, "Audio file not found");
            return;
        }

        long fileSize = Files.size(filePath);
        String contentType = audiobookReaderService.getContentType(filePath);
        String rangeHeader = request.getHeader("Range");

        // Always indicate we accept range requests
        response.setHeader("Accept-Ranges", "bytes");
        response.setContentType(contentType);
        response.setHeader("Cache-Control", "public, max-age=3600");

        if (rangeHeader == null) {
            // No Range header - return full file
            response.setStatus(HttpServletResponse.SC_OK);
            response.setContentLengthLong(fileSize);
            streamBytes(filePath, 0, fileSize - 1, response.getOutputStream());
        } else {
            // Parse Range header
            RangeInfo range = parseRange(rangeHeader, fileSize);
            if (range == null) {
                // Invalid range
                response.setStatus(HttpServletResponse.SC_REQUESTED_RANGE_NOT_SATISFIABLE);
                response.setHeader("Content-Range", "bytes */" + fileSize);
                return;
            }

            // Return partial content
            long contentLength = range.end - range.start + 1;
            response.setStatus(HttpServletResponse.SC_PARTIAL_CONTENT);
            response.setContentLengthLong(contentLength);
            response.setHeader("Content-Range", "bytes " + range.start + "-" + range.end + "/" + fileSize);

            streamBytes(filePath, range.start, range.end, response.getOutputStream());
        }
    }

    /**
     * Parse HTTP Range header and return start/end byte positions.
     * Supports formats: "bytes=start-end", "bytes=start-", "bytes=-suffix"
     */
    private RangeInfo parseRange(String rangeHeader, long fileSize) {
        if (!rangeHeader.startsWith("bytes=")) {
            return null;
        }

        String rangeSpec = rangeHeader.substring(6).trim();
        String[] ranges = rangeSpec.split(",");
        if (ranges.length == 0) {
            return null;
        }

        // Only handle first range (most common case)
        String range = ranges[0].trim();
        String[] parts = range.split("-", -1);
        if (parts.length != 2) {
            return null;
        }

        try {
            long start, end;

            if (parts[0].isEmpty()) {
                // Suffix range: "-500" means last 500 bytes
                long suffix = Long.parseLong(parts[1]);
                start = Math.max(0, fileSize - suffix);
                end = fileSize - 1;
            } else if (parts[1].isEmpty()) {
                // Open-ended range: "500-" means from byte 500 to end
                start = Long.parseLong(parts[0]);
                end = fileSize - 1;
            } else {
                // Full range: "500-999"
                start = Long.parseLong(parts[0]);
                end = Long.parseLong(parts[1]);
            }

            // Validate range
            if (start < 0 || start >= fileSize || end < start) {
                return null;
            }

            // Clamp end to file size
            end = Math.min(end, fileSize - 1);

            return new RangeInfo(start, end);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * Stream bytes from start to end (inclusive) using RandomAccessFile for efficient seeking.
     */
    private void streamBytes(Path filePath, long start, long end, OutputStream outputStream) throws IOException {
        try (RandomAccessFile raf = new RandomAccessFile(filePath.toFile(), "r")) {
            raf.seek(start);

            long bytesToRead = end - start + 1;
            byte[] buffer = new byte[8192];
            long totalRead = 0;

            while (totalRead < bytesToRead) {
                int toRead = (int) Math.min(buffer.length, bytesToRead - totalRead);
                int read = raf.read(buffer, 0, toRead);
                if (read == -1) {
                    break;
                }
                outputStream.write(buffer, 0, read);
                totalRead += read;
            }

            outputStream.flush();
        }
    }

    private record RangeInfo(long start, long end) {}
}
