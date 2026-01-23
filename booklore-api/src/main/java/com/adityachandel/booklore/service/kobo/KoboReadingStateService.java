package com.adityachandel.booklore.service.kobo;

import com.adityachandel.booklore.config.security.service.AuthenticationService;
import com.adityachandel.booklore.mapper.KoboReadingStateMapper;
import com.adityachandel.booklore.model.dto.BookLoreUser;
import com.adityachandel.booklore.model.dto.KoboSyncSettings;
import com.adityachandel.booklore.model.dto.kobo.KoboReadingState;
import com.adityachandel.booklore.model.dto.response.kobo.KoboReadingStateResponse;
import com.adityachandel.booklore.model.entity.BookEntity;
import com.adityachandel.booklore.model.entity.BookLoreUserEntity;
import com.adityachandel.booklore.model.entity.KoboReadingStateEntity;
import com.adityachandel.booklore.model.entity.UserBookProgressEntity;
import com.adityachandel.booklore.model.enums.ReadStatus;
import com.adityachandel.booklore.repository.BookRepository;
import com.adityachandel.booklore.repository.KoboReadingStateRepository;
import com.adityachandel.booklore.repository.UserBookProgressRepository;
import com.adityachandel.booklore.repository.UserRepository;
import com.adityachandel.booklore.service.hardcover.HardcoverSyncService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.temporal.ChronoField;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class KoboReadingStateService {

    private static final int STATUS_SYNC_BUFFER_SECONDS = 10;
    private static final DateTimeFormatter KOBO_TIMESTAMP_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSSSSS'Z'").withZone(ZoneOffset.UTC);
    private static final DateTimeFormatter LOCAL_TIMESTAMP_FORMAT = new DateTimeFormatterBuilder()
            .appendPattern("yyyy-MM-dd HH:mm:ss")
            .optionalStart()
            .appendFraction(ChronoField.NANO_OF_SECOND, 0, 9, true)
            .optionalEnd()
            .toFormatter();

    private final KoboReadingStateRepository repository;
    private final KoboReadingStateMapper mapper;
    private final UserBookProgressRepository progressRepository;
    private final BookRepository bookRepository;
    private final UserRepository userRepository;
    private final AuthenticationService authenticationService;
    private final KoboSettingsService koboSettingsService;
    private final KoboReadingStateBuilder readingStateBuilder;
    private final HardcoverSyncService hardcoverSyncService;

    @Transactional
    public KoboReadingStateResponse saveReadingState(List<KoboReadingState> readingStates) {
        normalizePutTimestamps(readingStates);
        List<KoboReadingState> koboReadingStates = saveAll(readingStates);

        List<KoboReadingStateResponse.UpdateResult> updateResults = koboReadingStates.stream()
                .map(state -> KoboReadingStateResponse.UpdateResult.builder()
                        .entitlementId(state.getEntitlementId())
                        .currentBookmarkResult(KoboReadingStateResponse.Result.success())
                        .statisticsResult(KoboReadingStateResponse.Result.success())
                        .statusInfoResult(KoboReadingStateResponse.Result.success())
                        .build())
                .collect(Collectors.toList());

        return KoboReadingStateResponse.builder()
                .requestResult("Success")
                .updateResults(updateResults)
                .build();
    }

    private List<KoboReadingState> saveAll(List<KoboReadingState> dtos) {
        BookLoreUser user = authenticationService.getAuthenticatedUser();
        
        return dtos.stream()
                .map(dto -> {
                    String entitlementId = mapper.cleanString(dto.getEntitlementId());
                    Optional<KoboReadingStateEntity> existingOpt = repository.findByEntitlementId(entitlementId);
                    log.debug("Kobo reading state lookup: entitlementId={}, foundExisting={}",
                            entitlementId, existingOpt.isPresent());
                    KoboReadingStateEntity entity = existingOpt
                            .map(existing -> {
                                existing.setCurrentBookmarkJson(mapper.toJson(dto.getCurrentBookmark()));
                                existing.setStatisticsJson(mapper.toJson(dto.getStatistics()));
                                existing.setStatusInfoJson(mapper.toJson(dto.getStatusInfo()));
                                existing.setLastModifiedString(mapper.cleanString(String.valueOf(dto.getLastModified())));
                                existing.setPriorityTimestamp(mapper.cleanString(String.valueOf(dto.getLastModified())));
                                return existing;
                            })
                            .orElseGet(() -> {
                                KoboReadingStateEntity newEntity = mapper.toEntity(dto);
                                if (entitlementId != null && !entitlementId.isBlank()) {
                                    newEntity.setEntitlementId(entitlementId);
                                }
                                String created = dto.getCreated();
                                if (created == null || created.isBlank()) {
                                    created = OffsetDateTime.now(ZoneOffset.UTC).toString();
                                }
                                newEntity.setLastModifiedString(mapper.cleanString(created));
                                newEntity.setPriorityTimestamp(mapper.cleanString(created));
                                newEntity.setCreated(mapper.cleanString(created));
                                return newEntity;
                            });

                    KoboReadingStateEntity savedEntity = repository.save(entity);
                    
                    syncKoboProgressToUserBookProgress(dto, user.getId());
                    
                    return savedEntity;
                })
                .map(mapper::toDto)
                .collect(Collectors.toList());
    }

    @Transactional
    public void deleteReadingState(Long bookId) {
        repository.findByEntitlementId(String.valueOf(bookId)).ifPresent(repository::delete);
    }

    public List<KoboReadingState> getReadingState(String entitlementId) {
        Optional<KoboReadingState> readingState = repository.findByEntitlementId(entitlementId)
                .map(mapper::toDto)
                .or(() -> constructReadingStateFromProgress(entitlementId));

        return readingState
                .map(state -> {
                    normalizeResponseTimestamps(state);
                    return List.of(state);
                })
                .orElse(List.of());
    }
    
    private Optional<KoboReadingState> constructReadingStateFromProgress(String entitlementId) {
        try {
            Long bookId = Long.parseLong(entitlementId);
            BookLoreUser user = authenticationService.getAuthenticatedUser();
            
            return progressRepository.findByUserIdAndBookId(user.getId(), bookId)
                    .filter(progress -> progress.getKoboProgressPercent() != null || progress.getKoboLocation() != null)
                    .map(progress -> readingStateBuilder.buildReadingStateFromProgress(entitlementId, progress));
        } catch (NumberFormatException e) {
            log.warn("Invalid entitlement ID format when constructing reading state: {}", entitlementId);
            return Optional.empty();
        }
    }
    
    private void syncKoboProgressToUserBookProgress(KoboReadingState readingState, Long userId) {
        try {
            Long bookId = Long.parseLong(readingState.getEntitlementId());
            
            Optional<BookEntity> bookOpt = bookRepository.findById(bookId);
            if (bookOpt.isEmpty()) {
                log.warn("Book not found for entitlement ID: {}", readingState.getEntitlementId());
                return;
            }
            
            BookEntity book = bookOpt.get();
            Optional<BookLoreUserEntity> userOpt = userRepository.findById(userId);
            if (userOpt.isEmpty()) {
                log.warn("User not found: {}", userId);
                return;
            }
            
            UserBookProgressEntity progress = progressRepository.findByUserIdAndBookId(userId, bookId)
                    .orElseGet(() -> {
                        UserBookProgressEntity newProgress = new UserBookProgressEntity();
                        newProgress.setUser(userOpt.get());
                        newProgress.setBook(book);
                        return newProgress;
                    });
            
            KoboReadingState.CurrentBookmark bookmark = readingState.getCurrentBookmark();
            if (bookmark != null) {
                if (bookmark.getProgressPercent() != null) {
                    progress.setKoboProgressPercent(bookmark.getProgressPercent().floatValue());
                }
                
                KoboReadingState.CurrentBookmark.Location location = bookmark.getLocation();
                if (location != null) {
                    log.debug("Kobo location data: value={}, type={}, source={} (length={})", 
                            location.getValue(), location.getType(), location.getSource(),
                            location.getSource() != null ? location.getSource().length() : 0);
                    progress.setKoboLocation(location.getValue());
                    progress.setKoboLocationType(location.getType());
                    progress.setKoboLocationSource(location.getSource());
                }
            }
            
            Instant now = Instant.now();
            progress.setKoboProgressReceivedTime(now);
            progress.setLastReadTime(now);
            
            if (progress.getKoboProgressPercent() != null) {
                updateReadStatusFromKoboProgress(progress, now);
            }
            
            progressRepository.save(progress);
            log.debug("Synced Kobo progress: bookId={}, progress={}%", bookId, progress.getKoboProgressPercent());
            
            // Sync progress to Hardcover asynchronously (if enabled for this user)
            hardcoverSyncService.syncProgressToHardcover(book.getId(), progress.getKoboProgressPercent(), userId);
        } catch (NumberFormatException e) {
            log.warn("Invalid entitlement ID format: {}", readingState.getEntitlementId());
        }
    }

    private void normalizePutTimestamps(List<KoboReadingState> readingStates) {
        if (readingStates == null || readingStates.isEmpty()) {
            return;
        }
        String requestTimestamp = KOBO_TIMESTAMP_FORMAT.format(Instant.now());
        readingStates.forEach(state -> {
            state.setPriorityTimestamp(requestTimestamp);
            state.setLastModified(requestTimestamp);
            if (state.getStatusInfo() != null) {
                state.getStatusInfo().setLastModified(requestTimestamp);
            }
            if (state.getStatistics() != null) {
                state.getStatistics().setLastModified(requestTimestamp);
            }
            if (state.getCurrentBookmark() != null) {
                state.getCurrentBookmark().setLastModified(requestTimestamp);
            }
        });
    }

    private void normalizeResponseTimestamps(KoboReadingState state) {
        if (state == null) {
            return;
        }
        state.setCreated(normalizeTimestampValue(state.getCreated()));
        state.setLastModified(normalizeTimestampValue(state.getLastModified()));
        state.setPriorityTimestamp(normalizeTimestampValue(state.getPriorityTimestamp()));
        if (state.getStatusInfo() != null) {
            state.getStatusInfo().setLastModified(normalizeTimestampValue(state.getStatusInfo().getLastModified()));
        }
        if (state.getStatistics() != null) {
            state.getStatistics().setLastModified(normalizeTimestampValue(state.getStatistics().getLastModified()));
        }
        if (state.getCurrentBookmark() != null) {
            state.getCurrentBookmark().setLastModified(normalizeTimestampValue(state.getCurrentBookmark().getLastModified()));
        }
    }

    private String normalizeTimestampValue(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.isEmpty() || "null".equalsIgnoreCase(trimmed)) {
            return value;
        }
        try {
            Instant instant = Instant.parse(trimmed).truncatedTo(ChronoUnit.SECONDS);
            return KOBO_TIMESTAMP_FORMAT.format(instant);
        } catch (Exception ignored) {
        }
        try {
            OffsetDateTime offsetDateTime = OffsetDateTime.parse(trimmed);
            return KOBO_TIMESTAMP_FORMAT.format(offsetDateTime.toInstant().truncatedTo(ChronoUnit.SECONDS));
        } catch (Exception ignored) {
        }
        try {
            LocalDateTime localDateTime = LocalDateTime.parse(trimmed, LOCAL_TIMESTAMP_FORMAT);
            return KOBO_TIMESTAMP_FORMAT.format(localDateTime.toInstant(ZoneOffset.UTC).truncatedTo(ChronoUnit.SECONDS));
        } catch (Exception ignored) {
        }
        return value;
    }
    
    private void updateReadStatusFromKoboProgress(UserBookProgressEntity userProgress, Instant now) {
        if (shouldPreserveCurrentStatus(userProgress, now)) {
            return;
        }

        double koboProgressPercent = userProgress.getKoboProgressPercent();

        ReadStatus derivedStatus = deriveStatusFromProgress(koboProgressPercent);
        userProgress.setReadStatus(derivedStatus);

        if (derivedStatus == ReadStatus.READ && userProgress.getDateFinished() == null) {
            userProgress.setDateFinished(Instant.now());
        }
    }

    private boolean shouldPreserveCurrentStatus(UserBookProgressEntity progress, Instant now) {
        Instant statusModifiedTime = progress.getReadStatusModifiedTime();
        if (statusModifiedTime == null) {
            return false;
        }

        Instant statusSentTime = progress.getKoboStatusSentTime();

        boolean hasPendingStatusUpdate = statusSentTime == null || statusModifiedTime.isAfter(statusSentTime);
        if (hasPendingStatusUpdate) {
            return true;
        }

        return now.isBefore(statusSentTime.plusSeconds(STATUS_SYNC_BUFFER_SECONDS));
    }
    
    private ReadStatus deriveStatusFromProgress(double progressPercent) {
        KoboSyncSettings settings = koboSettingsService.getCurrentUserSettings();
        
        float finishedThreshold = settings.getProgressMarkAsFinishedThreshold() != null 
                ? settings.getProgressMarkAsFinishedThreshold() : 99f;
        float readingThreshold = settings.getProgressMarkAsReadingThreshold() != null 
                ? settings.getProgressMarkAsReadingThreshold() : 1f;
        
        if (progressPercent >= finishedThreshold) return ReadStatus.READ;
        if (progressPercent >= readingThreshold) return ReadStatus.READING;
        return ReadStatus.UNREAD;
    }
}
