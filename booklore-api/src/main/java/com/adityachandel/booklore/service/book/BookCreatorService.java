package com.adityachandel.booklore.service.book;

import com.adityachandel.booklore.model.dto.settings.LibraryFile;
import com.adityachandel.booklore.model.entity.*;
import com.adityachandel.booklore.model.enums.BookFileType;
import com.adityachandel.booklore.repository.*;
import com.adityachandel.booklore.service.file.FileFingerprint;
import com.adityachandel.booklore.util.FileUtils;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;

@Slf4j
@Service
@AllArgsConstructor
public class BookCreatorService {

    private final AuthorRepository authorRepository;
    private final CategoryRepository categoryRepository;
    private final MoodRepository moodRepository;
    private final TagRepository tagRepository;
    private final BookRepository bookRepository;
    private final BookMetadataRepository bookMetadataRepository;

    public BookEntity createShellBook(LibraryFile libraryFile, BookFileType bookFileType) {
        Optional<BookEntity> existingBookOpt = bookRepository.findByLibraryIdAndLibraryPathIdAndFileSubPathAndFileName(
                libraryFile.getLibraryEntity().getId(),
                libraryFile.getLibraryPathEntity().getId(),
                libraryFile.getFileSubPath(),
                libraryFile.getFileName());

        if (existingBookOpt.isPresent()) {
            log.warn("Book already exists for file: {}", libraryFile.getFileName());
            long fileSizeKb = calculateFileSize(libraryFile);
            String newHash = libraryFile.isFolderBased()
                    ? FileFingerprint.generateFolderHash(libraryFile.getFullPath())
                    : FileFingerprint.generateHash(libraryFile.getFullPath());
            BookEntity existingBook = existingBookOpt.get();
            BookFileEntity primaryFile = existingBook.getPrimaryBookFile();
            primaryFile.setCurrentHash(newHash);
            primaryFile.setInitialHash(newHash);
            primaryFile.setFileSizeKb(fileSizeKb);
            primaryFile.setFolderBased(libraryFile.isFolderBased());
            existingBook.setDeleted(false);
            return existingBook;
        }

        long fileSizeKb = calculateFileSize(libraryFile);
        String hash = libraryFile.isFolderBased()
                ? FileFingerprint.generateFolderHash(libraryFile.getFullPath())
                : FileFingerprint.generateHash(libraryFile.getFullPath());

        BookEntity bookEntity = BookEntity.builder()
                .library(libraryFile.getLibraryEntity())
                .libraryPath(libraryFile.getLibraryPathEntity())
                .addedOn(Instant.now())
                .bookFiles(new ArrayList<>())
                .build();

        BookFileEntity bookFileEntity = BookFileEntity.builder()
                .book(bookEntity)
                .fileName(libraryFile.getFileName())
                .fileSubPath(libraryFile.getFileSubPath())
                .isBookFormat(true)
                .folderBased(libraryFile.isFolderBased())
                .bookType(bookFileType)
                .fileSizeKb(fileSizeKb)
                .initialHash(hash)
                .currentHash(hash)
                .addedOn(Instant.now())
                .build();
        bookEntity.getBookFiles().add(bookFileEntity);

        BookMetadataEntity metadata = BookMetadataEntity.builder()
                .book(bookEntity)
                .build();
        bookEntity.setMetadata(metadata);

        return bookRepository.saveAndFlush(bookEntity);
    }

    private long calculateFileSize(LibraryFile libraryFile) {
        if (libraryFile.isFolderBased()) {
            Long size = FileUtils.getFolderSizeInKb(libraryFile.getFullPath());
            return size != null ? size : 0L;
        } else {
            Long size = FileUtils.getFileSizeInKb(libraryFile.getFullPath());
            return size != null ? size : 0L;
        }
    }

    public void addCategoriesToBook(Set<String> categories, BookEntity bookEntity) {
        if (bookEntity.getMetadata().getCategories() == null) {
            bookEntity.getMetadata().setCategories(new HashSet<>());
        }
        categories.stream()
                .map(cat -> truncate(cat, 255))
                .map(truncated -> categoryRepository.findByName(truncated)
                        .orElseGet(() -> categoryRepository.save(CategoryEntity.builder().name(truncated).build())))
                .forEach(catEntity -> bookEntity.getMetadata().getCategories().add(catEntity));
    }

    public void addAuthorsToBook(Set<String> authors, BookEntity bookEntity) {
        if (bookEntity.getMetadata().getAuthors() == null) {
            bookEntity.getMetadata().setAuthors(new HashSet<>());
        }
        authors.stream()
                .map(authorName -> truncate(authorName, 255))
                .map(authorName -> authorRepository.findByName(authorName)
                        .orElseGet(() -> authorRepository.save(AuthorEntity.builder().name(authorName).build())))
                .forEach(authorEntity -> bookEntity.getMetadata().getAuthors().add(authorEntity));
        bookEntity.getMetadata().updateSearchText(); // Manually trigger search text update since collection modification doesn't trigger @PreUpdate
    }

    public void addMoodsToBook(Set<String> moods, BookEntity bookEntity) {
        if (bookEntity.getMetadata().getMoods() == null) {
            bookEntity.getMetadata().setMoods(new HashSet<>());
        }
        moods.stream()
                .map(mood -> truncate(mood, 255))
                .map(truncated -> moodRepository.findByName(truncated)
                        .orElseGet(() -> moodRepository.save(MoodEntity.builder().name(truncated).build())))
                .forEach(moodEntity -> bookEntity.getMetadata().getMoods().add(moodEntity));
    }

    public void addTagsToBook(Set<String> tags, BookEntity bookEntity) {
        if (bookEntity.getMetadata().getTags() == null) {
            bookEntity.getMetadata().setTags(new HashSet<>());
        }
        tags.stream()
                .map(tag -> truncate(tag, 255))
                .map(truncated -> tagRepository.findByName(truncated)
                        .orElseGet(() -> tagRepository.save(TagEntity.builder().name(truncated).build())))
                .forEach(tagEntity -> bookEntity.getMetadata().getTags().add(tagEntity));
    }

    private String truncate(String input, int maxLength) {
        if (input == null)
            return null;
        return input.length() <= maxLength ? input : input.substring(0, maxLength);
    }

    public void saveConnections(BookEntity bookEntity) {
        if (bookEntity.getMetadata().getAuthors() != null && !bookEntity.getMetadata().getAuthors().isEmpty()) {
            authorRepository.saveAll(bookEntity.getMetadata().getAuthors());
        }
        bookRepository.save(bookEntity);
        bookMetadataRepository.save(bookEntity.getMetadata());
    }
}
