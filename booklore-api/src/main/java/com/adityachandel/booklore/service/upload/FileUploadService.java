package com.adityachandel.booklore.service.upload;

import com.adityachandel.booklore.config.AppProperties;
import com.adityachandel.booklore.exception.ApiError;
import com.adityachandel.booklore.mapper.AdditionalFileMapper;
import com.adityachandel.booklore.model.dto.AdditionalFile;
import com.adityachandel.booklore.model.dto.Book;
import com.adityachandel.booklore.model.dto.BookMetadata;
import com.adityachandel.booklore.model.entity.BookAdditionalFileEntity;
import com.adityachandel.booklore.model.entity.BookEntity;
import com.adityachandel.booklore.model.entity.LibraryEntity;
import com.adityachandel.booklore.model.entity.LibraryPathEntity;
import com.adityachandel.booklore.model.enums.AdditionalFileType;
import com.adityachandel.booklore.model.enums.BookFileExtension;
import com.adityachandel.booklore.repository.BookAdditionalFileRepository;
import com.adityachandel.booklore.repository.BookRepository;
import com.adityachandel.booklore.repository.LibraryRepository;
import com.adityachandel.booklore.service.file.FileFingerprint;
import com.adityachandel.booklore.service.appsettings.AppSettingService;
import com.adityachandel.booklore.service.file.FileMovingHelper;
import com.adityachandel.booklore.service.metadata.extractor.MetadataExtractorFactory;
import com.adityachandel.booklore.util.PathPatternResolver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.Optional;

@RequiredArgsConstructor
@Service
@Slf4j
public class FileUploadService {

    private static final String UPLOAD_TEMP_PREFIX = "upload-";
    private static final String BOOKDROP_TEMP_PREFIX = "bookdrop-";
    private static final long BYTES_TO_KB_DIVISOR = 1024L;
    private static final long MB_TO_BYTES_MULTIPLIER = 1024L * 1024L;

    private final LibraryRepository libraryRepository;
    private final BookRepository bookRepository;
    private final BookAdditionalFileRepository additionalFileRepository;
    private final AppSettingService appSettingService;
    private final AppProperties appProperties;
    private final MetadataExtractorFactory metadataExtractorFactory;
    private final AdditionalFileMapper additionalFileMapper;
    private final FileMovingHelper fileMovingHelper;

    public void uploadFile(MultipartFile file, long libraryId, long pathId) {
        validateFile(file);

        final LibraryEntity libraryEntity = findLibraryById(libraryId);
        final LibraryPathEntity libraryPathEntity = findLibraryPathById(libraryEntity, pathId);
        final String originalFileName = getValidatedFileName(file);

        Path tempPath = null;
        try {
            tempPath = createTempFile(UPLOAD_TEMP_PREFIX, originalFileName);
            file.transferTo(tempPath);

            final BookFileExtension fileExtension = getFileExtension(originalFileName);
            final BookMetadata metadata = extractMetadata(fileExtension, tempPath.toFile());
            final String uploadPattern = fileMovingHelper.getFileNamingPattern(libraryEntity);

            final String relativePath = PathPatternResolver.resolvePattern(metadata, uploadPattern, originalFileName);
            final Path finalPath = Paths.get(libraryPathEntity.getPath(), relativePath);

            validateFinalPath(finalPath);
            moveFileToFinalLocation(tempPath, finalPath);

            log.info("File uploaded to final location: {}", finalPath);

        } catch (IOException e) {
            log.error("Failed to upload file: {}", originalFileName, e);
            throw ApiError.FILE_READ_ERROR.createException(e.getMessage());
        } finally {
            cleanupTempFile(tempPath);
        }
    }

    @Transactional
    public AdditionalFile uploadAdditionalFile(Long bookId, MultipartFile file, AdditionalFileType additionalFileType, String description) throws IOException {
        final BookEntity book = findBookById(bookId);
        final String originalFileName = getValidatedFileName(file);

        Path tempPath = null;
        try {
            tempPath = createTempFile(UPLOAD_TEMP_PREFIX, originalFileName);
            file.transferTo(tempPath);

            final String fileHash = FileFingerprint.generateHash(tempPath);
            validateAlternativeFormatDuplicate(additionalFileType, fileHash);

            final Path finalPath = buildAdditionalFilePath(book, originalFileName);
            validateFinalPath(finalPath);
            moveFileToFinalLocation(tempPath, finalPath);

            log.info("Additional file uploaded to final location: {}", finalPath);

            final BookAdditionalFileEntity entity = createAdditionalFileEntity(book, originalFileName, additionalFileType, file.getSize(), fileHash, description);
            final BookAdditionalFileEntity savedEntity = additionalFileRepository.save(entity);

            return additionalFileMapper.toAdditionalFile(savedEntity);

        } catch (IOException e) {
            log.error("Failed to upload additional file for book {}: {}", bookId, originalFileName, e);
            throw ApiError.FILE_READ_ERROR.createException(e.getMessage());
        } finally {
            cleanupTempFile(tempPath);
        }
    }

    public Book uploadFileBookDrop(MultipartFile file) throws IOException {
        validateFile(file);

        final Path dropFolder = Paths.get(appProperties.getBookdropFolder());
        Files.createDirectories(dropFolder);

        final String originalFilename = getValidatedFileName(file);
        Path tempPath = null;

        try {
            tempPath = createTempFile(BOOKDROP_TEMP_PREFIX, originalFilename);
            file.transferTo(tempPath);

            final Path finalPath = dropFolder.resolve(originalFilename);
            validateFinalPath(finalPath);
            Files.move(tempPath, finalPath);

            log.info("File moved to book-drop folder: {}", finalPath);
            return null;

        } finally {
            cleanupTempFile(tempPath);
        }
    }

    private LibraryEntity findLibraryById(long libraryId) {
        return libraryRepository.findById(libraryId)
                .orElseThrow(() -> ApiError.LIBRARY_NOT_FOUND.createException(libraryId));
    }

    private LibraryPathEntity findLibraryPathById(LibraryEntity libraryEntity, long pathId) {
        return libraryEntity.getLibraryPaths()
                .stream()
                .filter(p -> p.getId() == pathId)
                .findFirst()
                .orElseThrow(() -> ApiError.INVALID_LIBRARY_PATH.createException(libraryEntity.getId()));
    }

    private BookEntity findBookById(Long bookId) {
        return bookRepository.findById(bookId)
                .orElseThrow(() -> new IllegalArgumentException("Book not found with id: " + bookId));
    }

    private String getValidatedFileName(MultipartFile file) {
        final String originalFileName = file.getOriginalFilename();
        if (originalFileName == null) {
            throw new IllegalArgumentException("File must have a name");
        }
        return originalFileName;
    }

    private BookFileExtension getFileExtension(String fileName) {
        return BookFileExtension.fromFileName(fileName)
                .orElseThrow(() -> ApiError.INVALID_FILE_FORMAT.createException("Unsupported file extension"));
    }

    private Path createTempFile(String prefix, String fileName) throws IOException {
        return Files.createTempFile(prefix, fileName);
    }

    private void validateFinalPath(Path finalPath) {
        if (Files.exists(finalPath)) {
            throw ApiError.FILE_ALREADY_EXISTS.createException();
        }
    }

    private void moveFileToFinalLocation(Path sourcePath, Path targetPath) throws IOException {
        Files.createDirectories(targetPath.getParent());
        Files.move(sourcePath, targetPath);
    }

    private void validateAlternativeFormatDuplicate(AdditionalFileType additionalFileType, String fileHash) {
        if (additionalFileType == AdditionalFileType.ALTERNATIVE_FORMAT) {
            final Optional<BookAdditionalFileEntity> existingAltFormat = additionalFileRepository.findByAltFormatCurrentHash(fileHash);
            if (existingAltFormat.isPresent()) {
                throw new IllegalArgumentException("Alternative format file already exists with same content");
            }
        }
    }

    private Path buildAdditionalFilePath(BookEntity book, String fileName) {
        return Paths.get(book.getLibraryPath().getPath(), book.getFileSubPath(), fileName);
    }

    private BookAdditionalFileEntity createAdditionalFileEntity(BookEntity book, String fileName, AdditionalFileType additionalFileType, long fileSize, String fileHash, String description) {
        return BookAdditionalFileEntity.builder()
                .book(book)
                .fileName(fileName)
                .fileSubPath(book.getFileSubPath())
                .additionalFileType(additionalFileType)
                .fileSizeKb(fileSize / BYTES_TO_KB_DIVISOR)
                .initialHash(fileHash)
                .currentHash(fileHash)
                .description(description)
                .addedOn(Instant.now())
                .build();
    }

    private void cleanupTempFile(Path tempPath) {
        if (tempPath != null) {
            try {
                Files.deleteIfExists(tempPath);
            } catch (IOException e) {
                log.warn("Failed to cleanup temp file: {}", tempPath, e);
            }
        }
    }

    private BookMetadata extractMetadata(BookFileExtension fileExt, File file) throws IOException {
        return metadataExtractorFactory.extractMetadata(fileExt, file);
    }

    private void validateFile(MultipartFile file) {
        final String originalFilename = file.getOriginalFilename();
        if (originalFilename == null || BookFileExtension.fromFileName(originalFilename).isEmpty()) {
            throw ApiError.INVALID_FILE_FORMAT.createException("Unsupported file extension");
        }

        final int maxSizeMb = appSettingService.getAppSettings().getMaxFileUploadSizeInMb();
        if (file.getSize() > maxSizeMb * MB_TO_BYTES_MULTIPLIER) {
            throw ApiError.FILE_TOO_LARGE.createException(maxSizeMb);
        }
    }
}
