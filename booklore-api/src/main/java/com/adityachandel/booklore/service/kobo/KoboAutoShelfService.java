package com.adityachandel.booklore.service.kobo;

import com.adityachandel.booklore.model.entity.BookEntity;
import com.adityachandel.booklore.model.entity.KoboUserSettingsEntity;
import com.adityachandel.booklore.model.entity.ShelfEntity;
import com.adityachandel.booklore.model.enums.ShelfType;
import com.adityachandel.booklore.repository.BookRepository;
import com.adityachandel.booklore.repository.KoboUserSettingsRepository;
import com.adityachandel.booklore.repository.ShelfRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class KoboAutoShelfService {

    private final KoboUserSettingsRepository koboUserSettingsRepository;
    private final ShelfRepository shelfRepository;
    private final BookRepository bookRepository;
    private final KoboCompatibilityService koboCompatibilityService;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void autoAddBookToKoboShelves(Long bookId) {
        BookEntity book = bookRepository.findById(bookId).orElse(null);
        if (book == null) {
            log.warn("Book not found for auto-add to Kobo shelf: {}", bookId);
            return;
        }

        autoAddBookToKoboShelvesInternal(book);
    }

    @Transactional
    public void autoAddBookToKoboShelvesInternal(BookEntity book) {
        if (book == null) {
            log.warn("Book is null for auto-add to Kobo shelf");
            return;
        }

        Long bookId = book.getId();

        if (!koboCompatibilityService.isBookSupportedForKobo(book)) {
            log.debug("Book {} is not Kobo-compatible, skipping auto-add", bookId);
            return;
        }

        List<KoboUserSettingsEntity> usersWithAutoAdd = koboUserSettingsRepository.findAll().stream()
                .filter(KoboUserSettingsEntity::isAutoAddToShelf)
                .filter(KoboUserSettingsEntity::isSyncEnabled)
                .toList();

        for (KoboUserSettingsEntity userSettings : usersWithAutoAdd) {
            Long userId = userSettings.getUserId();

            ShelfEntity koboShelf = shelfRepository.findByUserIdAndName(userId, ShelfType.KOBO.getName())
                    .orElse(null);

            if (koboShelf == null) {
                log.debug("User {} has auto-add enabled but no Kobo shelf exists", userId);
                continue;
            }

            if (book.getShelves().contains(koboShelf)) {
                log.debug("Book {} already on Kobo shelf for user {}", bookId, userId);
                continue;
            }

            book.getShelves().add(koboShelf);
            log.info("Auto-added book {} to Kobo shelf for user {}", bookId, userId);
        }

        bookRepository.save(book);
    }
}
