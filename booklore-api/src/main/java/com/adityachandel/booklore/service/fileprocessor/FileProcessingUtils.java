package com.adityachandel.booklore.service.fileprocessor;

import com.adityachandel.booklore.model.dto.Book;
import com.adityachandel.booklore.model.dto.settings.LibraryFile;
import com.adityachandel.booklore.model.entity.BookAdditionalFileEntity;
import com.adityachandel.booklore.model.entity.BookEntity;
import com.adityachandel.booklore.model.entity.BookMetadataEntity;
import com.adityachandel.booklore.repository.BookAdditionalFileRepository;
import com.adityachandel.booklore.repository.BookRepository;
import com.adityachandel.booklore.service.appsettings.AppSettingService;
import com.adityachandel.booklore.util.FileService;
import com.adityachandel.booklore.mapper.BookMapper;
import jakarta.transaction.Transactional;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.Comparator;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

@AllArgsConstructor
@Service
@Slf4j
public class FileProcessingUtils {

    private final FileService fileService;
    private final AppSettingService appSettingService;

    public void setBookCoverPath(long bookId, BookMetadataEntity bookMetadataEntity) {
        bookMetadataEntity.setThumbnail(fileService.getThumbnailPath(bookId) + "/f.jpg");
        bookMetadataEntity.setCoverUpdatedOn(Instant.now());
    }

    public boolean saveCoverImage(BufferedImage coverImage, long bookId) throws IOException {
        String resolution = appSettingService.getAppSettings().getCoverResolution();
        String[] split = resolution.split("x");
        int x = Integer.parseInt(split[0]);
        int y = Integer.parseInt(split[1]);

        BufferedImage resizedImage = resizeImage(coverImage, x, y);
        File bookDirectory = new File(fileService.getThumbnailPath(bookId));
        if (!bookDirectory.exists()) {
            if (!bookDirectory.mkdirs()) {
                throw new IOException("Failed to create directory: " + bookDirectory.getAbsolutePath());
            }
        }
        String coverImageName = "f.jpg";
        File coverImageFile = new File(bookDirectory, coverImageName);
        return ImageIO.write(resizedImage, "JPEG", coverImageFile);
    }

    public BufferedImage resizeImage(BufferedImage originalImage, int width, int height) {
        Image tmp = originalImage.getScaledInstance(width, height, Image.SCALE_SMOOTH);
        BufferedImage resizedImage = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2d = resizedImage.createGraphics();
        g2d.drawImage(tmp, 0, 0, null);
        g2d.dispose();
        return resizedImage;
    }

    public void deleteBookCovers(Set<Long> bookIds) {
        for (Long bookId : bookIds) {
            String bookCoverFolder = fileService.getThumbnailPath(bookId);
            Path folderPath = Paths.get(bookCoverFolder);
            try {
                if (Files.exists(folderPath) && Files.isDirectory(folderPath)) {
                    try (Stream<Path> walk = Files.walk(folderPath)) {
                        walk.sorted(Comparator.reverseOrder())
                                .forEach(path -> {
                                    try {
                                        Files.delete(path);
                                    } catch (IOException e) {
                                        log.error("Failed to delete file: {} - {}", path, e.getMessage());
                                    }
                                });
                    }
                }
            } catch (IOException e) {
                log.error("Error processing folder: {} - {}", folderPath, e.getMessage());
            }
        }
        log.info("Deleted {} book covers", bookIds.size());
    }

    @Transactional
    public Optional<Book> checkForDuplicateAndUpdateMetadataIfNeeded(LibraryFile libraryFile, String hash, BookRepository bookRepository, BookAdditionalFileRepository bookAdditionalFileRepository, BookMapper bookMapper) {
        if (StringUtils.isBlank(hash)) {
            log.warn("Skipping file due to missing hash: {}", libraryFile.getFullPath());
            return Optional.empty();
        }
        Optional<BookEntity> existingByHash = bookRepository.findByCurrentHash(hash);
        if (existingByHash.isPresent()) {
            BookEntity book = existingByHash.get();
            String fileName = libraryFile.getFullPath().getFileName().toString();
            if (!book.getFileName().equals(fileName)) {
                book.setFileName(fileName);
            }
            if (!Objects.equals(book.getLibraryPath().getId(), libraryFile.getLibraryPathEntity().getId())) {
                book.setLibraryPath(libraryFile.getLibraryPathEntity());
                book.setFileSubPath(libraryFile.getFileSubPath());
            }
            return Optional.of(bookMapper.toBook(book));
        }
        Optional<BookAdditionalFileEntity> existingAdditionalFile = bookAdditionalFileRepository.findByAltFormatCurrentHash(hash);
        if (existingAdditionalFile.isPresent()) {
            BookAdditionalFileEntity additionalFile = existingAdditionalFile.get();
            BookEntity book = additionalFile.getBook();

            // Additional file might have a different name or path, so there is no need
            // to update the file name or library path here
            return Optional.of(bookMapper.toBook(book));
        }

        return Optional.empty();
    }

    public static String truncate(String input, int maxLength) {
        return input == null ? null : (input.length() <= maxLength ? input : input.substring(0, maxLength));
    }
}
