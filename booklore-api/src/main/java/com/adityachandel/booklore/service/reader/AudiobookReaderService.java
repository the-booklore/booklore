package com.adityachandel.booklore.service.reader;

import com.adityachandel.booklore.exception.ApiError;
import com.adityachandel.booklore.model.dto.response.AudiobookChapter;
import com.adityachandel.booklore.model.dto.response.AudiobookInfo;
import com.adityachandel.booklore.model.dto.response.AudiobookTrack;
import com.adityachandel.booklore.model.entity.BookEntity;
import com.adityachandel.booklore.model.entity.BookFileEntity;
import com.adityachandel.booklore.model.enums.BookFileType;
import com.adityachandel.booklore.repository.BookRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jaudiotagger.audio.AudioFile;
import org.jaudiotagger.audio.AudioFileIO;
import org.jaudiotagger.audio.AudioHeader;
import org.jaudiotagger.tag.Tag;
import org.jaudiotagger.tag.FieldKey;
import org.jaudiotagger.tag.images.Artwork;
import org.jaudiotagger.tag.mp4.Mp4Tag;
import org.springframework.stereotype.Service;

import java.io.IOException;
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

        // Extract chapters for M4B/M4A
        List<AudiobookChapter> chapters = extractChapters(audioFile, durationMs);
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

    private List<AudiobookChapter> extractChapters(AudioFile audioFile, long totalDurationMs) {
        List<AudiobookChapter> chapters = new ArrayList<>();

        try {
            Tag tag = audioFile.getTag();
            if (tag instanceof Mp4Tag mp4Tag) {
                // Note: JAudioTagger doesn't provide direct access to M4B chapter atoms
                // Chapter extraction from M4B files would require parsing the MP4 atom structure directly
                // For now, we fall back to a single chapter spanning the entire duration
                log.debug("M4B file detected, chapter extraction not yet supported via JAudioTagger");
            }
        } catch (Exception e) {
            log.debug("Could not extract embedded chapters: {}", e.getMessage());
        }

        // If no chapters found, create a single chapter spanning the entire duration
        if (chapters.isEmpty()) {
            chapters.add(AudiobookChapter.builder()
                    .index(0)
                    .title("Full Audiobook")
                    .startTimeMs(0L)
                    .endTimeMs(totalDurationMs)
                    .durationMs(totalDurationMs)
                    .build());
        }

        return chapters;
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
}
