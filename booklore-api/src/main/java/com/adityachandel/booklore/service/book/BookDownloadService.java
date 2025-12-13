package com.adityachandel.booklore.service.book;

import com.adityachandel.booklore.config.security.service.AuthenticationService;
import com.adityachandel.booklore.exception.ApiError;
import com.adityachandel.booklore.model.dto.settings.KoboSettings;
import com.adityachandel.booklore.model.entity.BookEntity;
import com.adityachandel.booklore.model.enums.BookFileType;
import com.adityachandel.booklore.repository.BookMetadataRepository;
import com.adityachandel.booklore.repository.BookRepository;
import com.adityachandel.booklore.repository.LibraryRepository;
import com.adityachandel.booklore.service.ShelfService;
import com.adityachandel.booklore.service.appsettings.AppSettingService;
import com.adityachandel.booklore.service.kobo.KepubConversionService;
import com.adityachandel.booklore.service.kobo.CbxConversionService;
import com.adityachandel.booklore.util.FileUtils;
import jakarta.servlet.http.HttpServletResponse;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.FileSystemUtils;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.regex.Pattern;
import com.adityachandel.booklore.mapper.BookMapper;
import com.adityachandel.booklore.model.dto.BookMetadata;
import com.adityachandel.booklore.model.enums.ShelfType;

@Slf4j
@AllArgsConstructor
@Service
public class BookDownloadService {

    private static final Pattern NON_ASCII_PATTERN = Pattern.compile("[^\\x00-\\x7F]");

    private final BookRepository bookRepository;
    private final KepubConversionService kepubConversionService;
    private final CbxConversionService cbxConversionService;
    private final AppSettingService appSettingService;
    private final ShelfService shelfService;
    private final AuthenticationService authenticationService;
    private final LibraryRepository libraryRepository;
    private final BookMetadataRepository bookMetadataRepository;
    private final BookImportService bookImportService;
    private final BookMapper bookMapper;

    public ResponseEntity<Resource> downloadBook(Long bookId) {
        try {
            BookEntity bookEntity = bookRepository.findById(bookId)
                    .orElseThrow(() -> ApiError.BOOK_NOT_FOUND.createException(bookId));

            Path file = Paths.get(FileUtils.getBookFullPath(bookEntity)).toAbsolutePath().normalize();
            File bookFile = file.toFile();

            if (!bookFile.exists()) {
                throw ApiError.FAILED_TO_DOWNLOAD_FILE.createException(bookId);
            }

            InputStream inputStream = new FileInputStream(bookFile);
            InputStreamResource resource = new InputStreamResource(inputStream);

            String encodedFilename = URLEncoder.encode(file.getFileName().toString(), StandardCharsets.UTF_8)
                    .replace("+", "%20");
            String fallbackFilename = NON_ASCII_PATTERN.matcher(file.getFileName().toString()).replaceAll("_");
            String contentDisposition = String.format("attachment; filename=\"%s\"; filename*=UTF-8''%s",
                    fallbackFilename, encodedFilename);
            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_OCTET_STREAM)
                    .contentLength(bookFile.length())
                    .header(HttpHeaders.CONTENT_DISPOSITION, contentDisposition)
                    .header(HttpHeaders.CACHE_CONTROL, "no-cache, no-store, must-revalidate")
                    .header(HttpHeaders.PRAGMA, "no-cache")
                    .header(HttpHeaders.EXPIRES, "0")
                    .body(resource);
        } catch (Exception e) {
            log.error("Failed to download book {}: {}", bookId, e.getMessage(), e);
            throw ApiError.FAILED_TO_DOWNLOAD_FILE.createException(bookId);
        }
    }

    public void downloadKoboBook(Long bookId, HttpServletResponse response) {
        BookEntity bookEntity = bookRepository.findById(bookId).orElseThrow(() -> ApiError.BOOK_NOT_FOUND.createException(bookId));

        boolean isEpub = bookEntity.getBookType() == BookFileType.EPUB;
        boolean isCbx = bookEntity.getBookType() == BookFileType.CBX;

        if (!isEpub && !isCbx) {
            throw ApiError.GENERIC_BAD_REQUEST.createException("The requested book is not an EPUB or CBX file.");
        }

        KoboSettings koboSettings = appSettingService.getAppSettings().getKoboSettings();
        if (koboSettings == null) {
            throw ApiError.GENERIC_BAD_REQUEST.createException("Kobo settings not found.");
        }

        boolean convertEpubToKepub = isEpub && koboSettings.isConvertToKepub() && bookEntity.getFileSizeKb() <= (long) koboSettings.getConversionLimitInMb() * 1024;
        boolean convertCbxToEpub = isCbx && koboSettings.isConvertCbxToEpub() && bookEntity.getFileSizeKb() <= (long) koboSettings.getConversionLimitInMbForCbx() * 1024;
        boolean persistConversion = koboSettings.isPersistConversion();

        Path tempDir = null;
        try {
            File inputFile = new File(FileUtils.getBookFullPath(bookEntity));
            File fileToSend = inputFile;
            boolean convertedExists = false;

            if(persistConversion) {
                try {
                    Path libraryDir = Paths.get(bookEntity.getLibraryPath().getPath(), bookEntity.getFileSubPath() == null ? "" : bookEntity.getFileSubPath());

                    if (convertCbxToEpub) {
                        Path maybeConverted = libraryDir.resolve(inputFile.getName() + ".epub");
                        if (Files.exists(maybeConverted)) {
                            log.debug("Found existing converted EPUB for book {} at {}", bookId, maybeConverted);
                            fileToSend = maybeConverted.toFile();
                            convertedExists = true;
                        }
                    }

                    if (convertEpubToKepub) {
                        Path maybeKepub1 = libraryDir.resolve(inputFile.getName() + ".kepub.epub");
                        Path maybeKepub2 = null;
                        String name = inputFile.getName();
                        if (name.toLowerCase().endsWith(".epub")) {
                            String base = name.substring(0, name.length() - 5);
                            maybeKepub2 = libraryDir.resolve(base + ".kepub.epub");
                        }
                        if (Files.exists(maybeKepub1)) {
                            log.debug("Found existing kepubified file for book {} at {}", bookId, maybeKepub1);
                            fileToSend = maybeKepub1.toFile();
                            convertedExists = true;

                        } else if (maybeKepub2 != null && Files.exists(maybeKepub2)) {
                            log.debug("Found existing kepubified file for book {} at {}", bookId, maybeKepub2);
                            fileToSend = maybeKepub2.toFile();
                            convertedExists = true;

                        }
                    }
                } catch (Exception e) {
                    log.warn("Error while checking for existing converted files for book {}: {}", bookId, e.getMessage());
                }
            }
            if (convertCbxToEpub || convertEpubToKepub) {
                tempDir = Files.createTempDirectory("kobo-conversion");
            }

            if (convertCbxToEpub && !convertedExists) {
                fileToSend = cbxConversionService.convertCbxToEpub(inputFile, tempDir.toFile(), bookEntity);
            }

            if (convertEpubToKepub && !convertedExists) {
                fileToSend = kepubConversionService.convertEpubToKepub(inputFile, tempDir.toFile(),
                    koboSettings.isForceEnableHyphenation());
            }

            if (!fileToSend.equals(inputFile) && !convertedExists && persistConversion) {
                Path destDir = Paths.get(bookEntity.getLibraryPath().getPath(),
                        bookEntity.getFileSubPath() == null ? "" : bookEntity.getFileSubPath());
                Files.createDirectories(destDir);

                String destFileName = fileToSend.getName();
                Path destPath = destDir.resolve(destFileName);

                try {
                    Files.move(fileToSend.toPath(), destPath, StandardCopyOption.ATOMIC_MOVE);
                } catch (IOException moveEx) {
                    try {
                        Files.copy(fileToSend.toPath(), destPath, StandardCopyOption.REPLACE_EXISTING);
                        Files.deleteIfExists(fileToSend.toPath());
                    } catch (IOException copyEx) {
                        log.error("Failed to move converted file into library directory: {}", copyEx.getMessage(), copyEx);
                        throw ApiError.FAILED_TO_DOWNLOAD_FILE.createException(bookId);
                    }
                }

                try {
                    BookMetadata metadataDto = bookMapper.toBook(bookEntity).getMetadata();
                    Long conversionShelfId = null;
                    try {
                        var authUser = authenticationService.getAuthenticatedUser();
                        if (authUser != null) {
                            var shelfOpt = shelfService.getShelf(authUser.getId(), ShelfType.CONVERSION.getName());
                            if (shelfOpt.isPresent()) conversionShelfId = shelfOpt.get().getId();
                        }
                    } catch (Exception e) {
                        log.debug("Could not resolve conversion shelf: {}", e.getMessage());
                    }
                    var imported = bookImportService.importFileToLibrary(destPath.toFile(),
                            bookEntity.getLibrary().getId(),
                            bookEntity.getLibraryPath().getId(),
                            metadataDto,
                            conversionShelfId);
                    log.debug("Imported converted file as book id={}", imported.getId());
                } catch (Exception e) {
                    log.warn("Failed to import converted file into library: {}", e.getMessage(), e);
                }

                fileToSend = destPath.toFile();
            }

            setResponseHeaders(response, fileToSend);
            streamFileToResponse(fileToSend, response);

            log.info("Successfully streamed {} ({} bytes) to client", fileToSend.getName(), fileToSend.length());

        } catch (Exception e) {
            log.error("Failed to download kobo book {}: {}", bookId, e.getMessage(), e);
            throw ApiError.FAILED_TO_DOWNLOAD_FILE.createException(bookId);
        } finally {
            cleanupTempDirectory(tempDir);
        }
    }

    private Path resolveWithUniqueName(Path dir, String baseName) {
        String name = baseName;
        String ext = "";
        int lastDot = baseName.lastIndexOf('.');
        if (lastDot > 0) {
            name = baseName.substring(0, lastDot);
            ext = baseName.substring(lastDot);
        }

        int counter = 1;
        Path candidate = dir.resolve(baseName);
        while (Files.exists(candidate)) {
            String candidateName = String.format("%s(%d)%s", name, counter++, ext);
            candidate = dir.resolve(candidateName);
        }
        return candidate;
    }

    private void setResponseHeaders(HttpServletResponse response, File file) {
        response.setContentType(MediaType.APPLICATION_OCTET_STREAM_VALUE);
        response.setContentLengthLong(file.length());
        String encodedFilename = URLEncoder.encode(file.getName(), StandardCharsets.UTF_8).replace("+", "%20");
        String fallbackFilename = NON_ASCII_PATTERN.matcher(file.getName()).replaceAll("_");
        String contentDisposition = String.format("attachment; filename=\"%s\"; filename*=UTF-8''%s",
                fallbackFilename, encodedFilename);
        response.setHeader(HttpHeaders.CONTENT_DISPOSITION, contentDisposition);
    }

    private void streamFileToResponse(File file, HttpServletResponse response) {
        try (InputStream in = Files.newInputStream(file.toPath())) {
            in.transferTo(response.getOutputStream());
            response.getOutputStream().flush();
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to stream file to response", e);
        }
    }

    private void cleanupTempDirectory(Path tempDir) {
        if (tempDir != null) {
            try {
                FileSystemUtils.deleteRecursively(tempDir);
                log.debug("Deleted temporary directory {}", tempDir);
            } catch (Exception e) {
                log.warn("Failed to delete temporary directory {}: {}", tempDir, e.getMessage());
            }
        }
    }
}
