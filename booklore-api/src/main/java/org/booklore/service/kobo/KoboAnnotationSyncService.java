package org.booklore.service.kobo;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.booklore.model.dto.HardcoverSyncSettings;
import org.booklore.model.dto.kobo.KoboAnnotation;
import org.booklore.model.dto.kobo.KoboAnnotationPatchRequest;
import org.booklore.model.entity.BookEntity;
import org.booklore.model.entity.BookLoreUserEntity;
import org.booklore.model.entity.KoboAnnotationSyncEntity;
import org.booklore.model.entity.UserBookProgressEntity;
import org.booklore.repository.BookRepository;
import org.booklore.repository.KoboAnnotationSyncRepository;
import org.booklore.repository.UserBookProgressRepository;
import org.booklore.repository.UserRepository;
import org.booklore.service.hardcover.HardcoverSyncService;
import org.booklore.service.hardcover.HardcoverSyncService.HardcoverBookInfo;
import org.booklore.service.hardcover.HardcoverSyncService.HardcoverUserBookInfo;
import org.booklore.service.hardcover.HardcoverSyncSettingsService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class KoboAnnotationSyncService {

    private final KoboAnnotationSyncRepository annotationSyncRepository;
    private final BookRepository bookRepository;
    private final UserRepository userRepository;
    private final UserBookProgressRepository progressRepository;
    private final HardcoverSyncSettingsService hardcoverSyncSettingsService;
    private final HardcoverSyncService hardcoverSyncService;
    private final PlatformTransactionManager transactionManager;

    private final ConcurrentHashMap<String, ReentrantLock> processingLocks = new ConcurrentHashMap<>();

    /**
     * Process an annotation PATCH from Kobo. Serializes concurrent requests for the same
     * user+book so duplicate Kobo PATCHes don't create duplicate Hardcover journal entries.
     * Runs synchronously (not @Async) to match the proxy-after-process pattern.
     */
    public void processAnnotationPatch(Long userId, String entitlementId, KoboAnnotationPatchRequest patchData) {
        String lockKey = userId + ":" + entitlementId;
        ReentrantLock lock = processingLocks.computeIfAbsent(lockKey, k -> new ReentrantLock());
        lock.lock();
        try {
            new TransactionTemplate(transactionManager).executeWithoutResult(status ->
                    doProcessAnnotationPatch(userId, entitlementId, patchData)
            );
        } finally {
            lock.unlock();
        }
    }

    private void doProcessAnnotationPatch(Long userId, String entitlementId, KoboAnnotationPatchRequest patchData) {
        log.info("[AnnotationSync] Starting for user={}, entitlementId={}, updated={}, deleted={}",
                userId, entitlementId,
                patchData.getUpdatedAnnotations() != null ? patchData.getUpdatedAnnotations().size() : 0,
                patchData.getDeletedAnnotationIds() != null ? patchData.getDeletedAnnotationIds().size() : 0);
        try {
            // Check user has Hardcover sync enabled
            HardcoverSyncSettings settings = hardcoverSyncSettingsService.getSettingsForUserId(userId);
            if (settings == null || !settings.isHardcoverSyncEnabled()
                    || settings.getHardcoverApiKey() == null || settings.getHardcoverApiKey().isBlank()) {
                log.info("[AnnotationSync] Skipped: Hardcover sync not enabled for user {}", userId);
                return;
            }

            String apiToken = settings.getHardcoverApiKey();

            // Resolve book by entitlement ID (numeric book ID)
            Long bookId;
            try {
                bookId = Long.parseLong(entitlementId);
            } catch (NumberFormatException e) {
                log.info("[AnnotationSync] Skipped: non-numeric entitlement ID {}", entitlementId);
                return;
            }

            BookEntity book = bookRepository.findById(bookId).orElse(null);
            if (book == null) {
                log.info("[AnnotationSync] Skipped: book {} not found", bookId);
                return;
            }

            // Process deletions
            processDeletions(userId, apiToken, patchData.getDeletedAnnotationIds());

            // Process updates
            processUpdates(userId, bookId, book, apiToken, patchData.getUpdatedAnnotations());

            log.info("[AnnotationSync] Completed for user={}, book={}", userId, bookId);

        } catch (Exception e) {
            log.error("[AnnotationSync] Failed for user {} entitlement {}: {}",
                    userId, entitlementId, e.getMessage(), e);
        }
    }

    private void processDeletions(Long userId, String apiToken, List<String> deletedAnnotationIds) {
        if (deletedAnnotationIds == null || deletedAnnotationIds.isEmpty()) {
            return;
        }

        List<KoboAnnotationSyncEntity> syncRecords =
                annotationSyncRepository.findByUserIdAndAnnotationIdIn(userId, deletedAnnotationIds);

        for (KoboAnnotationSyncEntity entity : syncRecords) {
            try {
                if (entity.isSyncedToHardcover() && entity.getHardcoverJournalId() != null) {
                    hardcoverSyncService.deleteJournalEntry(apiToken, entity.getHardcoverJournalId());
                }
                annotationSyncRepository.delete(entity);
                log.debug("Deleted annotation sync record for annotation {}", entity.getAnnotationId());
            } catch (Exception e) {
                log.warn("Failed to process deletion for annotation {}: {}", entity.getAnnotationId(), e.getMessage());
            }
        }
    }

    private void processUpdates(Long userId, Long bookId, BookEntity book, String apiToken,
                                List<KoboAnnotation> updatedAnnotations) {
        if (updatedAnnotations == null || updatedAnnotations.isEmpty()) {
            return;
        }

        // Batch-load existing sync records (N+1 prevention)
        List<String> annotationIds = updatedAnnotations.stream()
                .map(KoboAnnotation::getId)
                .filter(Objects::nonNull)
                .toList();
        Map<String, KoboAnnotationSyncEntity> existingSyncs =
                annotationSyncRepository.findByUserIdAndAnnotationIdIn(userId, annotationIds).stream()
                        .collect(Collectors.toMap(KoboAnnotationSyncEntity::getAnnotationId, Function.identity()));

        // Resolve Hardcover book info once (stored ID first, then ISBN fallback; persist for future syncs)
        HardcoverBookInfo bookInfo = hardcoverSyncService.resolveHardcoverBook(book.getMetadata(), apiToken, true);
        if (bookInfo == null) {
            log.info("[AnnotationSync] Skipped: book {} could not be resolved on Hardcover", bookId);
            return;
        }
        String hardcoverBookId = bookInfo.bookId();
        log.info("[AnnotationSync] Processing {} annotations for book {} (hardcoverBookId={})",
                updatedAnnotations.size(), bookId, hardcoverBookId);

        HardcoverUserBookInfo userBookInfo = hardcoverSyncService.getUserBook(apiToken, hardcoverBookId);

        // Get privacy setting once
        Integer privacySettingId = hardcoverSyncService.getPrivacy(apiToken);

        // Look up stored Kobo progress for this book
        Double progressPercent = progressRepository.findByUserIdAndBookId(userId, bookId)
                .map(UserBookProgressEntity::getKoboProgressPercent)
                .map(Float::doubleValue)
                .orElse(null);

        // Get user entity for creating new sync records
        BookLoreUserEntity userEntity = userRepository.findById(userId).orElse(null);
        if (userEntity == null) {
            log.warn("User {} not found, skipping annotation sync", userId);
            return;
        }

        Integer editionId = userBookInfo != null ? userBookInfo.editionId() : null;
        Integer totalPages = userBookInfo != null ? userBookInfo.pages() : null;

        for (KoboAnnotation annotation : updatedAnnotations) {
            try {
                processAnnotation(userId, bookId, book, userEntity, apiToken, annotation,
                        existingSyncs, hardcoverBookId, editionId, privacySettingId,
                        progressPercent, totalPages);
            } catch (Exception e) {
                log.warn("Failed to process annotation {}: {}", annotation.getId(), e.getMessage());
            }
        }
    }

    private void processAnnotation(Long userId, Long bookId, BookEntity book,
                                   BookLoreUserEntity userEntity, String apiToken,
                                   KoboAnnotation annotation,
                                   Map<String, KoboAnnotationSyncEntity> existingSyncs,
                                   String hardcoverBookId, Integer editionId,
                                   Integer privacySettingId, Double progressPercent,
                                   Integer totalPages) {
        String annotationId = annotation.getId();
        if (annotationId == null || annotationId.isBlank()) {
            log.info("[AnnotationSync] Skipping annotation with null/blank ID");
            return;
        }

        String highlightedText = annotation.getHighlightedText();
        String noteText = annotation.getNoteText();
        String highlightColor = annotation.getHighlightColor();

        log.info("[AnnotationSync] Annotation {}: type={}, highlight={}chars, note={}, color={}",
                annotationId, annotation.getType(),
                highlightedText != null ? highlightedText.length() : 0,
                noteText != null ? noteText.length() + "chars" : "null",
                highlightColor);

        // Skip if no text content
        if ((highlightedText == null || highlightedText.isBlank())
                && (noteText == null || noteText.isBlank())) {
            log.info("[AnnotationSync] Skipping annotation {}: no text content", annotationId);
            return;
        }

        KoboAnnotationSyncEntity existing = existingSyncs.get(annotationId);

        // Idempotency check: skip if already synced with identical content
        if (existing != null && existing.isSyncedToHardcover()) {
            if (Objects.equals(existing.getHighlightedText(), highlightedText)
                    && Objects.equals(existing.getNoteText(), noteText)
                    && Objects.equals(existing.getHighlightColor(), highlightColor)) {
                log.info("[AnnotationSync] Annotation {} already synced with same content, skipping", annotationId);
                return;
            }
        }

        if (existing != null && existing.isSyncedToHardcover() && existing.getHardcoverJournalId() != null) {
            // Update existing journal entry
            log.info("[AnnotationSync] Updating existing journal entry {} for annotation {}", existing.getHardcoverJournalId(), annotationId);
            boolean updated = hardcoverSyncService.updateJournalEntry(
                    apiToken, existing.getHardcoverJournalId(), highlightedText, noteText);
            if (updated) {
                existing.setHighlightedText(highlightedText);
                existing.setNoteText(noteText);
                existing.setHighlightColor(highlightColor);
                annotationSyncRepository.save(existing);
                log.info("[AnnotationSync] Updated annotation {}", annotationId);
            } else {
                log.warn("[AnnotationSync] Failed to update journal entry for annotation {}", annotationId);
            }
        } else {
            // Create new journal entry
            log.info("[AnnotationSync] Creating new journal entry for annotation {} (hardcoverBookId={}, editionId={})",
                    annotationId, hardcoverBookId, editionId);
            Integer journalId = hardcoverSyncService.addJournalEntry(
                    apiToken, hardcoverBookId, editionId, privacySettingId,
                    highlightedText, noteText, progressPercent, totalPages);

            if (journalId != null) {
                if (existing != null) {
                    existing.setSyncedToHardcover(true);
                    existing.setHardcoverJournalId(journalId);
                    existing.setHighlightedText(highlightedText);
                    existing.setNoteText(noteText);
                    existing.setHighlightColor(highlightColor);
                    annotationSyncRepository.save(existing);
                } else {
                    KoboAnnotationSyncEntity newSync = KoboAnnotationSyncEntity.builder()
                            .user(userEntity)
                            .book(book)
                            .annotationId(annotationId)
                            .syncedToHardcover(true)
                            .hardcoverJournalId(journalId)
                            .highlightedText(highlightedText)
                            .noteText(noteText)
                            .highlightColor(highlightColor)
                            .build();
                    annotationSyncRepository.save(newSync);
                }
                log.info("[AnnotationSync] Created journal entry {} for annotation {}", journalId, annotationId);
            } else {
                log.warn("[AnnotationSync] addJournalEntry returned null for annotation {}", annotationId);
            }
        }
    }
}
