package com.adityachandel.booklore.service.kobo;

import com.adityachandel.booklore.config.security.service.AuthenticationService;
import com.adityachandel.booklore.mapper.KoboReadingStateMapper;
import com.adityachandel.booklore.model.dto.kobo.*;
import com.adityachandel.booklore.model.dto.settings.KoboSettings;
import com.adityachandel.booklore.model.entity.*;
import com.adityachandel.booklore.model.enums.BookFileType;
import com.adityachandel.booklore.model.enums.KoboBookFormat;
import com.adityachandel.booklore.model.enums.KoboReadStatus;
import com.adityachandel.booklore.repository.KoboReadingStateRepository;
import com.adityachandel.booklore.repository.UserBookProgressRepository;
import com.adityachandel.booklore.service.book.BookQueryService;
import com.adityachandel.booklore.service.appsettings.AppSettingService;
import com.adityachandel.booklore.util.kobo.KoboUrlBuilder;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@AllArgsConstructor
@Service
@Slf4j
public class KoboEntitlementService {

    private static final Pattern NON_ALPHANUMERIC_LOWERCASE_PATTERN = Pattern.compile("[^a-z0-9]");
    private final KoboUrlBuilder koboUrlBuilder;
    private final BookQueryService bookQueryService;
    private final AppSettingService appSettingService;
    private final KoboCompatibilityService koboCompatibilityService;
    private final UserBookProgressRepository progressRepository;
    private final KoboReadingStateRepository readingStateRepository;
    private final KoboReadingStateMapper readingStateMapper;
    private final AuthenticationService authenticationService;
    private final KoboReadingStateBuilder readingStateBuilder;

    public List<NewEntitlement> generateNewEntitlements(Set<Long> bookIds, String token, boolean removed) {
        List<BookEntity> books = bookQueryService.findAllWithMetadataByIds(bookIds);

        return books.stream()
                .filter(koboCompatibilityService::isBookSupportedForKobo)
                .map(book -> NewEntitlement.builder()
                        .newEntitlement(BookEntitlementContainer.builder()
                                .bookEntitlement(buildBookEntitlement(book, removed))
                                .bookMetadata(mapToKoboMetadata(book, token))
                                .readingState(createInitialReadingState(book))
                                .build())
                        .build())
                .collect(Collectors.toList());
    }

    public List<ChangedEntitlement> generateChangedEntitlements(Set<Long> bookIds, String token, boolean removed) {
        List<BookEntity> books = bookQueryService.findAllWithMetadataByIds(bookIds);
        return books.stream()
                .filter(koboCompatibilityService::isBookSupportedForKobo)
                .map(book -> {
                    KoboBookMetadata metadata;
                    if (removed) {
                        metadata = KoboBookMetadata.builder()
                                .coverImageId(String.valueOf(book.getId()))
                                .crossRevisionId(String.valueOf(book.getId()))
                                .entitlementId(String.valueOf(book.getId()))
                                .revisionId(String.valueOf(book.getId()))
                                .workId(String.valueOf(book.getId()))
                                .title(String.valueOf(book.getId()))
                                .build();
                    } else {
                        metadata = mapToKoboMetadata(book, token);
                    }
                    return ChangedEntitlement.builder()
                            .changedEntitlement(BookEntitlementContainer.builder()
                                    .bookEntitlement(buildBookEntitlement(book, true))
                                    .bookMetadata(metadata)
                                    .build())
                            .build();
                })
                .collect(Collectors.toList());
    }

    public List<ChangedReadingState> generateChangedReadingStates(List<UserBookProgressEntity> progressEntries) {
        OffsetDateTime now = getCurrentUtc();
        String timestamp = now.toString();
        
        return progressEntries.stream()
                .map(progress -> buildChangedReadingState(progress, timestamp, now))
                .toList();
    }
    
    private ChangedReadingState buildChangedReadingState(UserBookProgressEntity progress, String timestamp, OffsetDateTime now) {
        String entitlementId = String.valueOf(progress.getBook().getId());
        
        KoboReadingState.CurrentBookmark bookmark = progress.getKoboProgressPercent() != null
                ? readingStateBuilder.buildBookmarkFromProgress(progress, now)
                : readingStateBuilder.buildEmptyBookmark(now);
        
        KoboReadingState readingState = KoboReadingState.builder()
                .entitlementId(entitlementId)
                .created(timestamp)
                .lastModified(timestamp)
                .priorityTimestamp(timestamp)
                .statusInfo(readingStateBuilder.buildStatusInfoFromProgress(progress, timestamp))
                .currentBookmark(bookmark)
                .statistics(KoboReadingState.Statistics.builder().lastModified(timestamp).build())
                .build();
        
        return ChangedReadingState.builder()
                .changedReadingState(ChangedReadingState.WrappedReadingState.builder()
                        .readingState(readingState)
                        .build())
                .build();
    }

    private KoboReadingState createInitialReadingState(BookEntity book) {
        OffsetDateTime now = getCurrentUtc();
        String entitlementId = String.valueOf(book.getId());
        
        KoboReadingState existingState = readingStateRepository.findByEntitlementId(entitlementId)
                .map(readingStateMapper::toDto)
                .orElse(null);
        
        Optional<UserBookProgressEntity> userProgress = progressRepository
                .findByUserIdAndBookId(authenticationService.getAuthenticatedUser().getId(), book.getId());
        
        KoboReadingState.CurrentBookmark bookmark = existingState != null && existingState.getCurrentBookmark() != null
                ? existingState.getCurrentBookmark()
                : userProgress
                        .filter(progress -> progress.getKoboProgressPercent() != null)
                        .map(progress -> readingStateBuilder.buildBookmarkFromProgress(progress, now))
                        .orElseGet(() -> readingStateBuilder.buildEmptyBookmark(now));
        
        KoboReadingState.StatusInfo statusInfo = userProgress
                .map(progress -> readingStateBuilder.buildStatusInfoFromProgress(progress, now.toString()))
                .orElseGet(() -> KoboReadingState.StatusInfo.builder()
                        .lastModified(now.toString())
                        .status(KoboReadStatus.READY_TO_READ)
                        .timesStartedReading(0)
                        .build());

        return KoboReadingState.builder()
                .entitlementId(entitlementId)
                .created(getCreatedOn(book).toString())
                .lastModified(now.toString())
                .statusInfo(statusInfo)
                .currentBookmark(bookmark)
                .statistics(KoboReadingState.Statistics.builder().lastModified(now.toString()).build())
                .priorityTimestamp(now.toString())
                .build();
    }

    private BookEntitlement buildBookEntitlement(BookEntity book, boolean removed) {
        OffsetDateTime now = getCurrentUtc();
        OffsetDateTime createdOn = getCreatedOn(book);

        return BookEntitlement.builder()
                .activePeriod(BookEntitlement.ActivePeriod.builder()
                        .from(now.toString())
                        .build())
                .isRemoved(removed)
                .status("Active")
                .crossRevisionId(String.valueOf(book.getId()))
                .revisionId(String.valueOf(book.getId()))
                .id(String.valueOf(book.getId()))
                .created(createdOn.toString())
                .lastModified(now.toString())
                .build();
    }

    public KoboBookMetadata getMetadataForBook(long bookId, String token) {
        List<BookEntity> books = bookQueryService.findAllWithMetadataByIds(Set.of(bookId))
                .stream()
                .filter(koboCompatibilityService::isBookSupportedForKobo)
                .toList();
        return mapToKoboMetadata(books.getFirst(), token);
    }

    private KoboBookMetadata mapToKoboMetadata(BookEntity book, String token) {
        BookMetadataEntity metadata = book.getMetadata();

        KoboBookMetadata.Publisher publisher = KoboBookMetadata.Publisher.builder()
                .name(metadata.getPublisher())
                .imprint(metadata.getPublisher())
                .build();

        List<String> authors = Optional.ofNullable(metadata.getAuthors())
                .map(list -> list.stream().map(AuthorEntity::getName).toList())
                .orElse(Collections.emptyList());

        List<String> categories = Optional.ofNullable(metadata.getCategories())
                .map(list -> list.stream().map(CategoryEntity::getName).toList())
                .orElse(Collections.emptyList());

        KoboBookMetadata.Series series = null;
        if (metadata.getSeriesName() != null) {
            series = KoboBookMetadata.Series.builder()
                    .id("series_" + metadata.getSeriesName().hashCode())
                    .name(metadata.getSeriesName())
                    .number(metadata.getSeriesNumber() != null ? metadata.getSeriesNumber().toString() : "1")
                    .numberFloat(metadata.getSeriesNumber() != null ? metadata.getSeriesNumber().doubleValue() : 1.0)
                    .build();
        }

        String downloadUrl = koboUrlBuilder.downloadUrl(token, book.getId());

        KoboBookFormat bookFormat = KoboBookFormat.EPUB3;
        KoboSettings koboSettings = appSettingService.getAppSettings().getKoboSettings();
        
        boolean isEpubFile = book.getBookType() == BookFileType.EPUB;
        boolean isCbxFile = book.getBookType() == BookFileType.CBX;
        
        if (koboSettings != null) {
            if (isEpubFile && koboSettings.isConvertToKepub()) {
                bookFormat = KoboBookFormat.KEPUB;
            } else if (isCbxFile && koboSettings.isConvertCbxToEpub()) {
                bookFormat = KoboBookFormat.EPUB3;
            }
        }

        return KoboBookMetadata.builder()
                .crossRevisionId(String.valueOf(book.getId()))
                .revisionId(String.valueOf(book.getId()))
                .publisher(publisher)
                .publicationDate(metadata.getPublishedDate() != null
                        ? metadata.getPublishedDate().atStartOfDay().atOffset(ZoneOffset.UTC).toString()
                        : null)
                .isbn(metadata.getIsbn13() != null ? metadata.getIsbn13() : metadata.getIsbn10())
                .genre(categories.isEmpty() ? null : categories.getFirst())
                .slug(metadata.getTitle() != null
                        ? NON_ALPHANUMERIC_LOWERCASE_PATTERN.matcher(metadata.getTitle().toLowerCase()).replaceAll("-")
                        : null)
                .coverImageId(String.valueOf(metadata.getBookId()))
                .workId(String.valueOf(book.getId()))
                .isPreOrder(false)
                .contributorRoles(Collections.emptyList())
                .entitlementId(String.valueOf(book.getId()))
                .title(metadata.getTitle())
                .description(metadata.getDescription())
                .contributors(authors)
                .series(series)
                .language(metadata.getLanguage())
                .downloadUrls(List.of(
                        KoboBookMetadata.DownloadUrl.builder()
                                .url(downloadUrl)
                                .format(bookFormat.toString())
                                .size(book.getFileSizeKb() * 1024)
                                .build()
                ))
                .build();
    }

    private OffsetDateTime getCurrentUtc() {
        return OffsetDateTime.now(ZoneOffset.UTC);
    }

    private OffsetDateTime getCreatedOn(BookEntity book) {
        return book.getAddedOn() != null ? book.getAddedOn().atOffset(ZoneOffset.UTC) : getCurrentUtc();
    }
}
