package org.booklore.service.reader;

import org.booklore.model.dto.response.AudiobookChapter;
import org.booklore.model.dto.response.AudiobookInfo;
import org.booklore.model.dto.response.AudiobookTrack;
import org.booklore.model.entity.BookFileEntity;
import org.booklore.model.entity.BookMetadataEntity;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jaudiotagger.audio.AudioFile;
import org.jaudiotagger.audio.AudioFileIO;
import org.jaudiotagger.audio.AudioHeader;
import org.jaudiotagger.tag.FieldKey;
import org.jaudiotagger.tag.Tag;
import org.jaudiotagger.tag.images.Artwork;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AudioMetadataService {

    private final AudioFileUtilityService audioFileUtility;

    public AudiobookInfo getMetadata(BookFileEntity bookFile, Path audioPath) throws Exception {
        if (bookFile.isFolderBased()) {
            return buildFolderBasedAudiobookInfo(bookFile, audioPath);
        } else {
            return buildSingleFileAudiobookInfo(bookFile, audioPath);
        }
    }

    private AudiobookInfo buildSingleFileAudiobookInfo(BookFileEntity bookFile, Path audioPath) throws Exception {
        AudiobookInfo.AudiobookInfoBuilder builder = AudiobookInfo.builder()
                .bookId(bookFile.getBook().getId())
                .bookFileId(bookFile.getId())
                .folderBased(false);

        if (bookFile.getDurationSeconds() != null) {
            BookMetadataEntity metadata = bookFile.getBook().getMetadata();
            String narrator = metadata != null ? metadata.getNarrator() : null;

            builder.narrator(narrator)
                    .durationMs(bookFile.getDurationSeconds() * 1000)
                    .bitrate(bookFile.getBitrate())
                    .sampleRate(bookFile.getSampleRate())
                    .channels(bookFile.getChannels())
                    .codec(bookFile.getCodec());

            if (bookFile.getChapters() != null && !bookFile.getChapters().isEmpty()) {
                List<AudiobookChapter> chapters = bookFile.getChapters().stream()
                        .map(ch -> AudiobookChapter.builder()
                                .index(ch.getIndex())
                                .title(ch.getTitle())
                                .startTimeMs(ch.getStartTimeMs())
                                .endTimeMs(ch.getEndTimeMs())
                                .durationMs(ch.getDurationMs())
                                .build())
                        .collect(Collectors.toList());
                builder.chapters(chapters);
            }

            if (metadata != null) {
                builder.title(metadata.getTitle());
                if (metadata.getAuthors() != null && !metadata.getAuthors().isEmpty()) {
                    builder.author(metadata.getAuthors().iterator().next().getName());
                }
            }

            return builder.build();
        }

        log.debug("No DB metadata found for audiobook {}, extracting from file", bookFile.getId());
        return extractSingleFileMetadata(builder, audioPath);
    }

    private AudiobookInfo buildFolderBasedAudiobookInfo(BookFileEntity bookFile, Path folderPath) throws Exception {
        AudiobookInfo.AudiobookInfoBuilder builder = AudiobookInfo.builder()
                .bookId(bookFile.getBook().getId())
                .bookFileId(bookFile.getId())
                .folderBased(true);

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

        BookMetadataEntity metadata = bookFile.getBook().getMetadata();
        if (metadata != null && metadata.getNarrator() != null) {
            builder.narrator(metadata.getNarrator());
        } else {
            builder.narrator(narrator);
        }

        if (bookFile.getBitrate() != null) {
            builder.bitrate(bookFile.getBitrate());
        } else {
            builder.bitrate(bitrate);
        }

        if (bookFile.getCodec() != null) {
            builder.codec(bookFile.getCodec());
        } else {
            builder.codec(codec);
        }

        if (bookFile.getSampleRate() != null) {
            builder.sampleRate(bookFile.getSampleRate());
        } else {
            builder.sampleRate(sampleRate);
        }

        if (bookFile.getChannels() != null) {
            builder.channels(bookFile.getChannels());
        } else {
            builder.channels(channels);
        }

        if (metadata != null) {
            String bookTitle = metadata.getTitle();
            if (bookTitle != null) {
                title = bookTitle;
            }
            if (metadata.getAuthors() != null && !metadata.getAuthors().isEmpty()) {
                author = metadata.getAuthors().iterator().next().getName();
            }
        }

        return builder
                .title(title)
                .author(author)
                .durationMs(totalDurationMs)
                .tracks(tracks)
                .build();
    }

    private AudiobookInfo extractSingleFileMetadata(AudiobookInfo.AudiobookInfoBuilder builder, Path audioPath) throws Exception {
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

        List<AudiobookChapter> chapters = new ArrayList<>();
        chapters.add(AudiobookChapter.builder()
                .index(0)
                .title("Full Audiobook")
                .startTimeMs(0L)
                .endTimeMs(durationMs)
                .durationMs(durationMs)
                .build());
        builder.chapters(chapters);

        return builder.build();
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
