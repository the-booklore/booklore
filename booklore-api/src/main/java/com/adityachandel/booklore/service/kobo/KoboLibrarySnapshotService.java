package com.adityachandel.booklore.service.kobo;

import com.adityachandel.booklore.mapper.BookEntityToKoboSnapshotBookMapper;
import com.adityachandel.booklore.model.entity.KoboDeletedBookProgressEntity;
import com.adityachandel.booklore.model.entity.KoboSnapshotBookEntity;
import com.adityachandel.booklore.model.entity.ShelfEntity;
import com.adityachandel.booklore.model.entity.KoboLibrarySnapshotEntity;
import com.adityachandel.booklore.model.enums.BookFileType;
import com.adityachandel.booklore.model.enums.ShelfType;
import com.adityachandel.booklore.repository.KoboDeletedBookProgressRepository;
import com.adityachandel.booklore.repository.ShelfRepository;
import com.adityachandel.booklore.repository.KoboSnapshotBookRepository;
import com.adityachandel.booklore.repository.KoboLibrarySnapshotRepository;
import lombok.AllArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@AllArgsConstructor
@Service
public class KoboLibrarySnapshotService {

    private final KoboLibrarySnapshotRepository koboLibrarySnapshotRepository;
    private final KoboSnapshotBookRepository koboSnapshotBookRepository;
    private final ShelfRepository shelfRepository;
    private final BookEntityToKoboSnapshotBookMapper mapper;
    private final KoboDeletedBookProgressRepository koboDeletedBookProgressRepository;

    @Transactional(readOnly = true)
    public Optional<KoboLibrarySnapshotEntity> findByIdAndUserId(String id, Long userId) {
        return koboLibrarySnapshotRepository.findByIdAndUserId(id, userId);
    }

    @Transactional
    public KoboLibrarySnapshotEntity create(Long userId) {
        KoboLibrarySnapshotEntity snapshot = KoboLibrarySnapshotEntity.builder()
                .id(UUID.randomUUID().toString())
                .userId(userId)
                .build();

        List<KoboSnapshotBookEntity> books = mapBooksToKoboSnapshotBook(getKoboShelf(userId), snapshot);
        snapshot.setBooks(books);

        return koboLibrarySnapshotRepository.save(snapshot);
    }

    @Transactional
    public Page<KoboSnapshotBookEntity> getUnsyncedBooks(String snapshotId, Pageable pageable) {
        Page<KoboSnapshotBookEntity> page = koboSnapshotBookRepository.findBySnapshot_IdAndSyncedFalse(snapshotId, pageable);
        List<Long> bookIds = page.getContent().stream()
                .map(KoboSnapshotBookEntity::getBookId)
                .toList();
        if (!bookIds.isEmpty()) {
            koboSnapshotBookRepository.markBooksSynced(snapshotId, bookIds);
        }
        return page;
    }

    @Transactional
    public void updateSyncedStatusForExistingBooks(String previousSnapshotId, String currentSnapshotId) {
        List<KoboSnapshotBookEntity> list = koboSnapshotBookRepository.findExistingBooksBetweenSnapshots(previousSnapshotId, currentSnapshotId);
        List<Long> existingBooks = list.stream()
                .map(KoboSnapshotBookEntity::getBookId)
                .toList();

        if (!existingBooks.isEmpty()) {
            koboSnapshotBookRepository.markBooksSynced(currentSnapshotId, existingBooks);
        }
    }

    @Transactional
    public Page<KoboSnapshotBookEntity> getNewlyAddedBooks(String previousSnapshotId, String currentSnapshotId, Pageable pageable, Long userId) {
        Page<KoboSnapshotBookEntity> page = koboSnapshotBookRepository.findNewlyAddedBooks(previousSnapshotId, currentSnapshotId, true, pageable);
        List<Long> newlyAddedBookIds = page.getContent().stream()
                .map(KoboSnapshotBookEntity::getBookId)
                .toList();

        if (!newlyAddedBookIds.isEmpty()) {
            koboSnapshotBookRepository.markBooksSynced(currentSnapshotId, newlyAddedBookIds);
        }

        return page;
    }

    @Transactional
    public Page<KoboSnapshotBookEntity> getRemovedBooks(String previousSnapshotId, String currentSnapshotId, Long userId, Pageable pageable) {
        Page<KoboSnapshotBookEntity> page = koboSnapshotBookRepository.findRemovedBooks(previousSnapshotId, currentSnapshotId, pageable);

        List<Long> bookIds = page.getContent().stream()
                .map(KoboSnapshotBookEntity::getBookId)
                .toList();

        if (!bookIds.isEmpty()) {
            List<KoboDeletedBookProgressEntity> progressEntities = bookIds.stream()
                    .map(bookId -> KoboDeletedBookProgressEntity.builder()
                            .bookIdSynced(bookId)
                            .snapshotId(currentSnapshotId)
                            .userId(userId)
                            .build())
                    .toList();

            koboDeletedBookProgressRepository.saveAll(progressEntities);
        }
        return page;
    }

    private ShelfEntity getKoboShelf(Long userId) {
        return shelfRepository
                .findByUserIdAndName(userId, ShelfType.KOBO.getName())
                .orElseThrow(() -> new NoSuchElementException(
                        String.format("Shelf '%s' not found for user %d", ShelfType.KOBO.getName(), userId)
                ));
    }

    private List<KoboSnapshotBookEntity> mapBooksToKoboSnapshotBook(ShelfEntity shelf, KoboLibrarySnapshotEntity snapshot) {
        return shelf.getBookEntities().stream()
                .filter(bookEntity -> bookEntity.getBookType() == BookFileType.EPUB)
                .map(book -> {
                    KoboSnapshotBookEntity snapshotBook = mapper.toKoboSnapshotBook(book);
                    snapshotBook.setSnapshot(snapshot);
                    return snapshotBook;
                })
                .collect(Collectors.toList());
    }

    public void deleteById(String id) {
        koboLibrarySnapshotRepository.deleteById(id);
    }
}