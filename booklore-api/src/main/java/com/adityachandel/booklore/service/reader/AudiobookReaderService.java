package com.adityachandel.booklore.service.reader;

import com.adityachandel.booklore.exception.ApiError;
import com.adityachandel.booklore.model.dto.response.AudiobookChapter;
import com.adityachandel.booklore.model.dto.response.AudiobookInfo;
import com.adityachandel.booklore.model.dto.response.AudiobookTrack;
import com.adityachandel.booklore.model.entity.BookEntity;
import com.adityachandel.booklore.model.entity.BookFileEntity;
import com.adityachandel.booklore.model.enums.BookFileType;
import com.adityachandel.booklore.repository.BookRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jaudiotagger.audio.AudioFile;
import org.jaudiotagger.audio.AudioFileIO;
import org.jaudiotagger.audio.AudioHeader;
import org.jaudiotagger.tag.FieldKey;
import org.jaudiotagger.tag.Tag;
import org.jaudiotagger.tag.images.Artwork;
import org.springframework.stereotype.Service;

import java.io.*;
import java.net.SocketTimeoutException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

@Slf4j
@Service
@RequiredArgsConstructor
public class AudiobookReaderService {

    private static final int MAX_CACHE_ENTRIES = 50;
    private static final Set<String> AUDIO_EXTENSIONS = Set.of(
            ".mp3", ".m4a", ".m4b", ".aac", ".flac", ".ogg", ".opus"
    );

    private final BookRepository bookRepository;
    private final Map<String, CachedAudiobookMetadata> metadataCache = new ConcurrentHashMap<>();

    private static class CachedAudiobookMetadata {
        final AudiobookInfo info;
        final long lastModified;
        volatile long lastAccessed;

        CachedAudiobookMetadata(AudiobookInfo info, long lastModified) {
            this.info = info;
            this.lastModified = lastModified;
            this.lastAccessed = System.currentTimeMillis();
        }
    }

    public AudiobookInfo getAudiobookInfo(Long bookId, String bookType) {
        BookFileEntity bookFile = getAudiobookFile(bookId, bookType);
        Path audioPath = bookFile.isFolderBased() ? bookFile.getFullFilePath() : bookFile.getFullFilePath();

        try {
            CachedAudiobookMetadata metadata = getCachedMetadata(bookFile, audioPath);
            return metadata.info;
        } catch (Exception e) {
            log.error("Failed to read audiobook metadata for book {}", bookId, e);
            throw ApiError.FILE_READ_ERROR.createException("Failed to read audiobook: " + e.getMessage());
        }
    }

    public Path getAudioFilePath(Long bookId, String bookType, Integer trackIndex) {
        BookFileEntity bookFile = getAudiobookFile(bookId, bookType);

        if (bookFile.isFolderBased()) {
            if (trackIndex == null) {
                trackIndex = 0;
            }
            List<Path> tracks = listAudioFiles(bookFile.getFullFilePath());
            if (trackIndex < 0 || trackIndex >= tracks.size()) {
                throw ApiError.FILE_NOT_FOUND.createException("Track index out of range: " + trackIndex);
            }
            return tracks.get(trackIndex);
        } else {
            return bookFile.getFullFilePath();
        }
    }

    public String getContentType(Path audioPath) {
        String fileName = audioPath.getFileName().toString().toLowerCase();
        if (fileName.endsWith(".m4b") || fileName.endsWith(".m4a")) {
            return "audio/mp4";
        } else if (fileName.endsWith(".mp3")) {
            return "audio/mpeg";
        } else if (fileName.endsWith(".aac")) {
            return "audio/aac";
        } else if (fileName.endsWith(".flac")) {
            return "audio/flac";
        } else if (fileName.endsWith(".ogg")) {
            return "audio/ogg";
        } else if (fileName.endsWith(".opus")) {
            return "audio/opus";
        }
        return "application/octet-stream";
    }

    public byte[] getEmbeddedCoverArt(Long bookId, String bookType) {
        BookFileEntity bookFile = getAudiobookFile(bookId, bookType);
        Path audioPath = bookFile.isFolderBased() ? bookFile.getFirstAudioFile() : bookFile.getFullFilePath();

        try {
            AudioFile audioFile = AudioFileIO.read(audioPath.toFile());
            Tag tag = audioFile.getTag();
            if (tag != null) {
                Artwork artwork = tag.getFirstArtwork();
                if (artwork != null) {
                    return artwork.getBinaryData();
                }
            }
        } catch (Exception e) {
            log.debug("No embedded cover art found for book {}: {}", bookId, e.getMessage());
        }
        return null;
    }

    public String getCoverArtMimeType(Long bookId, String bookType) {
        BookFileEntity bookFile = getAudiobookFile(bookId, bookType);
        Path audioPath = bookFile.isFolderBased() ? bookFile.getFirstAudioFile() : bookFile.getFullFilePath();

        try {
            AudioFile audioFile = AudioFileIO.read(audioPath.toFile());
            Tag tag = audioFile.getTag();
            if (tag != null) {
                Artwork artwork = tag.getFirstArtwork();
                if (artwork != null) {
                    String mimeType = artwork.getMimeType();
                    if (mimeType != null && !mimeType.isEmpty()) {
                        return mimeType;
                    }
                    // Fallback based on binary data magic bytes
                    byte[] data = artwork.getBinaryData();
                    if (data != null && data.length > 2) {
                        if (data[0] == (byte) 0xFF && data[1] == (byte) 0xD8) {
                            return "image/jpeg";
                        } else if (data[0] == (byte) 0x89 && data[1] == (byte) 0x50) {
                            return "image/png";
                        }
                    }
                    return "image/jpeg"; // Default fallback
                }
            }
        } catch (Exception e) {
            log.debug("Could not determine cover art MIME type for book {}", bookId);
        }
        return "image/jpeg";
    }

    private BookFileEntity getAudiobookFile(Long bookId, String bookType) {
        BookEntity bookEntity = bookRepository.findByIdWithBookFiles(bookId)
                .orElseThrow(() -> ApiError.BOOK_NOT_FOUND.createException(bookId));

        if (bookType != null) {
            BookFileType requestedType = BookFileType.valueOf(bookType.toUpperCase());
            return bookEntity.getBookFiles().stream()
                    .filter(bf -> bf.getBookType() == requestedType)
                    .findFirst()
                    .orElseThrow(() -> ApiError.FILE_NOT_FOUND.createException("No file of type " + bookType + " found for book"));
        }

        return bookEntity.getBookFiles().stream()
                .filter(bf -> bf.getBookType() == BookFileType.AUDIOBOOK && bf.isBookFormat())
                .findFirst()
                .orElseThrow(() -> ApiError.FILE_NOT_FOUND.createException("No audiobook file found for book " + bookId));
    }

    private CachedAudiobookMetadata getCachedMetadata(BookFileEntity bookFile, Path audioPath) throws Exception {
        String cacheKey = audioPath.toString();
        long currentModified = getLastModified(bookFile, audioPath);
        CachedAudiobookMetadata cached = metadataCache.get(cacheKey);

        if (cached != null && cached.lastModified == currentModified) {
            cached.lastAccessed = System.currentTimeMillis();
            log.debug("Cache hit for audiobook: {}", audioPath.getFileName());
            return cached;
        }

        log.debug("Cache miss for audiobook: {}, parsing...", audioPath.getFileName());
        AudiobookInfo info = parseAudiobookMetadata(bookFile, audioPath);
        CachedAudiobookMetadata newMetadata = new CachedAudiobookMetadata(info, currentModified);
        metadataCache.put(cacheKey, newMetadata);
        evictOldestCacheEntries();
        return newMetadata;
    }

    private long getLastModified(BookFileEntity bookFile, Path audioPath) throws IOException {
        if (bookFile.isFolderBased()) {
            // For folder-based, use the most recent modification time of any audio file
            try (Stream<Path> files = Files.list(audioPath)) {
                return files.filter(this::isAudioFile)
                        .mapToLong(p -> {
                            try {
                                return Files.getLastModifiedTime(p).toMillis();
                            } catch (IOException e) {
                                return 0;
                            }
                        })
                        .max()
                        .orElse(Files.getLastModifiedTime(audioPath).toMillis());
            }
        }
        return Files.getLastModifiedTime(audioPath).toMillis();
    }

    private void evictOldestCacheEntries() {
        if (metadataCache.size() <= MAX_CACHE_ENTRIES) {
            return;
        }
        List<String> keysToRemove = metadataCache.entrySet().stream()
                .sorted(Comparator.comparingLong(e -> e.getValue().lastAccessed))
                .limit(metadataCache.size() - MAX_CACHE_ENTRIES)
                .map(Map.Entry::getKey)
                .toList();
        keysToRemove.forEach(key -> {
            metadataCache.remove(key);
            log.debug("Evicted audiobook cache entry: {}", key);
        });
    }

    private AudiobookInfo parseAudiobookMetadata(BookFileEntity bookFile, Path audioPath) throws Exception {
        AudiobookInfo.AudiobookInfoBuilder builder = AudiobookInfo.builder()
                .bookId(bookFile.getBook().getId())
                .bookFileId(bookFile.getId())
                .folderBased(bookFile.isFolderBased());

        if (bookFile.isFolderBased()) {
            return parseFolderBasedAudiobook(builder, audioPath);
        } else {
            return parseSingleFileAudiobook(builder, audioPath);
        }
    }

    private AudiobookInfo parseSingleFileAudiobook(AudiobookInfo.AudiobookInfoBuilder builder, Path audioPath) throws Exception {
        AudioFile audioFile = AudioFileIO.read(audioPath.toFile());
        AudioHeader header = audioFile.getAudioHeader();
        Tag tag = audioFile.getTag();

        // Basic audio info
        long durationMs = (long) (header.getPreciseTrackLength() * 1000);
        long bitrateValue = header.getBitRateAsNumber();
        builder.durationMs(durationMs)
                .bitrate(bitrateValue > 0 ? (int) bitrateValue : null)
                .codec(header.getEncodingType())
                .sampleRate(header.getSampleRateAsNumber())
                .channels(parseChannels(header.getChannels()));

        // Metadata from tags
        if (tag != null) {
            builder.title(getTagValue(tag, FieldKey.TITLE, FieldKey.ALBUM))
                    .author(getTagValue(tag, FieldKey.ARTIST, FieldKey.ALBUM_ARTIST))
                    .narrator(getTagValue(tag, FieldKey.COMPOSER));
        }

        // Extract chapters for M4B/M4A using FFprobe
        List<AudiobookChapter> chapters = extractChapters(audioPath, durationMs);
        builder.chapters(chapters);

        return builder.build();
    }

    private AudiobookInfo parseFolderBasedAudiobook(AudiobookInfo.AudiobookInfoBuilder builder, Path folderPath) throws Exception {
        List<Path> audioFiles = listAudioFiles(folderPath);
        if (audioFiles.isEmpty()) {
            throw ApiError.FILE_NOT_FOUND.createException("No audio files found in folder: " + folderPath);
        }

        List<AudiobookTrack> tracks = new ArrayList<>();
        long totalDurationMs = 0;
        String title = null;
        String author = null;
        String narrator = null;
        Integer bitrate = null;
        String codec = null;
        Integer sampleRate = null;
        Integer channels = null;

        for (int i = 0; i < audioFiles.size(); i++) {
            Path trackPath = audioFiles.get(i);
            try {
                AudioFile audioFile = AudioFileIO.read(trackPath.toFile());
                AudioHeader header = audioFile.getAudioHeader();
                Tag tag = audioFile.getTag();

                long trackDurationMs = (long) (header.getPreciseTrackLength() * 1000);
                long fileSizeBytes = Files.size(trackPath);

                String trackTitle = null;
                if (tag != null) {
                    trackTitle = tag.getFirst(FieldKey.TITLE);
                }
                if (trackTitle == null || trackTitle.isEmpty()) {
                    trackTitle = getTrackTitleFromFilename(trackPath.getFileName().toString());
                }

                tracks.add(AudiobookTrack.builder()
                        .index(i)
                        .fileName(trackPath.getFileName().toString())
                        .title(trackTitle)
                        .durationMs(trackDurationMs)
                        .fileSizeBytes(fileSizeBytes)
                        .cumulativeStartMs(totalDurationMs)
                        .build());

                totalDurationMs += trackDurationMs;

                // Use metadata from first track for the audiobook
                if (i == 0) {
                    long bitrateValue = header.getBitRateAsNumber();
                    bitrate = bitrateValue > 0 ? (int) bitrateValue : null;
                    codec = header.getEncodingType();
                    sampleRate = header.getSampleRateAsNumber();
                    channels = parseChannels(header.getChannels());
                    if (tag != null) {
                        title = getTagValue(tag, FieldKey.ALBUM, FieldKey.TITLE);
                        author = getTagValue(tag, FieldKey.ALBUM_ARTIST, FieldKey.ARTIST);
                        narrator = getTagValue(tag, FieldKey.COMPOSER);
                    }
                }
            } catch (Exception e) {
                log.warn("Failed to read track metadata: {}", trackPath, e);
                // Add basic track info even if metadata extraction fails
                tracks.add(AudiobookTrack.builder()
                        .index(i)
                        .fileName(trackPath.getFileName().toString())
                        .title(getTrackTitleFromFilename(trackPath.getFileName().toString()))
                        .fileSizeBytes(Files.size(trackPath))
                        .cumulativeStartMs(totalDurationMs)
                        .build());
            }
        }

        return builder
                .title(title)
                .author(author)
                .narrator(narrator)
                .durationMs(totalDurationMs)
                .bitrate(bitrate)
                .codec(codec)
                .sampleRate(sampleRate)
                .channels(channels)
                .tracks(tracks)
                .build();
    }

    private List<AudiobookChapter> extractChapters(Path audioPath, long totalDurationMs) {
        List<AudiobookChapter> chapters = new ArrayList<>();

        // Try to extract chapters using FFprobe
        try {
            chapters = extractChaptersWithFFprobe(audioPath);
            if (!chapters.isEmpty()) {
                log.debug("Extracted {} chapters from {} using FFprobe", chapters.size(), audioPath.getFileName());
                return chapters;
            }
        } catch (Exception e) {
            log.debug("FFprobe chapter extraction failed for {}: {}", audioPath.getFileName(), e.getMessage());
        }

        // Fallback: create a single chapter spanning the entire duration
        chapters.add(AudiobookChapter.builder()
                .index(0)
                .title("Full Audiobook")
                .startTimeMs(0L)
                .endTimeMs(totalDurationMs)
                .durationMs(totalDurationMs)
                .build());

        return chapters;
    }

    private List<AudiobookChapter> extractChaptersWithFFprobe(Path audioPath) throws Exception {
        List<AudiobookChapter> chapters = new ArrayList<>();

        // Run ffprobe to get chapter info as JSON
        ProcessBuilder pb = new ProcessBuilder(
                "ffprobe",
                "-v", "quiet",
                "-print_format", "json",
                "-show_chapters",
                audioPath.toAbsolutePath().toString()
        );
        pb.redirectErrorStream(true);

        Process process = pb.start();
        StringBuilder output = new StringBuilder();

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line);
            }
        }

        int exitCode = process.waitFor();
        if (exitCode != 0) {
            log.debug("FFprobe exited with code {} for {}", exitCode, audioPath.getFileName());
            return chapters;
        }

        // Parse JSON output
        String jsonOutput = output.toString().trim();
        if (jsonOutput.isEmpty() || jsonOutput.equals("{}")) {
            return chapters;
        }

        ObjectMapper mapper = new ObjectMapper();
        JsonNode root = mapper.readTree(jsonOutput);
        JsonNode chaptersNode = root.get("chapters");

        if (chaptersNode == null || !chaptersNode.isArray() || chaptersNode.isEmpty()) {
            return chapters;
        }

        for (int i = 0; i < chaptersNode.size(); i++) {
            JsonNode chapterNode = chaptersNode.get(i);

            // Get start and end times (in seconds, need to convert to ms)
            double startTime = 0;
            double endTime = 0;

            if (chapterNode.has("start_time")) {
                startTime = chapterNode.get("start_time").asDouble();
            } else if (chapterNode.has("start")) {
                // start is in timebase units, need to calculate
                long start = chapterNode.get("start").asLong();
                String timeBase = chapterNode.has("time_base") ? chapterNode.get("time_base").asText() : "1/1000";
                startTime = convertTimebaseToSeconds(start, timeBase);
            }

            if (chapterNode.has("end_time")) {
                endTime = chapterNode.get("end_time").asDouble();
            } else if (chapterNode.has("end")) {
                long end = chapterNode.get("end").asLong();
                String timeBase = chapterNode.has("time_base") ? chapterNode.get("time_base").asText() : "1/1000";
                endTime = convertTimebaseToSeconds(end, timeBase);
            }

            long startTimeMs = Math.round(startTime * 1000);
            long endTimeMs = Math.round(endTime * 1000);
            long durationMs = endTimeMs - startTimeMs;

            // Get chapter title from tags
            String title = "Chapter " + (i + 1);
            JsonNode tagsNode = chapterNode.get("tags");
            if (tagsNode != null && tagsNode.has("title")) {
                title = tagsNode.get("title").asText();
            }

            chapters.add(AudiobookChapter.builder()
                    .index(i)
                    .title(title)
                    .startTimeMs(startTimeMs)
                    .endTimeMs(endTimeMs)
                    .durationMs(durationMs)
                    .build());
        }

        return chapters;
    }

    private double convertTimebaseToSeconds(long value, String timeBase) {
        // timeBase is typically "1/1000" or "1/44100" etc.
        String[] parts = timeBase.split("/");
        if (parts.length == 2) {
            try {
                double numerator = Double.parseDouble(parts[0]);
                double denominator = Double.parseDouble(parts[1]);
                return value * (numerator / denominator);
            } catch (NumberFormatException e) {
                return value / 1000.0; // Fallback to milliseconds
            }
        }
        return value / 1000.0;
    }

    private List<Path> listAudioFiles(Path folderPath) {
        try (Stream<Path> files = Files.list(folderPath)) {
            return files.filter(Files::isRegularFile)
                    .filter(this::isAudioFile)
                    .sorted(this::naturalCompare)
                    .toList();
        } catch (IOException e) {
            log.error("Failed to list audio files in folder: {}", folderPath, e);
            return Collections.emptyList();
        }
    }

    private boolean isAudioFile(Path path) {
        String fileName = path.getFileName().toString().toLowerCase();
        return AUDIO_EXTENSIONS.stream().anyMatch(fileName::endsWith);
    }

    private int naturalCompare(Path p1, Path p2) {
        return naturalCompare(p1.getFileName().toString(), p2.getFileName().toString());
    }

    /**
     * Natural alphanumeric sort comparison.
     * Handles filenames like "Track 1.mp3", "Track 2.mp3", ..., "Track 10.mp3" correctly.
     */
    private int naturalCompare(String s1, String s2) {
        int i1 = 0, i2 = 0;
        while (i1 < s1.length() && i2 < s2.length()) {
            char c1 = s1.charAt(i1);
            char c2 = s2.charAt(i2);

            if (Character.isDigit(c1) && Character.isDigit(c2)) {
                // Extract full numbers
                StringBuilder num1 = new StringBuilder();
                StringBuilder num2 = new StringBuilder();
                while (i1 < s1.length() && Character.isDigit(s1.charAt(i1))) {
                    num1.append(s1.charAt(i1++));
                }
                while (i2 < s2.length() && Character.isDigit(s2.charAt(i2))) {
                    num2.append(s2.charAt(i2++));
                }
                int cmp = Long.compare(Long.parseLong(num1.toString()), Long.parseLong(num2.toString()));
                if (cmp != 0) return cmp;
            } else {
                int cmp = Character.compare(Character.toLowerCase(c1), Character.toLowerCase(c2));
                if (cmp != 0) return cmp;
                i1++;
                i2++;
            }
        }
        return s1.length() - s2.length();
    }

    private String getTagValue(Tag tag, FieldKey... keys) {
        for (FieldKey key : keys) {
            try {
                String value = tag.getFirst(key);
                if (value != null && !value.isEmpty()) {
                    return value;
                }
            } catch (Exception e) {
                // Field not supported for this tag type
            }
        }
        return null;
    }

    private Integer parseChannels(String channels) {
        if (channels == null) return null;
        if (channels.toLowerCase().contains("stereo")) return 2;
        if (channels.toLowerCase().contains("mono")) return 1;
        try {
            return Integer.parseInt(channels.replaceAll("[^0-9]", ""));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private String getTrackTitleFromFilename(String filename) {
        // Remove extension
        int lastDot = filename.lastIndexOf('.');
        if (lastDot > 0) {
            filename = filename.substring(0, lastDot);
        }
        // Replace underscores and hyphens with spaces, clean up
        return filename.replaceAll("[_-]+", " ").trim();
    }

    // ==================== STREAMING METHODS ====================

    /**
     * Stream a file with HTTP Range support for seeking/scrubbing.
     * Implements HTTP 206 Partial Content for range requests.
     */
    public void streamWithRangeSupport(Path filePath, HttpServletRequest request, HttpServletResponse response) throws IOException {
        if (!Files.exists(filePath)) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND, "Audio file not found");
            return;
        }

        long fileSize = Files.size(filePath);
        String contentType = getContentType(filePath);
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
                log.debug("Client disconnected during audio streaming: {}", e.getMessage());
            } else {
                throw e;
            }
        }
    }

    /**
     * Check if the exception is due to client disconnect (common during seeking).
     */
    private boolean isClientDisconnect(IOException e) {
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

    private record RangeInfo(long start, long end) {
    }
}
