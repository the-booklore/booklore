package org.booklore.service.watcher;

import org.booklore.exception.ApiError;
import org.booklore.mapper.BookMapper;
import org.booklore.model.entity.BookEntity;
import org.booklore.model.entity.BookFileEntity;
import org.booklore.model.entity.LibraryEntity;
import org.booklore.model.entity.LibraryPathEntity;
import org.booklore.model.websocket.Topic;
import org.booklore.repository.BookFileRepository;
import org.booklore.repository.BookRepository;
import org.booklore.service.NotificationService;
import org.booklore.util.FileUtils;
import jakarta.persistence.EntityManager;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.*;

import static org.booklore.model.enums.PermissionType.ADMIN;
import static org.booklore.model.enums.PermissionType.MANAGE_LIBRARY;

@Slf4j
@Service
@AllArgsConstructor
public class BookFilePersistenceService {

    private final EntityManager entityManager;
    private final BookRepository bookRepository;
    private final BookFileRepository bookFileRepository;
    private final NotificationService notificationService;
    private final BookMapper bookMapper;

    @Transactional
    public void updatePathIfChanged(BookEntity book, LibraryEntity libraryEntity, Path path, String currentHash) {
        LibraryPathEntity newLibraryPath = getLibraryPathEntityForFile(libraryEntity, path.toString());
        newLibraryPath = entityManager.merge(newLibraryPath);

        String newSubPath = FileUtils.getRelativeSubPath(newLibraryPath.getPath(), path);

        var primaryFile = book.getPrimaryBookFile();
        boolean pathChanged = !Objects.equals(newSubPath, primaryFile.getFileSubPath()) || !Objects.equals(newLibraryPath.getId(), book.getLibraryPath().getId());

        if (pathChanged || Boolean.TRUE.equals(book.getDeleted())) {
            book.setLibraryPath(newLibraryPath);
            primaryFile.setFileSubPath(newSubPath);
            book.setDeleted(Boolean.FALSE);
            bookRepository.save(book);
            log.info("[FILE_CREATE] Updated path / undeleted existing book with hash '{}': '{}'", currentHash, path);
        } else {
            log.info("[FILE_CREATE] Book with hash '{}' already exists at same path. Skipping update.", currentHash);
        }
        notificationService.sendMessageToPermissions(Topic.BOOK_ADD, bookMapper.toBookWithDescription(book, false), Set.of(ADMIN, MANAGE_LIBRARY));
    }

    String findMatchingLibraryPath(LibraryEntity libraryEntity, Path filePath) {
        return libraryEntity.getLibraryPaths().stream()
                .map(lp -> Paths.get(lp.getPath()).toAbsolutePath().normalize())
                .filter(base -> filePath.toAbsolutePath().normalize().startsWith(base))
                .map(Path::toString)
                .findFirst()
                .orElseThrow(() -> ApiError.LIBRARY_NOT_FOUND.createException("No matching libraryPath for: " + filePath));
    }

    LibraryPathEntity getLibraryPathEntityForFile(LibraryEntity libraryEntity, String inputPath) {
        Path fullPath = Paths.get(inputPath).toAbsolutePath().normalize();
        return libraryEntity.getLibraryPaths().stream()
                .map(lp -> Map.entry(lp, Paths.get(lp.getPath()).toAbsolutePath().normalize()))
                .filter(entry -> fullPath.startsWith(entry.getValue()))
                .max(Comparator.comparingInt(entry -> entry.getValue().getNameCount()))
                .map(Map.Entry::getKey)
                .orElseThrow(() -> ApiError.BOOK_NOT_FOUND.createException(inputPath));
    }

    @Transactional
    public int markAllBooksUnderPathAsDeleted(long libraryPathId, String relativeFolderPath) {
        if (relativeFolderPath == null) {
            throw new IllegalArgumentException("relativeFolderPath cannot be null");
        }
        String normalizedPrefix = relativeFolderPath.endsWith("/") ? relativeFolderPath : (relativeFolderPath + "/");

        List<BookEntity> books = bookRepository.findAllByLibraryPathIdAndFileSubPathStartingWith(libraryPathId, normalizedPrefix);
        books.forEach(book -> {
            book.setDeleted(true);
            book.setDeletedAt(Instant.now());
        });

        bookRepository.saveAll(books);
        return books.size();
    }

    @Transactional(readOnly = true)
    public Optional<BookEntity> findByLibraryPathSubPathAndFileName(long libraryPathId, String fileSubPath, String fileName) {
        return bookRepository.findByLibraryPath_IdAndFileSubPathAndFileName(libraryPathId, fileSubPath, fileName);
    }

    @Transactional(readOnly = true)
    public Optional<BookFileEntity> findBookFileByLibraryPathSubPathAndFileName(long libraryPathId, String fileSubPath, String fileName) {
        return bookFileRepository.findByLibraryPathIdAndFileSubPathAndFileName(libraryPathId, fileSubPath, fileName);
    }

    @Transactional
    public void deleteBookFile(BookFileEntity bookFile) {
        bookFileRepository.delete(bookFile);
    }

    @Transactional
    public void markBookAsDeleted(BookEntity book) {
        book.setDeleted(true);
        book.setDeletedAt(Instant.now());
        bookRepository.save(book);
    }

    @Transactional
    public void save(BookEntity book) {
        bookRepository.save(book);
    }

    @Transactional(readOnly = true)
    public long countBookFilesByBookId(Long bookId) {
        return bookFileRepository.countByBookId(bookId);
    }
}