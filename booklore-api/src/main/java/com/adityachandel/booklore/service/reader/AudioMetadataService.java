package com.adityachandel.booklore.service.reader;

import com.adityachandel.booklore.model.dto.response.AudiobookChapter;
import com.adityachandel.booklore.model.dto.response.AudiobookInfo;
import com.adityachandel.booklore.model.dto.response.AudiobookTrack;
import com.adityachandel.booklore.model.entity.BookFileEntity;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jaudiotagger.audio.AudioFile;
import org.jaudiotagger.audio.AudioFileIO;
import org.jaudiotagger.audio.AudioHeader;
import org.jaudiotagger.tag.FieldKey;
import org.jaudiotagger.tag.Tag;
import org.jaudiotagger.tag.images.Artwork;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

@Slf4j
@Service
@RequiredArgsConstructor
public class AudioMetadataService {

    private static final int MAX_CACHE_ENTRIES = 50;

    private final AudioFileUtilityService audioFileUtility;
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

    public AudiobookInfo getMetadata(BookFileEntity bookFile, Path audioPath) throws Exception {
        CachedAudiobookMetadata metadata = getCachedMetadata(bookFile, audioPath);
        return metadata.info;
    }

    public byte[] getEmbeddedCoverArt(Path audioPath) {
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
            log.debug("No embedded cover art found: {}", e.getMessage());
        }
        return null;
    }

    public String getCoverArtMimeType(Path audioPath) {
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
                    byte[] data = artwork.getBinaryData();
                    if (data != null && data.length > 2) {
                        if (data[0] == (byte) 0xFF && data[1] == (byte) 0xD8) {
                            return "image/jpeg";
                        } else if (data[0] == (byte) 0x89 && data[1] == (byte) 0x50) {
                            return "image/png";
                        }
                    }
                    return "image/jpeg";
                }
            }
        } catch (Exception e) {
            log.debug("Could not determine cover art MIME type: {}", e.getMessage());
        }
        return "image/jpeg";
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
            try (Stream<Path> files = Files.list(audioPath)) {
                return files.filter(audioFileUtility::isAudioFile)
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

        long durationMs = (long) (header.getPreciseTrackLength() * 1000);
        long bitrateValue = header.getBitRateAsNumber();
        builder.durationMs(durationMs)
                .bitrate(bitrateValue > 0 ? (int) bitrateValue : null)
                .codec(header.getEncodingType())
                .sampleRate(header.getSampleRateAsNumber())
                .channels(parseChannels(header.getChannels()));

        if (tag != null) {
            builder.title(getTagValue(tag, FieldKey.TITLE, FieldKey.ALBUM))
                    .author(getTagValue(tag, FieldKey.ARTIST, FieldKey.ALBUM_ARTIST))
                    .narrator(getTagValue(tag, FieldKey.COMPOSER));
        }

        List<AudiobookChapter> chapters = extractChapters(audioPath, durationMs);
        builder.chapters(chapters);

        return builder.build();
    }

    private AudiobookInfo parseFolderBasedAudiobook(AudiobookInfo.AudiobookInfoBuilder builder, Path folderPath) throws Exception {
        List<Path> audioFiles = audioFileUtility.listAudioFiles(folderPath);
        if (audioFiles.isEmpty()) {
            throw new IllegalStateException("No audio files found in folder: " + folderPath);
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
                    trackTitle = audioFileUtility.getTrackTitleFromFilename(trackPath.getFileName().toString());
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
                tracks.add(AudiobookTrack.builder()
                        .index(i)
                        .fileName(trackPath.getFileName().toString())
                        .title(audioFileUtility.getTrackTitleFromFilename(trackPath.getFileName().toString()))
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

        try {
            chapters = extractChaptersWithFFprobe(audioPath);
            if (!chapters.isEmpty()) {
                log.debug("Extracted {} chapters from {} using FFprobe", chapters.size(), audioPath.getFileName());
                return chapters;
            }
        } catch (Exception e) {
            log.debug("FFprobe chapter extraction failed for {}: {}", audioPath.getFileName(), e.getMessage());
        }

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

            double startTime = 0;
            double endTime = 0;

            if (chapterNode.has("start_time")) {
                startTime = chapterNode.get("start_time").asDouble();
            } else if (chapterNode.has("start")) {
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
        String[] parts = timeBase.split("/");
        if (parts.length == 2) {
            try {
                double numerator = Double.parseDouble(parts[0]);
                double denominator = Double.parseDouble(parts[1]);
                return value * (numerator / denominator);
            } catch (NumberFormatException e) {
                return value / 1000.0;
            }
        }
        return value / 1000.0;
    }

    private String getTagValue(Tag tag, FieldKey... keys) {
        for (FieldKey key : keys) {
            try {
                String value = tag.getFirst(key);
                if (value != null && !value.isEmpty()) {
                    return value;
                }
            } catch (Exception e) {
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
}
