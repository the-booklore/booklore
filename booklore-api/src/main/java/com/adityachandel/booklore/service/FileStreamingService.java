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

@Slf4j
@Service
public class FileStreamingService {

    private static final int BUFFER_SIZE = 8192;
    private static final long MAX_CHUNK_SIZE = 2 * 1024 * 1024;

    public void streamWithRangeSupport(Path filePath, String contentType, HttpServletRequest request, HttpServletResponse response) throws IOException {
        if (!Files.exists(filePath)) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND, "File not found");
            return;
        }

        long fileSize = Files.size(filePath);
        String rangeHeader = request.getHeader("Range");

        response.setHeader("Accept-Ranges", "bytes");
        response.setContentType(contentType);
        response.setHeader("Cache-Control", "public, max-age=3600");

        try {
            if (rangeHeader == null) {
                response.setStatus(HttpServletResponse.SC_OK);
                response.setContentLengthLong(fileSize);
                streamBytes(filePath, 0, fileSize - 1, response.getOutputStream());
            } else {
                RangeInfo range = parseRange(rangeHeader, fileSize);
                if (range == null) {
                    response.setStatus(HttpServletResponse.SC_REQUESTED_RANGE_NOT_SATISFIABLE);
                    response.setHeader("Content-Range", "bytes */" + fileSize);
                    return;
                }

                long contentLength = range.end - range.start + 1;
                response.setStatus(HttpServletResponse.SC_PARTIAL_CONTENT);
                response.setContentLengthLong(contentLength);
                response.setHeader("Content-Range", "bytes " + range.start + "-" + range.end + "/" + fileSize);

                streamBytes(filePath, range.start, range.end, response.getOutputStream());
            }
        } catch (IOException e) {
            if (isClientDisconnect(e)) {
                log.debug("Client disconnected during streaming: {}", e.getMessage());
            } else {
                throw e;
            }
        }
    }

    boolean isClientDisconnect(IOException e) {
        if (e instanceof SocketTimeoutException) {
            return true;
        }

        String message = e.getMessage();
        if (message == null) {
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

    RangeInfo parseRange(String rangeHeader, long fileSize) {
        if (!rangeHeader.startsWith("bytes=")) {
            return null;
        }

        String rangeSpec = rangeHeader.substring(6).trim();
        String[] ranges = rangeSpec.split(",");
        if (ranges.length == 0) {
            return null;
        }

        String range = ranges[0].trim();
        String[] parts = range.split("-", -1);
        if (parts.length != 2) {
            return null;
        }

        try {
            long start, end;

            if (parts[0].isEmpty()) {
                long suffix = Long.parseLong(parts[1]);
                start = Math.max(0, fileSize - suffix);
                end = fileSize - 1;
            } else if (parts[1].isEmpty()) {
                start = Long.parseLong(parts[0]);
                end = Math.min(start + MAX_CHUNK_SIZE - 1, fileSize - 1);
            } else {
                start = Long.parseLong(parts[0]);
                end = Long.parseLong(parts[1]);
            }

            if (start < 0 || start >= fileSize || end < start) {
                return null;
            }

            end = Math.min(end, fileSize - 1);

            return new RangeInfo(start, end);
        } catch (NumberFormatException e) {
            return null;
        }
    }

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

    record RangeInfo(long start, long end) {}
}
