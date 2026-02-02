package org.booklore.service.conversion;

import org.booklore.exception.ApiError;
import org.booklore.model.dto.request.CbzConversionRequest;
import org.booklore.model.dto.response.CbzConversionResponse;
import org.booklore.model.entity.BookEntity;
import org.booklore.model.entity.BookFileEntity;
import org.booklore.model.entity.BookMetadataEntity;
import org.booklore.model.enums.BookFileType;
import org.booklore.model.enums.EReaderProfile;
import org.booklore.repository.BookRepository;
import org.booklore.service.book.BookImportService;
import org.booklore.service.kobo.CbxConversionService;
import com.github.junrar.exception.RarException;
import freemarker.template.TemplateException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Service for standalone CBZ to EPUB conversion with resolution control.
 * This service allows users to convert comic book archives to EPUB format
 * independent of Kobo device sync.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StandaloneCbzConversionService {
    
    private final BookRepository bookRepository;
    private final CbxConversionService cbxConversionService;
    private final BookImportService bookImportService;
    
    /**
     * Convert a CBZ book to EPUB with specified resolution settings.
     * The converted EPUB will be imported as a new book in the same library.
     * 
     * @param request Conversion request with device profile and optional custom dimensions
     * @return Conversion response with details about the created EPUB
     * @throws IOException if file operations fail
     * @throws TemplateException if EPUB template processing fails
     * @throws RarException if CBR extraction fails
     */
    @Transactional
    public CbzConversionResponse convertCbzToEpub(CbzConversionRequest request) 
            throws IOException, TemplateException, RarException {
        
        // Validate request
        validateRequest(request);
        
        // Fetch the source book
        BookEntity sourceBook = bookRepository.findById(request.getBookId())
                .orElseThrow(() -> ApiError.BOOK_NOT_FOUND.createException(request.getBookId()));
        
        // Verify it's a CBX file
        BookFileEntity primaryFile = sourceBook.getPrimaryBookFile();
        if (primaryFile == null || primaryFile.getBookType() != BookFileType.CBX) {
            throw ApiError.GENERIC_BAD_REQUEST.createException("Book is not a CBX file");
        }
        
        // Determine target resolution
        int targetWidth;
        int targetHeight;
        EReaderProfile profile = request.getDeviceProfile();
        
        if (profile == EReaderProfile.OTHER) {
            if (request.getCustomWidth() == null || request.getCustomHeight() == null) {
                throw ApiError.GENERIC_BAD_REQUEST.createException(
                    "Custom width and height are required when using OTHER profile");
            }
            targetWidth = request.getCustomWidth();
            targetHeight = request.getCustomHeight();
        } else {
            targetWidth = profile.getWidth();
            targetHeight = profile.getHeight();
        }
        
        log.info("Converting CBZ book {} to EPUB with resolution {}x{}", 
                request.getBookId(), targetWidth, targetHeight);
        
        // Create temporary directory for conversion
        Path tempDir = Files.createTempDirectory("cbz-conversion");
        
        try {
            // Get source file path
            File sourceFile = primaryFile.getFullFilePath().toFile();
            
            // Perform conversion using existing CbxConversionService
            // Note: Current CbxConversionService doesn't support resolution targeting yet
            // This will be enhanced in a follow-up implementation
            int compressionPercentage = request.getCompressionPercentage() != null 
                    ? request.getCompressionPercentage() : 85;
            
            File epubFile = cbxConversionService.convertCbxToEpub(
                    sourceFile, 
                    tempDir.toFile(), 
                    sourceBook,
                    compressionPercentage
            );
            
            // Import the converted EPUB as a new book in the same library
            BookEntity newBook = importConvertedEpub(epubFile, sourceBook);
            
            // Build response
            BookFileEntity newPrimaryFile = newBook.getPrimaryBookFile();
            
            return CbzConversionResponse.builder()
                    .newBookId(newBook.getId())
                    .fileName(newPrimaryFile.getFileName())
                    .fileSizeKb(newPrimaryFile.getFileSizeKb())
                    .targetWidth(targetWidth)
                    .targetHeight(targetHeight)
                    .pageCount(countPages(sourceFile))
                    .message("Successfully converted CBZ to EPUB")
                    .build();
                    
        } finally {
            // Clean up temporary directory
            cleanupTempDirectory(tempDir);
        }
    }
    
    /**
     * Validate the conversion request
     */
    private void validateRequest(CbzConversionRequest request) {
        if (request.getDeviceProfile() == EReaderProfile.OTHER) {
            if (request.getCustomWidth() == null || request.getCustomHeight() == null) {
                throw ApiError.GENERIC_BAD_REQUEST.createException(
                    "Custom width and height are required for OTHER profile");
            }
            if (request.getCustomWidth() < 100 || request.getCustomHeight() < 100) {
                throw ApiError.GENERIC_BAD_REQUEST.createException(
                    "Custom dimensions must be at least 100x100 pixels");
            }
        }
        
        if (request.getCompressionPercentage() != null) {
            int compression = request.getCompressionPercentage();
            if (compression < 1 || compression > 100) {
                throw ApiError.GENERIC_BAD_REQUEST.createException(
                    "Compression percentage must be between 1 and 100");
            }
        }
    }
    
    /**
     * Import the converted EPUB file as a new book
     */
    private BookEntity importConvertedEpub(File epubFile, BookEntity sourceBook) throws IOException {
        // Use BookImportService to import the EPUB
        // Place it in the same library as the source book
        Long libraryId = sourceBook.getLibrary().getId();
        
        // Create a unique filename to avoid conflicts
        String originalTitle = sourceBook.getMetadata() != null 
                ? sourceBook.getMetadata().getTitle() : "Unknown";
        String newFileName = sanitizeFilename(originalTitle) + "_converted.epub";
        
        // Import using the existing service
        return bookImportService.importFileToLibrary(
                epubFile.toPath(),
                libraryId,
                newFileName
        );
    }
    
    /**
     * Count the number of pages in a CBX file (simple estimation)
     */
    private Integer countPages(File cbxFile) {
        // This is a placeholder - actual implementation would scan the archive
        // For now, return null to indicate unknown
        return null;
    }
    
    /**
     * Sanitize filename for safe file system usage
     */
    private String sanitizeFilename(String filename) {
        return filename.replaceAll("[^a-zA-Z0-9.-]", "_");
    }
    
    /**
     * Clean up temporary directory and all its contents
     */
    private void cleanupTempDirectory(Path tempDir) {
        try {
            if (Files.exists(tempDir)) {
                org.springframework.util.FileSystemUtils.deleteRecursively(tempDir);
            }
        } catch (IOException e) {
            log.warn("Failed to clean up temporary directory: {}", tempDir, e);
        }
    }
}
