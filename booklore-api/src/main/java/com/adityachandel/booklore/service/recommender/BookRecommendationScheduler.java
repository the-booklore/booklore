package com.adityachandel.booklore.service.recommender;

import com.adityachandel.booklore.model.dto.BookRecommendationLite;
import com.adityachandel.booklore.model.entity.BookEntity;
import com.adityachandel.booklore.model.entity.BookMetadataEntity;
import com.adityachandel.booklore.service.appsettings.AppSettingService;
import com.adityachandel.booklore.service.BookQueryService;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class BookRecommendationScheduler {

    private final BookQueryService bookQueryService;
    private final AppSettingService appSettingService;
    private final BookVectorService vectorService;

    private static final int RECOMMENDATION_LIMIT = 25;

    @Scheduled(cron = "0 0 2 * * *")
    @Transactional
    public void updateAllSimilarBooks() {
        if (!appSettingService.getAppSettings().isSimilarBookRecommendation()) {
            log.info("Similar book recommendations are disabled. Skipping scheduled task.");
            return;
        }
        long startTime = System.currentTimeMillis();
        log.info("Scheduled task 'updateAllSimilarBooks' started at: {}. Current timestamp: {}", startTime, startTime);

        List<BookEntity> allBooks = bookQueryService.getAllFullBookEntities();

        Map<Long, double[]> embeddings = new HashMap<>();
        List<BookEntity> booksToUpdate = new ArrayList<>();

        for (BookEntity book : allBooks) {
            double[] embedding = vectorService.generateEmbedding(book);
            embeddings.put(book.getId(), embedding);

            if (book.getMetadata() != null) {
                String embeddingJson = vectorService.serializeVector(embedding);
                if (!Objects.equals(book.getMetadata().getEmbeddingVector(), embeddingJson)) {
                    book.getMetadata().setEmbeddingVector(embeddingJson);
                    book.getMetadata().setEmbeddingUpdatedAt(Instant.now());
                }
            }
        }

        for (BookEntity targetBook : allBooks) {
            try {
                double[] targetVector = embeddings.get(targetBook.getId());
                if (targetVector == null) continue;

                String targetSeries = Optional.ofNullable(targetBook.getMetadata())
                        .map(BookMetadataEntity::getSeriesName)
                        .map(String::toLowerCase)
                        .orElse(null);

                List<BookVectorService.ScoredBook> candidates = allBooks.stream()
                        .filter(candidate -> !candidate.getId().equals(targetBook.getId()))
                        .filter(candidate -> {
                            String candidateSeries = Optional.ofNullable(candidate.getMetadata())
                                    .map(BookMetadataEntity::getSeriesName)
                                    .map(String::toLowerCase)
                                    .orElse(null);
                            return targetSeries == null || !targetSeries.equals(candidateSeries);
                        })
                        .map(candidate -> {
                            double[] candidateVector = embeddings.get(candidate.getId());
                            double similarity = vectorService.cosineSimilarity(targetVector, candidateVector);
                            return new BookVectorService.ScoredBook(candidate.getId(), similarity);
                        })
                        .filter(scored -> scored.getScore() > 0.1)
                        .collect(Collectors.toList());

                List<BookVectorService.ScoredBook> topSimilar = vectorService.findTopKSimilar(
                        targetVector,
                        candidates,
                        RECOMMENDATION_LIMIT
                );

                Set<BookRecommendationLite> recommendations = topSimilar.stream()
                        .map(scored -> new BookRecommendationLite(scored.getBookId(), scored.getScore()))
                        .collect(Collectors.toSet());

                targetBook.setSimilarBooksJson(recommendations);
                booksToUpdate.add(targetBook);

            } catch (Exception e) {
                log.error("Error updating similar books for book ID {}: {}", targetBook.getId(), e.getMessage(), e);
            }
        }

        bookQueryService.saveAll(booksToUpdate);

        long endTime = System.currentTimeMillis();
        log.info("Completed scheduled task 'updateAllSimilarBooks' at: {}. Duration: {} ms", endTime, endTime - startTime);
    }
}