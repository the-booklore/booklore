package org.booklore.service;

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

@Slf4j
@Service
public class FileStreamingService {

    private static final int BUFFER_SIZE = 64 * 1024;

    public void streamWithRangeSupport(
            Path filePath,
            String contentType,
            HttpServletRequest request,
            HttpServletResponse response
    ) throws IOException {

        if (!Files.exists(filePath)) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND, "File not found");
            return;
        }

        long fileSize = Files.size(filePath);
        String rangeHeader = request.getHeader("Range");

        response.setHeader("Accept-Ranges", "bytes");
        response.setHeader("Cache-Control", "no-store");
        response.setHeader("Content-Disposition", "inline");
        response.setContentType(contentType);

        // -------------------------
        // HEAD
        // -------------------------
        if ("HEAD".equalsIgnoreCase(request.getMethod())) {
            response.setStatus(HttpServletResponse.SC_PARTIAL_CONTENT);
            response.setHeader(
                    "Content-Range",
                    "bytes 0-" + (fileSize - 1) + "/" + fileSize
            );
            response.setContentLengthLong(fileSize);
            return;
        }

        try {
            // -------------------------
            // NO RANGE
            // -------------------------
            if (rangeHeader == null) {
                response.setStatus(HttpServletResponse.SC_OK);
                response.setContentLengthLong(fileSize);
                streamBytes(filePath, 0, fileSize - 1, response.getOutputStream());
                return;
            }

            // -------------------------
            // RANGE
            // -------------------------
            Range range = parseRange(rangeHeader, fileSize);
            if (range == null) {
                response.setStatus(HttpServletResponse.SC_REQUESTED_RANGE_NOT_SATISFIABLE);
                response.setHeader("Content-Range", "bytes */" + fileSize);
                return;
            }

            long length = range.end - range.start + 1;

            response.setStatus(HttpServletResponse.SC_PARTIAL_CONTENT);
            response.setHeader(
                    "Content-Range",
                    "bytes " + range.start + "-" + range.end + "/" + fileSize
            );
            response.setContentLengthLong(length);

            streamBytes(filePath, range.start, range.end, response.getOutputStream());

        } catch (IOException e) {
            if (isClientDisconnect(e)) {
                log.debug("Client disconnected during streaming: {}", e.getMessage());
            } else {
                throw e;
            }
        }
    }

    // ------------------------------------------------------------
    // RANGE PARSER — RFC 7233 compliant
    // ------------------------------------------------------------
    Range parseRange(String header, long size) {
        if (header == null || !header.startsWith("bytes=")) {
            return null;
        }

        String value = header.substring(6).trim();
        String[] parts = value.split(",", 2);
        String range = parts[0].trim();

        int dash = range.indexOf('-');
        if (dash < 0) return null;

        try {
            // suffix-byte-range-spec: "-<length>"
            if (dash == 0) {
                long suffix = Long.parseLong(range.substring(1));
                if (suffix <= 0) return null;
                suffix = Math.min(suffix, size);
                return new Range(size - suffix, size - 1);
            }

            long start = Long.parseLong(range.substring(0, dash));

            // open-ended: "<start>-"
            if (dash == range.length() - 1) {
                if (start >= size) return null;
                return new Range(start, size - 1);
            }

            // "<start>-<end>"
            long end = Long.parseLong(range.substring(dash + 1));
            if (start > end || start >= size) return null;
            end = Math.min(end, size - 1);

            return new Range(start, end);

        } catch (NumberFormatException e) {
            return null;
        }
    }

    // ------------------------------------------------------------
    // STREAM BYTES
    // ------------------------------------------------------------
    private void streamBytes(Path path, long start, long end, OutputStream out)
            throws IOException {

        try (RandomAccessFile raf = new RandomAccessFile(path.toFile(), "r")) {
            raf.seek(start);

            long remaining = end - start + 1;
            byte[] buffer = new byte[BUFFER_SIZE];

            while (remaining > 0) {
                int read = raf.read(buffer, 0, (int) Math.min(buffer.length, remaining));
                if (read == -1) break;
                out.write(buffer, 0, read);
                remaining -= read;
            }

            out.flush();
        }
    }

    // ------------------------------------------------------------
    // DISCONNECT DETECTION
    // ------------------------------------------------------------
    boolean isClientDisconnect(IOException e) {
        if (e instanceof SocketTimeoutException) return true;

        String msg = e.getMessage();
        if (msg == null) return false;

        return msg.contains("Broken pipe")
                || msg.contains("Connection reset")
                || msg.contains("connection was aborted")
                || msg.contains("An established connection was aborted")
                || msg.contains("SocketTimeout")
                || msg.contains("timed out");
    }

    record Range(long start, long end) {}
}