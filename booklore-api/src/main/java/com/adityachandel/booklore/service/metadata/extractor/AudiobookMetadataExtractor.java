package com.adityachandel.booklore.service.metadata.extractor;

import com.adityachandel.booklore.model.dto.BookMetadata;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.StringUtils;
import org.jaudiotagger.audio.AudioFile;
import org.jaudiotagger.audio.AudioFileIO;
import org.jaudiotagger.tag.FieldKey;
import org.jaudiotagger.tag.Tag;
import org.jaudiotagger.tag.images.Artwork;
import org.springframework.stereotype.Component;

import java.io.File;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

@Slf4j
@Component
public class AudiobookMetadataExtractor implements FileMetadataExtractor {

    static {
        // Suppress verbose JAudioTagger logging
        Logger.getLogger("org.jaudiotagger").setLevel(Level.WARNING);
    }

    @Override
    public BookMetadata extractMetadata(File audioFile) {
        try {
            AudioFile f = AudioFileIO.read(audioFile);
            Tag tag = f.getTag();

            if (tag == null) {
                return BookMetadata.builder()
                        .title(FilenameUtils.getBaseName(audioFile.getName()))
                        .build();
            }

            BookMetadata.BookMetadataBuilder builder = BookMetadata.builder();

            // Title - prefer album for audiobooks, fallback to title, then filename
            String album = tag.getFirst(FieldKey.ALBUM);
            String title = tag.getFirst(FieldKey.TITLE);
            if (StringUtils.isNotBlank(album)) {
                builder.title(album);
            } else if (StringUtils.isNotBlank(title)) {
                builder.title(title);
            } else {
                builder.title(FilenameUtils.getBaseName(audioFile.getName()));
            }

            // Author - use album artist or artist
            String albumArtist = tag.getFirst(FieldKey.ALBUM_ARTIST);
            String artist = tag.getFirst(FieldKey.ARTIST);
            Set<String> authors = new HashSet<>();
            if (StringUtils.isNotBlank(albumArtist)) {
                authors.add(albumArtist);
            } else if (StringUtils.isNotBlank(artist)) {
                authors.add(artist);
            }
            if (!authors.isEmpty()) {
                builder.authors(authors);
            }

            // Description - use comment field
            String comment = tag.getFirst(FieldKey.COMMENT);
            if (StringUtils.isNotBlank(comment)) {
                builder.description(comment);
            }

            // Publisher - use record label or publisher
            String publisher = tag.getFirst(FieldKey.RECORD_LABEL);
            if (StringUtils.isNotBlank(publisher)) {
                builder.publisher(publisher);
            }

            // Year/Date
            String year = tag.getFirst(FieldKey.YEAR);
            if (StringUtils.isNotBlank(year)) {
                try {
                    int yearInt = Integer.parseInt(year.trim().substring(0, Math.min(4, year.trim().length())));
                    if (yearInt >= 1 && yearInt <= 9999) {
                        builder.publishedDate(LocalDate.of(yearInt, 1, 1));
                    }
                } catch (NumberFormatException ignored) {
                }
            }

            // Genre as category
            String genre = tag.getFirst(FieldKey.GENRE);
            if (StringUtils.isNotBlank(genre)) {
                Set<String> categories = new HashSet<>();
                categories.add(genre);
                builder.categories(categories);
            }

            // Language
            String language = tag.getFirst(FieldKey.LANGUAGE);
            if (StringUtils.isNotBlank(language)) {
                builder.language(language);
            }

            // Series info from grouping or custom tags
            String grouping = tag.getFirst(FieldKey.GROUPING);
            if (StringUtils.isNotBlank(grouping)) {
                builder.seriesName(grouping);
            }

            // Try to extract track number as series number for multi-part audiobooks
            String trackNo = tag.getFirst(FieldKey.TRACK);
            if (StringUtils.isNotBlank(trackNo)) {
                try {
                    String trackNum = trackNo.contains("/") ? trackNo.split("/")[0] : trackNo;
                    builder.seriesNumber((float) Integer.parseInt(trackNum.trim()));
                } catch (NumberFormatException ignored) {
                }
            }

            // Try to get total tracks
            String trackTotal = tag.getFirst(FieldKey.TRACK_TOTAL);
            if (StringUtils.isNotBlank(trackTotal)) {
                try {
                    builder.seriesTotal(Integer.parseInt(trackTotal.trim()));
                } catch (NumberFormatException ignored) {
                }
            }

            return builder.build();

        } catch (Exception e) {
            log.error("Failed to read metadata from audio file {}: {}", audioFile.getName(), e.getMessage(), e);
            return BookMetadata.builder()
                    .title(FilenameUtils.getBaseName(audioFile.getName()))
                    .build();
        }
    }

    @Override
    public byte[] extractCover(File audioFile) {
        try {
            AudioFile f = AudioFileIO.read(audioFile);
            Tag tag = f.getTag();

            if (tag == null) {
                return null;
            }

            Artwork artwork = tag.getFirstArtwork();
            if (artwork != null) {
                return artwork.getBinaryData();
            }

            return null;
        } catch (Exception e) {
            log.warn("Failed to extract cover from audio file {}: {}", audioFile.getName(), e.getMessage());
            return null;
        }
    }
}
