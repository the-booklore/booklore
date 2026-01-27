package com.adityachandel.booklore.service;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.net.SocketTimeoutException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Generic file streaming service with HTTP Range support.
 * Can be used for streaming audio, video, or any binary files.
 */
@Slf4j
@Service
public class FileStreamingService {

    private static final int BUFFER_SIZE = 8192;

    /**
     * Stream a file with HTTP Range support for seeking/scrubbing.
     * Implements HTTP 206 Partial Content for range requests.
     *
     * @param filePath    Path to the file to stream
     * @param contentType MIME type of the file
     * @param request     HTTP request (to read Range header)
     * @param response    HTTP response (to write headers and body)
     */
    public void streamWithRangeSupport(Path filePath, String contentType, HttpServletRequest request, HttpServletResponse response) throws IOException {
        if (!Files.exists(filePath)) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND, "File not found");
            return;
        }

        long fileSize = Files.size(filePath);
        String rangeHeader = request.getHeader("Range");

        // Always indicate we accept range requests
        response.setHeader("Accept-Ranges", "bytes");
        response.setContentType(contentType);
        response.setHeader("Cache-Control", "public, max-age=3600");

        try {
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
        } catch (IOException e) {
            // Client disconnected (e.g., during seeking) - this is normal, just log at debug level
            if (isClientDisconnect(e)) {
                log.debug("Client disconnected during streaming: {}", e.getMessage());
            } else {
                throw e;
            }
        }
    }

    /**
     * Check if the exception is due to client disconnect (common during seeking).
     */
    boolean isClientDisconnect(IOException e) {
        // SocketTimeoutException is common when client disconnects
        if (e instanceof SocketTimeoutException) {
            return true;
        }

        String message = e.getMessage();
        if (message == null) {
            // Check the exception class name for common disconnect types
            String className = e.getClass().getSimpleName();
            return className.contains("Timeout") || className.contains("Closed");
        }

        return message.contains("Connection reset") ||
               message.contains("Broken pipe") ||
               message.contains("connection was aborted") ||
               message.contains("An established connection was aborted") ||
               message.contains("SocketTimeout") ||
               message.contains("timed out");
    }

    /**
     * Parse HTTP Range header and return start/end byte positions.
     * Supports formats: "bytes=start-end", "bytes=start-", "bytes=-suffix"
     */
    RangeInfo parseRange(String rangeHeader, long fileSize) {
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
            byte[] buffer = new byte[BUFFER_SIZE];
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

    /**
     * Range information holder.
     */
    record RangeInfo(long start, long end) {}
}
