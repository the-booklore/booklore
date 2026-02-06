package org.booklore.service.library;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.booklore.model.FileProcessResult;
import org.booklore.model.dto.settings.LibraryFile;
import org.booklore.model.entity.BookEntity;
import org.booklore.model.entity.BookFileEntity;
import org.booklore.model.entity.LibraryEntity;
import org.booklore.model.enums.BookFileType;
import org.booklore.repository.BookAdditionalFileRepository;
import org.booklore.repository.BookRepository;
import org.booklore.service.event.BookEventBroadcaster;
import org.booklore.service.file.FileFingerprint;
import org.booklore.service.fileprocessor.BookFileProcessor;
import org.booklore.service.fileprocessor.BookFileProcessorRegistry;
import org.booklore.service.kobo.KoboAutoShelfService;
import org.booklore.service.metadata.extractor.AudiobookMetadataExtractor;
import org.booklore.service.metadata.extractor.MetadataExtractorFactory;
import org.booklore.util.BookCoverUtils;
import org.booklore.util.BookFileGroupingUtils;
import org.booklore.util.FileService;
import org.booklore.util.FileUtils;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@AllArgsConstructor
@Component
@Slf4j
public class FileAsBookProcessor {

    private final BookEventBroadcaster bookEventBroadcaster;
    private final BookFileProcessorRegistry processorRegistry;
    private final KoboAutoShelfService koboAutoShelfService;
    private final BookRepository bookRepository;
    private final BookAdditionalFileRepository bookAdditionalFileRepository;
    private final FileService fileService;
    private final MetadataExtractorFactory metadataExtractorFactory;
    private final AudiobookMetadataExtractor audiobookMetadataExtractor;

    @Transactional
    public void processLibraryFiles(List<LibraryFile> libraryFiles, LibraryEntity libraryEntity) {
        Map<String, List<LibraryFile>> groups = BookFileGroupingUtils.groupByBaseName(libraryFiles);
        processLibraryFilesGrouped(groups, libraryEntity);
    }

    @Transactional
    public void processLibraryFilesGrouped(Map<String, List<LibraryFile>> groups, LibraryEntity libraryEntity) {
        for (Map.Entry<String, List<LibraryFile>> entry : groups.entrySet()) {
            processGroupWithErrorHandling(entry.getValue(), libraryEntity);
        }
        log.info("Finished processing library '{}'", libraryEntity.getName());
    }

    private void processGroupWithErrorHandling(List<LibraryFile> group, LibraryEntity libraryEntity) {
        try {
            processGroup(group, libraryEntity);
        } catch (Exception e) {
            String fileNames = group.stream().map(LibraryFile::getFileName).toList().toString();
            log.error("Failed to process file group {}: {}", fileNames, e.getMessage());
        }
    }

    private void processGroup(List<LibraryFile> group, LibraryEntity libraryEntity) {
        Optional<LibraryFile> primaryFile = findBestPrimaryFile(group, libraryEntity);
        if (primaryFile.isEmpty()) {
            log.warn("No suitable book file found in group");
            return;
        }

        LibraryFile primary = primaryFile.get();
        log.info("Processing file: {}", primary.getFileName());

        BookFileType type = primary.getBookFileType();
        if (type == null) {
            log.warn("Unsupported file type for file: {}", primary.getFileName());
            return;
        }

        BookFileProcessor processor = processorRegistry.getProcessorOrThrow(type);
        FileProcessResult result = processor.processFile(primary);

        if (result == null || result.getBook() == null) {
            log.warn("Failed to process primary file: {}", primary.getFileName());
            return;
        }

        bookEventBroadcaster.broadcastBookAddEvent(result.getBook());
        koboAutoShelfService.autoAddBookToKoboShelves(result.getBook().getId());

        List<LibraryFile> additionalFiles = group.stream()
                .filter(f -> !f.equals(primary))
                .toList();

        if (!additionalFiles.isEmpty()) {
            BookEntity bookEntity = bookRepository.getReferenceById(result.getBook().getId());
            for (LibraryFile additionalFile : additionalFiles) {
                createAdditionalBookFile(bookEntity, additionalFile);
            }
        }
    }

    private Optional<LibraryFile> findBestPrimaryFile(List<LibraryFile> group, LibraryEntity libraryEntity) {
        List<BookFileType> formatPriority = libraryEntity.getFormatPriority();
        return group.stream()
                .filter(f -> f.getBookFileType() != null)
                .min(Comparator.comparingInt(f -> {
                    BookFileType bookFileType = f.getBookFileType();
                    if (formatPriority != null && !formatPriority.isEmpty()) {
                        int index = formatPriority.indexOf(bookFileType);
                        return index >= 0 ? index : Integer.MAX_VALUE;
                    }
                    return bookFileType.ordinal();
                }));
    }

    private void createAdditionalBookFile(BookEntity bookEntity, LibraryFile file) {
        Optional<BookFileEntity> existing = bookAdditionalFileRepository
                .findByLibraryPath_IdAndFileSubPathAndFileName(
                        file.getLibraryPathEntity().getId(), file.getFileSubPath(), file.getFileName());

        if (existing.isPresent()) {
            log.debug("Additional file already exists: {}", file.getFileName());
            return;
        }

        String hash;
        Long fileSizeKb;
        if (file.isFolderBased()) {
            hash = FileFingerprint.generateFolderHash(file.getFullPath());
            fileSizeKb = FileUtils.getFolderSizeInKb(file.getFullPath());
        } else {
            hash = FileFingerprint.generateHash(file.getFullPath());
            fileSizeKb = FileUtils.getFileSizeInKb(file.getFullPath());
        }

        BookFileEntity additionalFile = BookFileEntity.builder()
                .book(bookEntity)
                .fileName(file.getFileName())
                .fileSubPath(file.getFileSubPath())
                .isBookFormat(true)
                .folderBased(file.isFolderBased())
                .bookType(file.getBookFileType())
                .fileSizeKb(fileSizeKb)
                .initialHash(hash)
                .currentHash(hash)
                .addedOn(Instant.now())
                .build();

        try {
            bookAdditionalFileRepository.save(additionalFile);
            log.info("Attached additional format {} to book: {}", file.getFileName(), bookEntity.getPrimaryBookFile().getFileName());
            generateCoverFromAdditionalFile(bookEntity, file);
        } catch (Exception e) {
            log.error("Error creating additional file {}: {}", file.getFileName(), e.getMessage());
        }
    }

    void generateCoverFromAdditionalFile(BookEntity bookEntity, LibraryFile additionalFile) {
        BookFileType additionalType = additionalFile.getBookFileType();
        boolean additionalIsAudiobook = additionalType == BookFileType.AUDIOBOOK;

        // For fileless books, generate cover based on the additional file type
        if (!bookEntity.hasFiles()) {
            try {
                if (additionalIsAudiobook) {
                    generateAudiobookCoverFromFile(bookEntity, additionalFile);
                } else {
                    generateEbookCoverFromFile(bookEntity, additionalFile);
                }
            } catch (Exception e) {
                log.warn("Failed to generate cover from additional file {}: {}", additionalFile.getFileName(), e.getMessage());
            }
            return;
        }

        BookFileType primaryType = bookEntity.getPrimaryBookFile().getBookType();
        boolean primaryIsAudiobook = primaryType == BookFileType.AUDIOBOOK;

        // Only generate cover when mixing audiobook with ebook formats
        if (primaryIsAudiobook == additionalIsAudiobook) {
            return;
        }

        try {
            if (additionalIsAudiobook) {
                generateAudiobookCoverFromFile(bookEntity, additionalFile);
            } else {
                generateEbookCoverFromFile(bookEntity, additionalFile);
            }
        } catch (Exception e) {
            log.warn("Failed to generate cover from additional file {}: {}", additionalFile.getFileName(), e.getMessage());
        }
    }

    private void generateAudiobookCoverFromFile(BookEntity bookEntity, LibraryFile audioFile) {
        try {
            File file = getFileForCoverExtraction(audioFile);
            if (file == null || !file.exists()) {
                log.debug("Audio file not found for cover extraction: {}", audioFile.getFileName());
                return;
            }

            byte[] coverData = audiobookMetadataExtractor.extractCover(file);
            if (coverData == null) {
                log.debug("No cover image found in audiobook '{}'", audioFile.getFileName());
                return;
            }

            try (ByteArrayInputStream bais = new ByteArrayInputStream(coverData)) {
                BufferedImage originalImage = FileService.readImage(bais);
                if (originalImage == null) {
                    log.warn("Failed to decode cover image for audiobook '{}'", audioFile.getFileName());
                    return;
                }
                boolean saved = fileService.saveAudiobookCoverImages(originalImage, bookEntity.getId());
                originalImage.flush();

                if (saved) {
                    bookEntity.getMetadata().setAudiobookCoverUpdatedOn(Instant.now());
                    bookEntity.setAudiobookCoverHash(BookCoverUtils.generateCoverHash());
                    bookRepository.save(bookEntity);
                    log.info("Generated audiobook cover from additional file: {}", audioFile.getFileName());
                }
            }
        } catch (Exception e) {
            log.warn("Error generating audiobook cover from {}: {}", audioFile.getFileName(), e.getMessage());
        }
    }

    private void generateEbookCoverFromFile(BookEntity bookEntity, LibraryFile ebookFile) {
        try {
            File file = ebookFile.getFullPath().toFile();
            if (!file.exists()) {
                log.debug("Ebook file not found for cover extraction: {}", ebookFile.getFileName());
                return;
            }

            var extractor = metadataExtractorFactory.getExtractor(ebookFile.getBookFileType());
            if (extractor == null) {
                log.debug("No extractor available for file type: {}", ebookFile.getBookFileType());
                return;
            }

            byte[] coverData = extractor.extractCover(file);
            if (coverData == null) {
                log.debug("No cover image found in ebook '{}'", ebookFile.getFileName());
                return;
            }

            try (ByteArrayInputStream bais = new ByteArrayInputStream(coverData)) {
                BufferedImage originalImage = FileService.readImage(bais);
                if (originalImage == null) {
                    log.warn("Failed to decode cover image for ebook '{}'", ebookFile.getFileName());
                    return;
                }
                boolean saved = fileService.saveCoverImages(originalImage, bookEntity.getId());
                originalImage.flush();

                if (saved) {
                    FileService.setBookCoverPath(bookEntity.getMetadata());
                    bookEntity.setBookCoverHash(BookCoverUtils.generateCoverHash());
                    bookRepository.save(bookEntity);
                    log.info("Generated ebook cover from additional file: {}", ebookFile.getFileName());
                }
            }
        } catch (Exception e) {
            log.warn("Error generating ebook cover from {}: {}", ebookFile.getFileName(), e.getMessage());
        }
    }

    private File getFileForCoverExtraction(LibraryFile libraryFile) {
        if (libraryFile.isFolderBased()) {
            Path folderPath = libraryFile.getFullPath();
            return FileUtils.getFirstAudioFileInFolder(folderPath)
                    .map(Path::toFile)
                    .orElse(null);
        } else {
            return libraryFile.getFullPath().toFile();
        }
    }
}
