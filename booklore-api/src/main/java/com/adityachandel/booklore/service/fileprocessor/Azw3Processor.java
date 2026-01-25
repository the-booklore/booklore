package com.adityachandel.booklore.service.fileprocessor;

import com.adityachandel.booklore.mapper.BookMapper;
import com.adityachandel.booklore.model.dto.BookMetadata;
import com.adityachandel.booklore.model.dto.settings.LibraryFile;
import com.adityachandel.booklore.model.entity.BookEntity;
import com.adityachandel.booklore.model.entity.BookMetadataEntity;
import com.adityachandel.booklore.model.enums.BookFileType;
import com.adityachandel.booklore.repository.BookAdditionalFileRepository;
import com.adityachandel.booklore.repository.BookRepository;
import com.adityachandel.booklore.service.book.BookCreatorService;
import com.adityachandel.booklore.service.metadata.MetadataMatchService;
import com.adityachandel.booklore.service.metadata.extractor.Azw3MetadataExtractor;
import com.adityachandel.booklore.util.BookCoverUtils;
import com.adityachandel.booklore.util.FileService;
import com.adityachandel.booklore.util.FileUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.awt.image.BufferedImage;
import java.io.File;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static com.adityachandel.booklore.util.FileService.truncate;

@Slf4j
@Service
public class Azw3Processor extends AbstractFileProcessor implements BookFileProcessor {

    private final Azw3MetadataExtractor azw3MetadataExtractor;

    public Azw3Processor(BookRepository bookRepository,
                         BookAdditionalFileRepository bookAdditionalFileRepository,
                         BookCreatorService bookCreatorService,
                         BookMapper bookMapper,
                         FileService fileService,
                         MetadataMatchService metadataMatchService,
                         Azw3MetadataExtractor azw3MetadataExtractor) {
        super(bookRepository, bookAdditionalFileRepository, bookCreatorService, bookMapper, fileService, metadataMatchService);
        this.azw3MetadataExtractor = azw3MetadataExtractor;
    }

    @Override
    public BookEntity processNewFile(LibraryFile libraryFile) {
        BookFileType fileType = determineFileType(libraryFile.getFileName());
        BookEntity bookEntity = bookCreatorService.createShellBook(libraryFile, fileType);
        setBookMetadata(bookEntity);
        if (generateCover(bookEntity)) {
            FileService.setBookCoverPath(bookEntity.getMetadata());
            bookEntity.setBookCoverHash(BookCoverUtils.generateCoverHash());
        }
        return bookEntity;
    }

    @Override
    public boolean generateCover(BookEntity bookEntity) {
        try {
            File azw3File = new File(FileUtils.getBookFullPath(bookEntity));
            byte[] coverData = azw3MetadataExtractor.extractCover(azw3File);

            if (coverData == null || coverData.length == 0) {
                log.warn("No cover image found in AZW3 '{}'", bookEntity.getPrimaryBookFile());
                return false;
            }

            return saveCoverImage(coverData, bookEntity.getId());

        } catch (Exception e) {
            log.error("Error generating cover for AZW3 '{}': {}", bookEntity.getPrimaryBookFile(), e.getMessage(), e);
            return false;
        }
    }

    @Override
    public List<BookFileType> getSupportedTypes() {
        return List.of(BookFileType.AZW3);
    }

    private BookFileType determineFileType(String fileName) {
        return BookFileType.AZW3;
    }

    private void setBookMetadata(BookEntity bookEntity) {
        File bookFile = new File(bookEntity.getFullFilePath().toUri());
        BookMetadata azw3Metadata = azw3MetadataExtractor.extractMetadata(bookFile);
        if (azw3Metadata == null) return;

        BookMetadataEntity metadata = bookEntity.getMetadata();

        metadata.setTitle(truncate(azw3Metadata.getTitle(), 1000));
        metadata.setSubtitle(truncate(azw3Metadata.getSubtitle(), 1000));
        metadata.setDescription(truncate(azw3Metadata.getDescription(), 2000));
        metadata.setPublisher(truncate(azw3Metadata.getPublisher(), 1000));
        metadata.setPublishedDate(azw3Metadata.getPublishedDate());
        metadata.setSeriesName(truncate(azw3Metadata.getSeriesName(), 1000));
        metadata.setSeriesNumber(azw3Metadata.getSeriesNumber());
        metadata.setSeriesTotal(azw3Metadata.getSeriesTotal());
        metadata.setIsbn13(truncate(azw3Metadata.getIsbn13(), 64));
        metadata.setIsbn10(truncate(azw3Metadata.getIsbn10(), 64));
        metadata.setPageCount(azw3Metadata.getPageCount());

        String lang = azw3Metadata.getLanguage();
        metadata.setLanguage(truncate((lang == null || "UND".equalsIgnoreCase(lang)) ? "en" : lang, 1000));

        metadata.setAsin(truncate(azw3Metadata.getAsin(), 20));
        metadata.setAmazonRating(azw3Metadata.getAmazonRating());
        metadata.setAmazonReviewCount(azw3Metadata.getAmazonReviewCount());
        metadata.setGoodreadsId(truncate(azw3Metadata.getGoodreadsId(), 100));
        metadata.setGoodreadsRating(azw3Metadata.getGoodreadsRating());
        metadata.setGoodreadsReviewCount(azw3Metadata.getGoodreadsReviewCount());
        metadata.setHardcoverId(truncate(azw3Metadata.getHardcoverId(), 100));
        metadata.setHardcoverRating(azw3Metadata.getHardcoverRating());
        metadata.setHardcoverReviewCount(azw3Metadata.getHardcoverReviewCount());
        metadata.setGoogleId(truncate(azw3Metadata.getGoogleId(), 100));
        metadata.setComicvineId(truncate(azw3Metadata.getComicvineId(), 100));
        metadata.setRanobedbId(truncate(azw3Metadata.getRanobedbId(), 100));
        metadata.setRanobedbRating(azw3Metadata.getRanobedbRating());

        bookCreatorService.addAuthorsToBook(azw3Metadata.getAuthors(), bookEntity);

        if (azw3Metadata.getCategories() != null) {
            Set<String> validSubjects = azw3Metadata.getCategories().stream()
                    .filter(s -> s != null && !s.isBlank() && s.length() <= 100 && !s.contains("\n") && !s.contains("\r") && !s.contains("  "))
                    .collect(Collectors.toSet());
            bookCreatorService.addCategoriesToBook(validSubjects, bookEntity);
        }
    }

    private boolean saveCoverImage(byte[] coverData, long bookId) throws Exception {
        BufferedImage originalImage = FileService.readImage(coverData);
        if (originalImage == null) {
            log.warn("Failed to decode cover image for AZW3");
            return false;
        }

        return fileService.saveCoverImages(originalImage, bookId);
    }
}

