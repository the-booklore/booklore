package org.booklore.service.file;

import org.booklore.exception.ApiError;
import org.booklore.model.dto.request.FileHashVerificationOptions;
import org.booklore.model.dto.request.FileHashVerificationRequest;
import org.booklore.model.entity.BookEntity;
import org.booklore.model.entity.BookFileEntity;
import org.booklore.model.entity.LibraryEntity;
import org.booklore.repository.BookRepository;
import org.booklore.repository.LibraryRepository;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

@Slf4j
@AllArgsConstructor
@Service
public class FileHashVerificationService {

    private final LibraryRepository libraryRepository;
    private final BookRepository bookRepository;
    private final PlatformTransactionManager transactionManager;

    @Getter
    @Builder
    public static class VerificationSummary {
        private final int totalBooks;
        private final int mismatchCount;
        private final int errorCount;
        private final boolean isDryRun;
    }

    public VerificationSummary verifyFileHashes(FileHashVerificationRequest request, String taskId) {
        log.info("Starting file hash verification task: {}", taskId);

        if (request == null) {
            log.error("FileHashVerificationRequest is null for task {}", taskId);
            throw new IllegalArgumentException("FileHashVerificationRequest cannot be null");
        }

        try {
            final Set<Long> bookIds = getBookEntities(request);
            final int totalBooks = bookIds.size();
            final FileHashVerificationOptions options = request.getVerificationOptions() != null 
                    ? request.getVerificationOptions() 
                    : FileHashVerificationOptions.builder().build();

            log.info("Verifying hashes for {} books. Dry-run: {}, Overwrite initial: {}", 
                    totalBooks, options.isDryRun(), options.isOverwriteInitialHash());

            TransactionTemplate txTemplate = new TransactionTemplate(transactionManager);
            int completedCount = 0;
            int mismatchCount = 0;
            int errorCount = 0;
            List<String> mismatchMessages = new ArrayList<>();

            for (Long bookId : bookIds) {
                log.debug("Processing book ID: {}", bookId);

                try {
                    Integer mismatchedFiles = txTemplate.execute(status -> {
                        BookEntity book = bookRepository.findAllWithMetadataByIds(Collections.singleton(bookId))
                                .stream().findFirst()
                                .orElseThrow(() -> ApiError.BOOK_NOT_FOUND.createException(bookId));

                        int mismatches = 0;
                        
                        // Check all book files (primary + additional)
                        for (BookFileEntity bookFile : book.getBookFiles()) {
                            String result = verifyFileHash(book, bookFile, options);
                            if (result != null) {
                                mismatchMessages.add(result);
                                mismatches++;
                            }
                        }

                        if (!options.isDryRun() && mismatches > 0) {
                            bookRepository.save(book);
                        }

                        return mismatches;
                    });

                    if (mismatchedFiles != null && mismatchedFiles > 0) {
                        mismatchCount += mismatchedFiles;
                    }

                } catch (Exception e) {
                    log.error("Error verifying hashes for book ID {}: {}", bookId, e.getMessage(), e);
                    errorCount++;
                }

                completedCount++;
            }

            log.info("File hash verification completed. Total: {}, Mismatches: {}, Errors: {}", 
                    totalBooks, mismatchCount, errorCount);
            
            if (!mismatchMessages.isEmpty()) {
                log.info("Mismatched files:");
                mismatchMessages.forEach(log::info);
            }

            return VerificationSummary.builder()
                    .totalBooks(totalBooks)
                    .mismatchCount(mismatchCount)
                    .errorCount(errorCount)
                    .isDryRun(options.isDryRun())
                    .build();

        } catch (RuntimeException e) {
            log.error("Fatal error during file hash verification", e);
            throw e;
        }
    }

    /**
     * Verifies the hash of a single book file.
     * 
     * @param book The book entity
     * @param bookFile The book file to verify
     * @param options Verification options
     * @return A message describing the mismatch, or null if hashes match
     */
    private String verifyFileHash(BookEntity book, BookFileEntity bookFile, FileHashVerificationOptions options) {
        Path filePath = bookFile.getFullFilePath();
        
        if (filePath == null || !Files.exists(filePath)) {
            log.warn("File not found for book {} (file ID: {}): {}", 
                    book.getId(), bookFile.getId(), filePath);
            return null;
        }

        try {
            String calculatedHash;
            if (bookFile.isFolderBased()) {
                calculatedHash = FileFingerprint.generateFolderHash(filePath);
            } else {
                calculatedHash = FileFingerprint.generateHash(filePath);
            }

            String currentHash = bookFile.getCurrentHash();
            
            if (!calculatedHash.equals(currentHash)) {
                String fileName = bookFile.getFileName();
                String message = String.format(
                        "Hash mismatch detected - Book ID: %d, File: %s, Stored: %s, Calculated: %s",
                        book.getId(), fileName, currentHash, calculatedHash);
                
                log.info(message);

                if (!options.isDryRun()) {
                    // Move current hash to initial hash if needed
                    if (options.isOverwriteInitialHash() || bookFile.getInitialHash() == null) {
                        bookFile.setInitialHash(currentHash);
                    }
                    // Update current hash with the newly calculated hash
                    bookFile.setCurrentHash(calculatedHash);
                    log.info("Updated hashes for book {} file {}: initial={}, current={}", 
                            book.getId(), fileName, bookFile.getInitialHash(), calculatedHash);
                } else {
                    log.info("Dry-run mode - no changes made");
                }

                return message;
            }

        } catch (Exception e) {
            log.error("Error calculating hash for file {}: {}", filePath, e.getMessage(), e);
        }

        return null;
    }

    /**
     * Retrieves book IDs based on the verification request type.
     */
    protected Set<Long> getBookEntities(FileHashVerificationRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("FileHashVerificationRequest cannot be null");
        }
        
        FileHashVerificationRequest.VerificationType verificationType = request.getVerificationType();
        
        if (verificationType == null) {
            throw new IllegalArgumentException("Verification type cannot be null");
        }
        
        if (verificationType != FileHashVerificationRequest.VerificationType.LIBRARY && 
            verificationType != FileHashVerificationRequest.VerificationType.BOOKS) {
            throw ApiError.INVALID_VERIFICATION_TYPE.createException();
        }
        
        return switch (verificationType) {
            case LIBRARY -> {
                if (request.getLibraryId() == null) {
                    // Null libraryId means scan all libraries
                    log.info("No library specified, scanning all libraries");
                    yield libraryRepository.findAll().stream()
                            .flatMap(lib -> bookRepository.findBookIdsByLibraryId(lib.getId()).stream())
                            .collect(java.util.stream.Collectors.toSet());
                }
                LibraryEntity libraryEntity = libraryRepository.findById(request.getLibraryId())
                        .orElseThrow(() -> ApiError.LIBRARY_NOT_FOUND.createException(request.getLibraryId()));
                yield bookRepository.findBookIdsByLibraryId(libraryEntity.getId());
            }
            case BOOKS -> {
                if (request.getBookIds() == null || request.getBookIds().isEmpty()) {
                    throw new IllegalArgumentException("Book IDs cannot be null or empty for BOOKS verification type");
                }
                yield request.getBookIds();
            }
        };
    }
}
