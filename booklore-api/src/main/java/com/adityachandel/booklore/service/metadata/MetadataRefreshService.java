package com.adityachandel.booklore.service.metadata;

import com.adityachandel.booklore.exception.ApiError;
import com.adityachandel.booklore.mapper.BookMapper;
import com.adityachandel.booklore.model.MetadataUpdateWrapper;
import com.adityachandel.booklore.model.dto.*;
import com.adityachandel.booklore.model.dto.request.FetchMetadataRequest;
import com.adityachandel.booklore.model.dto.request.MetadataRefreshOptions;
import com.adityachandel.booklore.model.dto.request.MetadataRefreshRequest;
import com.adityachandel.booklore.model.dto.settings.AppSettings;
import com.adityachandel.booklore.model.entity.BookEntity;
import com.adityachandel.booklore.model.entity.LibraryEntity;
import com.adityachandel.booklore.model.entity.MetadataFetchJobEntity;
import com.adityachandel.booklore.model.entity.MetadataFetchProposalEntity;
import com.adityachandel.booklore.model.enums.*;
import com.adityachandel.booklore.model.websocket.Topic;
import com.adityachandel.booklore.repository.BookRepository;
import com.adityachandel.booklore.repository.LibraryRepository;
import com.adityachandel.booklore.repository.MetadataFetchJobRepository;
import com.adityachandel.booklore.service.NotificationService;
import com.adityachandel.booklore.service.appsettings.AppSettingService;
import com.adityachandel.booklore.service.metadata.parser.BookParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

import static com.adityachandel.booklore.model.enums.MetadataProvider.*;

@Slf4j
@AllArgsConstructor
@Service
public class MetadataRefreshService {

    private final LibraryRepository libraryRepository;
    private final MetadataFetchJobRepository metadataFetchJobRepository;
    private final BookMapper bookMapper;
    private final BookMetadataUpdater bookMetadataUpdater;
    private final NotificationService notificationService;
    private final AppSettingService appSettingService;
    private final Map<MetadataProvider, BookParser> parserMap;
    private final ObjectMapper objectMapper;
    private final BookRepository bookRepository;
    private final PlatformTransactionManager transactionManager;


    public void refreshMetadata(MetadataRefreshRequest request, Long userId, String jobId) {
        final Set<Long> bookIds = null;
        final int totalBooks;
        try {
            AppSettings appSettings = appSettingService.getAppSettings();

            final boolean isLibraryRefresh = request.getRefreshType() == MetadataRefreshRequest.RefreshType.LIBRARY;
            final MetadataRefreshOptions requestRefreshOptions = request.getRefreshOptions();

            final boolean useRequestOptions = requestRefreshOptions != null;
            final MetadataRefreshOptions libraryRefreshOptions = !useRequestOptions && isLibraryRefresh ? resolveMetadataRefreshOptions(request.getLibraryId(), appSettings) : null;
            final List<MetadataProvider> fixedProviders = useRequestOptions ?
                    prepareProviders(requestRefreshOptions) :
                    (isLibraryRefresh ? prepareProviders(libraryRefreshOptions) : null);

            final Set<Long> actualBookIds = getBookEntities(request);
            totalBooks = actualBookIds.size();

            MetadataRefreshOptions reviewModeOptions = requestRefreshOptions != null ?
                    requestRefreshOptions :
                    (libraryRefreshOptions != null ? libraryRefreshOptions : appSettings.getDefaultMetadataRefreshOptions());
            boolean isReviewMode = Boolean.TRUE.equals(reviewModeOptions.getReviewBeforeApply());

            MetadataFetchJobEntity task = MetadataFetchJobEntity.builder()
                    .taskId(jobId)
                    .userId(userId)
                    .status(MetadataFetchTaskStatus.IN_PROGRESS)
                    .startedAt(Instant.now())
                    .totalBooksCount(totalBooks)
                    .completedBooks(0)
                    .build();
            metadataFetchJobRepository.save(task);

            TransactionTemplate txTemplate = new TransactionTemplate(transactionManager);
            int completedCount = 0;

            for (Long bookId : actualBookIds) {
                checkForInterruption(jobId, task, totalBooks);
                int finalCompletedCount = completedCount;
                txTemplate.execute(status -> {
                    BookEntity book = bookRepository.findAllWithMetadataByIds(Collections.singleton(bookId))
                            .stream().findFirst()
                            .orElseThrow(() -> ApiError.BOOK_NOT_FOUND.createException(bookId));
                    try {
                        checkForInterruption(jobId, task, totalBooks);
                        if (book.getMetadata().areAllFieldsLocked()) {
                            log.info("Skipping locked book: {}", book.getFileName());
                            sendBatchProgressNotification(jobId, finalCompletedCount, totalBooks, "Skipped locked book: " + book.getMetadata().getTitle(), MetadataFetchTaskStatus.IN_PROGRESS, isReviewMode);
                            return null;
                        }

                        MetadataRefreshOptions refreshOptions;
                        List<MetadataProvider> providers;

                        if (useRequestOptions) {
                            refreshOptions = requestRefreshOptions;
                            providers = fixedProviders;
                        } else if (isLibraryRefresh) {
                            refreshOptions = libraryRefreshOptions;
                            providers = fixedProviders;
                        } else {
                            refreshOptions = resolveMetadataRefreshOptions(book.getLibrary().getId(), appSettings);
                            providers = prepareProviders(refreshOptions);
                        }

                        reportProgressIfNeeded(task, jobId, finalCompletedCount, totalBooks, book, isReviewMode);
                        Map<MetadataProvider, BookMetadata> metadataMap = fetchMetadataForBook(providers, book);
                        if (providers.contains(GoodReads)) {
                            try {
                                Thread.sleep(ThreadLocalRandom.current().nextLong(500, 1500));
                            } catch (InterruptedException e) {
                                Thread.currentThread().interrupt();
                                status.setRollbackOnly();
                                return null;
                            }
                        }
                        BookMetadata fetched = buildFetchMetadata(book.getId(), refreshOptions, metadataMap);

                        boolean bookReviewMode = Boolean.TRUE.equals(refreshOptions.getReviewBeforeApply());
                        if (bookReviewMode) {
                            saveProposal(task, book.getId(), fetched);
                        } else {
                            updateBookMetadata(book, fetched, refreshOptions.isRefreshCovers(), refreshOptions.isMergeCategories());
                        }

                        sendBatchProgressNotification(jobId, finalCompletedCount + 1, totalBooks, "Processed: " + book.getMetadata().getTitle(), MetadataFetchTaskStatus.IN_PROGRESS, bookReviewMode);
                    } catch (Exception e) {
                        if (Thread.currentThread().isInterrupted()) {
                            log.info("Processing interrupted for book: {}", book.getFileName());
                            status.setRollbackOnly();
                            return null;
                        }
                        log.error("Metadata update failed for book: {}", book.getFileName(), e);
                        sendBatchProgressNotification(jobId, finalCompletedCount, totalBooks, String.format("Failed to process: %s - %s", book.getMetadata().getTitle(), e.getMessage()), MetadataFetchTaskStatus.ERROR, isReviewMode);
                    }
                    bookRepository.saveAndFlush(book);
                    return null;
                });
                completedCount++;
            }

            completeTask(task, completedCount, totalBooks, isReviewMode);
            log.info("Metadata refresh task {} completed successfully", jobId);

        } catch (RuntimeException e) {
            if (e.getCause() instanceof InterruptedException) {
                log.info("Metadata refresh task {} cancelled successfully", jobId);
                return;
            }
            log.error("Fatal error during metadata refresh", e);
            int totalBooksForError = 0;
            sendBatchProgressNotification(jobId, 0, totalBooksForError, "Fatal error during metadata refresh: " + e.getMessage(), MetadataFetchTaskStatus.ERROR, false);
            throw e;
        } catch (Exception fatal) {
            log.error("Fatal error during metadata refresh", fatal);
            int totalBooksForError = bookIds != null ? bookIds.size() : 0;
            sendBatchProgressNotification(jobId, 0, totalBooksForError, "Fatal error during metadata refresh: " + fatal.getMessage(), MetadataFetchTaskStatus.ERROR, false);
            throw fatal;
        }
    }

    MetadataRefreshOptions resolveMetadataRefreshOptions(Long libraryId, AppSettings appSettings) {
        MetadataRefreshOptions defaultOptions = appSettings.getDefaultMetadataRefreshOptions();
        List<MetadataRefreshOptions> libraryOptions = appSettings.getLibraryMetadataRefreshOptions();

        if (libraryId != null && libraryOptions != null) {
            return libraryOptions.stream()
                    .filter(options -> libraryId.equals(options.getLibraryId()))
                    .findFirst()
                    .orElse(defaultOptions);
        }

        return defaultOptions;
    }

    public Map<MetadataProvider, BookMetadata> fetchMetadataForBook(List<MetadataProvider> providers, Book book) {
        return providers.stream()
                .map(provider -> createInterruptibleMetadataFuture(() -> fetchTopMetadataFromAProvider(provider, book)))
                .map(this::joinFutureSafely)
                .filter(Objects::nonNull)
                .collect(Collectors.toMap(
                        BookMetadata::getProvider,
                        metadata -> metadata,
                        (existing, replacement) -> existing
                ));
    }

    public Map<MetadataProvider, BookMetadata> fetchMetadataForBook(List<MetadataProvider> providers, BookEntity bookEntity) {
        Book book = bookMapper.toBook(bookEntity);
        return providers.stream()
                .map(provider -> createInterruptibleMetadataFuture(() -> fetchTopMetadataFromAProvider(provider, book)))
                .map(this::joinFutureSafely)
                .filter(Objects::nonNull)
                .collect(Collectors.toMap(
                        BookMetadata::getProvider,
                        metadata -> metadata,
                        (existing, replacement) -> existing
                ));
    }

    private CompletableFuture<BookMetadata> createInterruptibleMetadataFuture(java.util.function.Supplier<BookMetadata> metadataSupplier) {
        return CompletableFuture.supplyAsync(() -> {
            if (Thread.currentThread().isInterrupted()) {
                log.info("Skipping metadata fetch due to interruption");
                return null;
            }
            return metadataSupplier.get();
        }).exceptionally(e -> {
            if (e.getCause() instanceof InterruptedException) {
                log.info("Metadata fetch was interrupted");
                Thread.currentThread().interrupt();
                return null;
            }
            log.error("Error fetching metadata from provider", e);
            return null;
        });
    }

    private BookMetadata joinFutureSafely(CompletableFuture<BookMetadata> future) {
        try {
            return future.join();
        } catch (Exception e) {
            if (Thread.currentThread().isInterrupted()) {
                log.info("Future join interrupted");
                return null;
            }
            throw e;
        }
    }

    private void checkForInterruption(String jobId, MetadataFetchJobEntity task, int totalBooks) {
        if (Thread.currentThread().isInterrupted()) {
            log.info("Metadata refresh task {} cancelled by user request", jobId);
            sendBatchProgressNotification(jobId, 0, totalBooks, "Task cancelled by user", MetadataFetchTaskStatus.ERROR, false);
            if (task != null) {
                failTask(task, totalBooks);
            }
            throw new RuntimeException(new InterruptedException("Task was cancelled"));
        }
    }


    private void reportProgressIfNeeded(MetadataFetchJobEntity task, String taskId, int completedCount, int total, BookEntity book, boolean isReviewMode) {
        if (task == null) return;
        task.setCompletedBooks(completedCount);
        metadataFetchJobRepository.save(task);
        String message = String.format("Processing '%s'", book.getMetadata().getTitle());
        sendBatchProgressNotification(taskId, completedCount, total, message, MetadataFetchTaskStatus.IN_PROGRESS, isReviewMode);
    }

    private void sendBatchProgressNotification(String taskId, int current, int total, String message, MetadataFetchTaskStatus status, boolean isReview) {
        notificationService.sendMessage(Topic.BOOK_METADATA_BATCH_PROGRESS,
                new MetadataBatchProgressNotification(
                        taskId, current, total, message, status.name(), isReview
                ));
    }

    private void completeTask(MetadataFetchJobEntity task, int completed, int total, boolean isReviewMode) {
        task.setStatus(MetadataFetchTaskStatus.COMPLETED);
        task.setCompletedAt(Instant.now());
        task.setCompletedBooks(completed);
        metadataFetchJobRepository.save(task);
        sendBatchProgressNotification(task.getTaskId(), completed, total, "Batch metadata fetch successfully completed!", MetadataFetchTaskStatus.COMPLETED, isReviewMode);
    }

    private void failTask(MetadataFetchJobEntity task, int total) {
        task.setStatus(MetadataFetchTaskStatus.ERROR);
        task.setCompletedAt(Instant.now());
        metadataFetchJobRepository.save(task);
        sendBatchProgressNotification(task.getTaskId(), 0, total, "Error: " + "Task was cancelled", MetadataFetchTaskStatus.ERROR, false);
    }

    private void saveProposal(MetadataFetchJobEntity job, Long bookId, BookMetadata metadata) throws JsonProcessingException {
        if (job.getProposals() == null) {
            job.setProposals(new ArrayList<>());
        }
        MetadataFetchProposalEntity proposal = MetadataFetchProposalEntity.builder()
                .job(job)
                .bookId(bookId)
                .metadataJson(objectMapper.writeValueAsString(metadata))
                .status(FetchedMetadataProposalStatus.FETCHED)
                .fetchedAt(Instant.now())
                .build();
        job.getProposals().add(proposal);
    }


    public void updateBookMetadata(BookEntity bookEntity, BookMetadata metadata, boolean replaceCover, boolean mergeCategories) {
        if (metadata != null) {
            MetadataUpdateWrapper metadataUpdateWrapper = MetadataUpdateWrapper.builder()
                    .metadata(metadata)
                    .build();
            bookMetadataUpdater.setBookMetadata(bookEntity, metadataUpdateWrapper, replaceCover, mergeCategories);

            Book book = bookMapper.toBook(bookEntity);
            notificationService.sendMessage(Topic.BOOK_METADATA_UPDATE, book);
        }
    }

    public List<MetadataProvider> prepareProviders(MetadataRefreshOptions refreshOptions) {
        Set<MetadataProvider> allProviders = new HashSet<>(getAllProvidersUsingIndividualFields(refreshOptions));
        return new ArrayList<>(allProviders);
    }

    protected Set<MetadataProvider> getAllProvidersUsingIndividualFields(MetadataRefreshOptions refreshOptions) {
        MetadataRefreshOptions.FieldOptions fieldOptions = refreshOptions.getFieldOptions();
        Set<MetadataProvider> uniqueProviders = new HashSet<>();

        if (fieldOptions != null) {
            addProviderToSet(fieldOptions.getTitle(), uniqueProviders);
            addProviderToSet(fieldOptions.getDescription(), uniqueProviders);
            addProviderToSet(fieldOptions.getAuthors(), uniqueProviders);
            addProviderToSet(fieldOptions.getCategories(), uniqueProviders);
            addProviderToSet(fieldOptions.getMoods(), uniqueProviders);
            addProviderToSet(fieldOptions.getTags(), uniqueProviders);
            addProviderToSet(fieldOptions.getCover(), uniqueProviders);
        }

        return uniqueProviders;
    }

    protected void addProviderToSet(MetadataRefreshOptions.FieldProvider fieldProvider, Set<MetadataProvider> providerSet) {
        if (fieldProvider != null) {
            if (fieldProvider.getP4() != null) providerSet.add(fieldProvider.getP4());
            if (fieldProvider.getP3() != null) providerSet.add(fieldProvider.getP3());
            if (fieldProvider.getP2() != null) providerSet.add(fieldProvider.getP2());
            if (fieldProvider.getP1() != null) providerSet.add(fieldProvider.getP1());
        }
    }


    public BookMetadata fetchTopMetadataFromAProvider(MetadataProvider provider, Book book) {
        return getParser(provider).fetchTopMetadata(book, buildFetchMetadataRequestFromBook(book));
    }

    private BookParser getParser(MetadataProvider provider) {
        BookParser parser = parserMap.get(provider);
        if (parser == null) {
            throw ApiError.METADATA_SOURCE_NOT_IMPLEMENT_OR_DOES_NOT_EXIST.createException();
        }
        return parser;
    }

    private FetchMetadataRequest buildFetchMetadataRequestFromBook(Book book) {
        BookMetadata metadata = book.getMetadata();
        return FetchMetadataRequest.builder()
                .isbn(metadata.getIsbn10())
                .asin(metadata.getAsin())
                .author(metadata.getAuthors() != null ? String.join(", ", metadata.getAuthors()) : null)
                .title(metadata.getTitle())
                .bookId(book.getId())
                .build();
    }

    public BookMetadata buildFetchMetadata(Long bookId, MetadataRefreshOptions refreshOptions, Map<MetadataProvider, BookMetadata> metadataMap) {
        BookMetadata metadata = BookMetadata.builder().bookId(bookId).build();
        MetadataRefreshOptions.FieldOptions fieldOptions = refreshOptions.getFieldOptions();

        metadata.setTitle(resolveFieldAsString(metadataMap, fieldOptions.getTitle(), BookMetadata::getTitle));
        metadata.setDescription(resolveFieldAsString(metadataMap, fieldOptions.getDescription(), BookMetadata::getDescription));
        metadata.setAuthors(resolveFieldAsList(metadataMap, fieldOptions.getAuthors(), BookMetadata::getAuthors));

        List<BookReview> allReviews = metadataMap.values().stream()
                .filter(Objects::nonNull)
                .flatMap(md -> Optional.ofNullable(md.getBookReviews()).stream().flatMap(Collection::stream))
                .collect(Collectors.toList());
        if (!allReviews.isEmpty()) {
            metadata.setBookReviews(allReviews);
        }

        if (metadataMap.containsKey(GoodReads)) {
            metadata.setGoodreadsId(metadataMap.get(GoodReads).getGoodreadsId());
        }
        if (metadataMap.containsKey(Hardcover)) {
            metadata.setHardcoverId(metadataMap.get(Hardcover).getHardcoverId());
        }
        if (metadataMap.containsKey(Google)) {
            metadata.setGoogleId(metadataMap.get(Google).getGoogleId());
        }
        if (metadataMap.containsKey(Comicvine)) {
            metadata.setComicvineId(metadataMap.get(Comicvine).getComicvineId());
        }

        if (refreshOptions.isMergeCategories()) {
            metadata.setCategories(getAllCategories(metadataMap, fieldOptions.getCategories(), BookMetadata::getCategories));
            metadata.setMoods(getAllMoods(metadataMap, fieldOptions.getMoods(), BookMetadata::getMoods));
            metadata.setTags(getAllTags(metadataMap, fieldOptions.getTags(), BookMetadata::getTags));
        } else {
            metadata.setCategories(resolveFieldAsList(metadataMap, fieldOptions.getCategories(), BookMetadata::getCategories));
            metadata.setMoods(resolveFieldAsList(metadataMap, fieldOptions.getMoods(), BookMetadata::getMoods));
            metadata.setTags(resolveFieldAsList(metadataMap, fieldOptions.getTags(), BookMetadata::getTags));
        }
        metadata.setThumbnailUrl(resolveFieldAsString(metadataMap, fieldOptions.getCover(), BookMetadata::getThumbnailUrl));

        if (refreshOptions.getAllP4() != null) {
            setOtherUnspecifiedMetadata(metadataMap, metadata, refreshOptions.getAllP4());
        }
        if (refreshOptions.getAllP3() != null) {
            setOtherUnspecifiedMetadata(metadataMap, metadata, refreshOptions.getAllP3());
        }
        if (refreshOptions.getAllP2() != null) {
            setOtherUnspecifiedMetadata(metadataMap, metadata, refreshOptions.getAllP2());
        }
        if (refreshOptions.getAllP1() != null) {
            setOtherUnspecifiedMetadata(metadataMap, metadata, refreshOptions.getAllP1());
        }

        return metadata;
    }

    protected void setOtherUnspecifiedMetadata(Map<MetadataProvider, BookMetadata> metadataMap, BookMetadata metadataCombined, MetadataProvider provider) {
        if (metadataMap.containsKey(provider)) {
            BookMetadata metadata = metadataMap.get(provider);
            metadataCombined.setSubtitle(metadata.getSubtitle() != null ? metadata.getSubtitle() : metadataCombined.getSubtitle());
            metadataCombined.setPublisher(metadata.getPublisher() != null ? metadata.getPublisher() : metadataCombined.getPublisher());
            metadataCombined.setPublishedDate(metadata.getPublishedDate() != null ? metadata.getPublishedDate() : metadataCombined.getPublishedDate());
            metadataCombined.setIsbn10(metadata.getIsbn10() != null ? metadata.getIsbn10() : metadataCombined.getIsbn10());
            metadataCombined.setIsbn13(metadata.getIsbn13() != null ? metadata.getIsbn13() : metadataCombined.getIsbn13());
            metadataCombined.setAsin(metadata.getAsin() != null ? metadata.getAsin() : metadataCombined.getAsin());
            metadataCombined.setPageCount(metadata.getPageCount() != null ? metadata.getPageCount() : metadataCombined.getPageCount());
            metadataCombined.setLanguage(metadata.getLanguage() != null ? metadata.getLanguage() : metadataCombined.getLanguage());
            metadataCombined.setGoodreadsRating(metadata.getGoodreadsRating() != null ? metadata.getGoodreadsRating() : metadataCombined.getGoodreadsRating());
            metadataCombined.setGoodreadsReviewCount(metadata.getGoodreadsReviewCount() != null ? metadata.getGoodreadsReviewCount() : metadataCombined.getGoodreadsReviewCount());
            metadataCombined.setAmazonRating(metadata.getAmazonRating() != null ? metadata.getAmazonRating() : metadataCombined.getAmazonRating());
            metadataCombined.setAmazonReviewCount(metadata.getAmazonReviewCount() != null ? metadata.getAmazonReviewCount() : metadataCombined.getAmazonReviewCount());
            metadataCombined.setHardcoverRating(metadata.getHardcoverRating() != null ? metadata.getHardcoverRating() : metadataCombined.getHardcoverRating());
            metadataCombined.setHardcoverReviewCount(metadata.getHardcoverReviewCount() != null ? metadata.getHardcoverReviewCount() : metadataCombined.getHardcoverReviewCount());
            metadataCombined.setPersonalRating(metadata.getPersonalRating() != null ? metadata.getPersonalRating() : metadataCombined.getPersonalRating());
            metadataCombined.setSeriesName(metadata.getSeriesName() != null ? metadata.getSeriesName() : metadataCombined.getSeriesName());
            metadataCombined.setSeriesNumber(metadata.getSeriesNumber() != null ? metadata.getSeriesNumber() : metadataCombined.getSeriesNumber());
            metadataCombined.setSeriesTotal(metadata.getSeriesTotal() != null ? metadata.getSeriesTotal() : metadataCombined.getSeriesTotal());
        }
    }

    @FunctionalInterface
    public interface FieldValueExtractor {
        String extract(BookMetadata metadata);
    }

    @FunctionalInterface
    public interface FieldValueExtractorList {
        Set<String> extract(BookMetadata metadata);
    }


    protected String resolveFieldAsString(Map<MetadataProvider, BookMetadata> metadataMap, MetadataRefreshOptions.FieldProvider fieldProvider, FieldValueExtractor fieldValueExtractor) {
        String value = null;
        if (fieldProvider.getP4() != null && metadataMap.containsKey(fieldProvider.getP4())) {
            String newValue = fieldValueExtractor.extract(metadataMap.get(fieldProvider.getP4()));
            if (newValue != null) value = newValue;
        }
        if (fieldProvider.getP3() != null && metadataMap.containsKey(fieldProvider.getP3())) {
            String newValue = fieldValueExtractor.extract(metadataMap.get(fieldProvider.getP3()));
            if (newValue != null) value = newValue;
        }
        if (fieldProvider.getP2() != null && metadataMap.containsKey(fieldProvider.getP2())) {
            String newValue = fieldValueExtractor.extract(metadataMap.get(fieldProvider.getP2()));
            if (newValue != null) value = newValue;
        }
        if (fieldProvider.getP1() != null && metadataMap.containsKey(fieldProvider.getP1())) {
            String newValue = fieldValueExtractor.extract(metadataMap.get(fieldProvider.getP1()));
            if (newValue != null) value = newValue;
        }
        return value;
    }


    protected Set<String> resolveFieldAsList(Map<MetadataProvider, BookMetadata> metadataMap, MetadataRefreshOptions.FieldProvider fieldProvider, FieldValueExtractorList fieldValueExtractor) {
        Set<String> values = new HashSet<>();
        if (fieldProvider.getP4() != null && metadataMap.containsKey(fieldProvider.getP4())) {
            Set<String> newValues = fieldValueExtractor.extract(metadataMap.get(fieldProvider.getP4()));
            if (newValues != null && !newValues.isEmpty()) values = newValues;
        }
        if (fieldProvider.getP3() != null && metadataMap.containsKey(fieldProvider.getP3())) {
            Set<String> newValues = fieldValueExtractor.extract(metadataMap.get(fieldProvider.getP3()));
            if (newValues != null && !newValues.isEmpty()) values = newValues;
        }
        if (values.isEmpty() && fieldProvider.getP2() != null && metadataMap.containsKey(fieldProvider.getP2())) {
            Set<String> newValues = fieldValueExtractor.extract(metadataMap.get(fieldProvider.getP2()));
            if (newValues != null && !newValues.isEmpty()) values = newValues;
        }
        if (values.isEmpty() && fieldProvider.getP1() != null && metadataMap.containsKey(fieldProvider.getP1())) {
            Set<String> newValues = fieldValueExtractor.extract(metadataMap.get(fieldProvider.getP1()));
            if (newValues != null && !newValues.isEmpty()) values = newValues;
        }
        return values;
    }

    Set<String> getAllCategories(Map<MetadataProvider, BookMetadata> metadataMap, MetadataRefreshOptions.FieldProvider fieldProvider, FieldValueExtractorList fieldValueExtractor) {
        Set<String> uniqueCategories = new HashSet<>();
        if (fieldProvider.getP4() != null && metadataMap.containsKey(fieldProvider.getP4())) {
            Set<String> extracted = fieldValueExtractor.extract(metadataMap.get(fieldProvider.getP4()));
            if (extracted != null) uniqueCategories.addAll(extracted);
        }
        if (fieldProvider.getP3() != null && metadataMap.containsKey(fieldProvider.getP3())) {
            Set<String> extracted = fieldValueExtractor.extract(metadataMap.get(fieldProvider.getP3()));
            if (extracted != null) uniqueCategories.addAll(extracted);
        }
        if (fieldProvider.getP2() != null && metadataMap.containsKey(fieldProvider.getP2())) {
            Set<String> extracted = fieldValueExtractor.extract(metadataMap.get(fieldProvider.getP2()));
            if (extracted != null) uniqueCategories.addAll(extracted);
        }
        if (fieldProvider.getP1() != null && metadataMap.containsKey(fieldProvider.getP1())) {
            Set<String> extracted = fieldValueExtractor.extract(metadataMap.get(fieldProvider.getP1()));
            if (extracted != null) uniqueCategories.addAll(extracted);
        }
        return new HashSet<>(uniqueCategories);
    }

    Set<String> getAllMoods(Map<MetadataProvider, BookMetadata> metadataMap, MetadataRefreshOptions.FieldProvider fieldProvider, FieldValueExtractorList fieldValueExtractor) {
        Set<String> uniqueMoods = new HashSet<>();
        if (fieldProvider.getP4() != null && metadataMap.containsKey(fieldProvider.getP4())) {
            Set<String> extracted = fieldValueExtractor.extract(metadataMap.get(fieldProvider.getP4()));
            if (extracted != null) uniqueMoods.addAll(extracted);
        }
        if (fieldProvider.getP3() != null && metadataMap.containsKey(fieldProvider.getP3())) {
            Set<String> extracted = fieldValueExtractor.extract(metadataMap.get(fieldProvider.getP3()));
            if (extracted != null) uniqueMoods.addAll(extracted);
        }
        if (fieldProvider.getP2() != null && metadataMap.containsKey(fieldProvider.getP2())) {
            Set<String> extracted = fieldValueExtractor.extract(metadataMap.get(fieldProvider.getP2()));
            if (extracted != null) uniqueMoods.addAll(extracted);
        }
        if (fieldProvider.getP1() != null && metadataMap.containsKey(fieldProvider.getP1())) {
            Set<String> extracted = fieldValueExtractor.extract(metadataMap.get(fieldProvider.getP1()));
            if (extracted != null) uniqueMoods.addAll(extracted);
        }
        return new HashSet<>(uniqueMoods);
    }

    Set<String> getAllTags(Map<MetadataProvider, BookMetadata> metadataMap, MetadataRefreshOptions.FieldProvider fieldProvider, FieldValueExtractorList fieldValueExtractor) {
        Set<String> uniqueTags = new HashSet<>();
        if (fieldProvider.getP4() != null && metadataMap.containsKey(fieldProvider.getP4())) {
            Set<String> extracted = fieldValueExtractor.extract(metadataMap.get(fieldProvider.getP4()));
            if (extracted != null) uniqueTags.addAll(extracted);
        }
        if (fieldProvider.getP3() != null && metadataMap.containsKey(fieldProvider.getP3())) {
            Set<String> extracted = fieldValueExtractor.extract(metadataMap.get(fieldProvider.getP3()));
            if (extracted != null) uniqueTags.addAll(extracted);
        }
        if (fieldProvider.getP2() != null && metadataMap.containsKey(fieldProvider.getP2())) {
            Set<String> extracted = fieldValueExtractor.extract(metadataMap.get(fieldProvider.getP2()));
            if (extracted != null) uniqueTags.addAll(extracted);
        }
        if (fieldProvider.getP1() != null && metadataMap.containsKey(fieldProvider.getP1())) {
            Set<String> extracted = fieldValueExtractor.extract(metadataMap.get(fieldProvider.getP1()));
            if (extracted != null) uniqueTags.addAll(extracted);
        }
        return new HashSet<>(uniqueTags);
    }


    protected Set<Long> getBookEntities(MetadataRefreshRequest request) {
        MetadataRefreshRequest.RefreshType refreshType = request.getRefreshType();
        if (refreshType != MetadataRefreshRequest.RefreshType.LIBRARY && refreshType != MetadataRefreshRequest.RefreshType.BOOKS) {
            throw ApiError.INVALID_REFRESH_TYPE.createException();
        }
        return switch (refreshType) {
            case LIBRARY -> {
                LibraryEntity libraryEntity = libraryRepository.findById(request.getLibraryId()).orElseThrow(() -> ApiError.LIBRARY_NOT_FOUND.createException(request.getLibraryId()));
                yield bookRepository.findBookIdsByLibraryId(libraryEntity.getId());
            }
            case BOOKS -> request.getBookIds();
        };
    }
}
