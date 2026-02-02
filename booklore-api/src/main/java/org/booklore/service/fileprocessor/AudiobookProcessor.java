package org.booklore.service.fileprocessor;

import org.booklore.mapper.BookMapper;
import org.booklore.model.dto.BookMetadata;
import org.booklore.model.dto.settings.LibraryFile;
import org.booklore.model.entity.BookEntity;
import org.booklore.model.entity.BookMetadataEntity;
import org.booklore.model.enums.BookFileType;
import org.booklore.repository.BookAdditionalFileRepository;
import org.booklore.repository.BookRepository;
import org.booklore.service.book.BookCreatorService;
import org.booklore.service.metadata.MetadataMatchService;
import org.booklore.service.metadata.extractor.AudiobookMetadataExtractor;
import org.booklore.util.BookCoverUtils;
import org.booklore.util.FileService;
import org.booklore.util.FileUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.booklore.util.FileService.truncate;

@Slf4j
@Service
public class AudiobookProcessor extends AbstractFileProcessor implements BookFileProcessor {

    private final AudiobookMetadataExtractor audiobookMetadataExtractor;

    public AudiobookProcessor(BookRepository bookRepository,
                              BookAdditionalFileRepository bookAdditionalFileRepository,
                              BookCreatorService bookCreatorService,
                              BookMapper bookMapper,
                              FileService fileService,
                              MetadataMatchService metadataMatchService,
                              AudiobookMetadataExtractor audiobookMetadataExtractor) {
        super(bookRepository, bookAdditionalFileRepository, bookCreatorService, bookMapper, fileService, metadataMatchService);
        this.audiobookMetadataExtractor = audiobookMetadataExtractor;
    }

    @Override
    public BookEntity processNewFile(LibraryFile libraryFile) {
        BookEntity bookEntity = bookCreatorService.createShellBook(libraryFile, BookFileType.AUDIOBOOK);
        setBookMetadata(bookEntity, libraryFile.isFolderBased());
        if (generateCover(bookEntity, libraryFile.isFolderBased())) {
            FileService.setBookCoverPath(bookEntity.getMetadata());
            bookEntity.setBookCoverHash(BookCoverUtils.generateCoverHash());
        }
        return bookEntity;
    }

    @Override
    public boolean generateCover(BookEntity bookEntity) {
        return generateCover(bookEntity, bookEntity.getPrimaryBookFile().isFolderBased());
    }

    public boolean generateCover(BookEntity bookEntity, boolean isFolderBased) {
        try {
            File audioFile = getAudioFileForMetadata(bookEntity, isFolderBased);
            if (audioFile == null || !audioFile.exists()) {
                log.debug("Audio file not found for audiobook '{}'", bookEntity.getPrimaryBookFile().getFileName());
                return false;
            }

            byte[] coverData = audiobookMetadataExtractor.extractCover(audioFile);

            if (coverData == null) {
                log.debug("No cover image found in audiobook '{}'", bookEntity.getPrimaryBookFile().getFileName());
                return false;
            }

            boolean saved;
            try (ByteArrayInputStream bais = new ByteArrayInputStream(coverData)) {
                BufferedImage originalImage = FileService.readImage(bais);
                if (originalImage == null) {
                    log.warn("Failed to decode cover image for audiobook '{}'", bookEntity.getPrimaryBookFile().getFileName());
                    return false;
                }
                saved = fileService.saveCoverImages(originalImage, bookEntity.getId());
                originalImage.flush();
            }

            return saved;

        } catch (Exception e) {
            log.error("Error generating cover for audiobook '{}': {}", bookEntity.getPrimaryBookFile().getFileName(), e.getMessage(), e);
            return false;
        }
    }

    /**
     * Get the audio file to use for metadata extraction.
     * For folder-based audiobooks, returns the first audio file in the folder.
     */
    private File getAudioFileForMetadata(BookEntity bookEntity, boolean isFolderBased) {
        if (isFolderBased) {
            java.nio.file.Path folderPath = bookEntity.getFullFilePath();
            return FileUtils.getFirstAudioFileInFolder(folderPath)
                    .map(java.nio.file.Path::toFile)
                    .orElse(null);
        } else {
            return new File(FileUtils.getBookFullPath(bookEntity));
        }
    }

    @Override
    public List<BookFileType> getSupportedTypes() {
        return List.of(BookFileType.AUDIOBOOK);
    }

    private void setBookMetadata(BookEntity bookEntity, boolean isFolderBased) {
        File bookFile = getAudioFileForMetadata(bookEntity, isFolderBased);
        if (bookFile == null || !bookFile.exists()) {
            log.warn("Audio file not found for metadata extraction: {}", bookEntity.getPrimaryBookFile().getFileName());
            return;
        }

        BookMetadata audioMetadata = audiobookMetadataExtractor.extractMetadata(bookFile);
        if (audioMetadata == null) return;

        BookMetadataEntity metadata = bookEntity.getMetadata();

        metadata.setTitle(truncate(audioMetadata.getTitle(), 1000));
        metadata.setSubtitle(truncate(audioMetadata.getSubtitle(), 1000));
        metadata.setDescription(truncate(audioMetadata.getDescription(), 2000));
        metadata.setPublisher(truncate(audioMetadata.getPublisher(), 1000));
        metadata.setPublishedDate(audioMetadata.getPublishedDate());
        metadata.setSeriesName(truncate(audioMetadata.getSeriesName(), 1000));
        metadata.setSeriesNumber(audioMetadata.getSeriesNumber());
        metadata.setSeriesTotal(audioMetadata.getSeriesTotal());

        String lang = audioMetadata.getLanguage();
        metadata.setLanguage(truncate((lang == null || "UND".equalsIgnoreCase(lang)) ? "en" : lang, 1000));

        bookCreatorService.addAuthorsToBook(audioMetadata.getAuthors(), bookEntity);

        if (audioMetadata.getCategories() != null) {
            Set<String> validCategories = audioMetadata.getCategories().stream()
                    .filter(s -> s != null && !s.isBlank() && s.length() <= 100 && !s.contains("\n") && !s.contains("\r") && !s.contains("  "))
                    .collect(Collectors.toSet());
            bookCreatorService.addCategoriesToBook(validCategories, bookEntity);
        }
    }
}
