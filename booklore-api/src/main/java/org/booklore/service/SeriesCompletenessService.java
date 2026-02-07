package org.booklore.service;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.booklore.model.entity.LibraryEntity;
import org.booklore.model.entity.SeriesCompletenessEntity;
import org.booklore.repository.LibraryRepository;
import org.booklore.repository.SeriesCompletenessRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;

@Slf4j
@AllArgsConstructor
@Service
public class SeriesCompletenessService {

    private final SeriesCompletenessRepository seriesCompletenessRepository;
    private final LibraryRepository libraryRepository;

    @PersistenceContext
    private EntityManager entityManager;

    /**
     * Calculate series completeness for all libraries
     * This is the main method called by the scheduled task
     */
    @Transactional
    public void calculateAllSeries() {
        log.info("Starting series completeness calculation for all libraries");
        long startTime = System.currentTimeMillis();

        List<LibraryEntity> libraries = libraryRepository.findAll();
        log.info("Found {} libraries to process", libraries.size());

        int totalSeriesProcessed = 0;
        for (LibraryEntity library : libraries) {
            try {
                int seriesCount = calculateLibrarySeries(library.getId());
                totalSeriesProcessed += seriesCount;
                log.info("Processed {} series for library: {} (ID: {})", 
                        seriesCount, library.getName(), library.getId());
            } catch (Exception e) {
                log.error("Error calculating series completeness for library: {} (ID: {})", 
                        library.getName(), library.getId(), e);
            }
        }

        long duration = System.currentTimeMillis() - startTime;
        log.info("Completed series completeness calculation. Total series processed: {} in {}ms", 
                totalSeriesProcessed, duration);
    }

    /**
     * Calculate series completeness for a specific library
     * @param libraryId the library ID to process
     * @return number of series processed
     */
    @Transactional
    public int calculateLibrarySeries(Long libraryId) {
        log.debug("Calculating series completeness for library ID: {}", libraryId);
        long startTime = System.currentTimeMillis();

        // Get all distinct series names with their book counts and min/max series numbers
        // Also count distinct integer positions (FLOOR of series numbers) to handle decimal series
        // Group only by normalized name to match frontend behavior (case-insensitive grouping)
        List<Object[]> seriesData = entityManager.createQuery(
                "SELECT " +
                "  LOWER(TRIM(bm.seriesName)), " +              // 0: normalized series name
                "  MAX(bm.seriesName), " +                       // 1: representative original series name
                "  COUNT(b.id), " +                              // 2: book count
                "  MIN(bm.seriesNumber), " +                     // 3: min series number
                "  MAX(bm.seriesNumber), " +                     // 4: max series number
                "  COUNT(DISTINCT FLOOR(bm.seriesNumber)) " +    // 5: distinct integer positions
                "FROM BookEntity b " +
                "JOIN b.metadata bm " +
                "WHERE b.library.id = :libraryId " +
                "  AND bm.seriesName IS NOT NULL " +
                "  AND TRIM(bm.seriesName) != '' " +
                "  AND bm.seriesNumber IS NOT NULL " +
                "GROUP BY LOWER(TRIM(bm.seriesName))",
                Object[].class)
                .setParameter("libraryId", libraryId)
                .getResultList();

        log.debug("Found {} series in library ID: {}", seriesData.size(), libraryId);

        // Delete existing records for this library to ensure clean state
        seriesCompletenessRepository.deleteByLibraryId(libraryId);
        entityManager.flush();

        // Process in batches for better performance
        final int BATCH_SIZE = 100;
        int processedCount = 0;
        List<SeriesCompletenessEntity> batch = new ArrayList<>(BATCH_SIZE);

        for (Object[] row : seriesData) {
            String seriesNameNormalized = (String) row[0];
            String seriesName = (String) row[1];
            Long bookCount = (Long) row[2];
            Float minSeriesNumber = (Float) row[3];
            Float maxSeriesNumber = (Float) row[4];
            Long distinctPositions = (Long) row[5]; // Count of distinct FLOOR(seriesNumber)

            // Skip if we don't have enough data
            if (minSeriesNumber == null || maxSeriesNumber == null || distinctPositions == null) {
                continue;
            }

            // Calculate completeness: series can start at 0 or 1 and have no gaps
            // A series is complete only if min is approximately 0.0 or 1.0 and has all positions
            // For integer series (0, 1, 2, 3): starts at 0, has 4 positions out of 4 expected → complete
            // For integer series (1, 2, 3): starts at 1, has 3 positions out of 3 expected → complete
            // For decimal series (1, 1.5, 2, 2.5, 3): starts at 1, has 3 positions out of 3 → complete
            // For series starting later (1.5, 2, 3): starts at 1.5 ≠ 0.0 or 1.0 → incomplete
            // For series starting later (3, 4, 5): starts at 3 ≠ 0.0 or 1.0 → incomplete
            boolean startsAtZeroOrOne = Math.abs(minSeriesNumber - 0.0) < 0.01 || Math.abs(minSeriesNumber - 1.0) < 0.01;
            // Calculate expected positions based on the range from min to max
            int expectedPositions = (int) Math.floor(maxSeriesNumber) - (int) Math.floor(minSeriesNumber) + 1;
            boolean hasAllPositions = distinctPositions == expectedPositions;
            
            // Series is complete only if it starts at 0 or 1 and has all integer positions covered
            boolean isComplete = startsAtZeroOrOne && hasAllPositions;
            boolean isIncomplete = !isComplete;

            SeriesCompletenessEntity entity = SeriesCompletenessEntity.builder()
                    .libraryId(libraryId)
                    .seriesName(seriesName)
                    .seriesNameNormalized(seriesNameNormalized)
                    .bookCount(bookCount.intValue())
                    .minSeriesNumber(minSeriesNumber.doubleValue())
                    .maxSeriesNumber(maxSeriesNumber.doubleValue())
                    .isComplete(isComplete)
                    .isIncomplete(isIncomplete)
                    .lastCalculatedAt(Instant.now())
                    .build();

            batch.add(entity);

            if (batch.size() >= BATCH_SIZE) {
                seriesCompletenessRepository.saveAll(batch);
                entityManager.flush();
                entityManager.clear();
                processedCount += batch.size();
                log.debug("Saved batch of {} series (total: {})", batch.size(), processedCount);
                batch.clear();
            }
        }

        // Save remaining batch
        if (!batch.isEmpty()) {
            seriesCompletenessRepository.saveAll(batch);
            entityManager.flush();
            processedCount += batch.size();
        }

        long duration = System.currentTimeMillis() - startTime;
        log.debug("Completed series calculation for library ID: {} - {} series in {}ms", 
                libraryId, processedCount, duration);

        return processedCount;
    }

    /**
     * Update series completeness for a specific book's series
     * Called when book metadata changes
     * @param bookId the book ID that was updated
     */
    @Transactional
    public void updateSeriesForBook(Long bookId) {
        log.debug("Updating series completeness for book ID: {}", bookId);

        // Get the book's library and series information
        List<Object[]> bookInfo = entityManager.createQuery(
                "SELECT b.library.id, bm.seriesName " +
                "FROM BookEntity b " +
                "JOIN b.metadata bm " +
                "WHERE b.id = :bookId " +
                "  AND bm.seriesName IS NOT NULL " +
                "  AND TRIM(bm.seriesName) != ''",
                Object[].class)
                .setParameter("bookId", bookId)
                .getResultList();

        if (bookInfo.isEmpty()) {
            log.debug("Book ID: {} has no series information, skipping update", bookId);
            return;
        }

        Long libraryId = (Long) bookInfo.get(0)[0];
        String seriesName = (String) bookInfo.get(0)[1];
        String seriesNameNormalized = seriesName.toLowerCase().trim();

        updateSingleSeries(libraryId, seriesNameNormalized);
    }

    /**
     * Update a single series completeness record
     * @param libraryId the library ID
     * @param seriesNameNormalized the normalized series name
     */
    @Transactional
    public void updateSingleSeries(Long libraryId, String seriesNameNormalized) {
        log.debug("Updating series: {} in library ID: {}", seriesNameNormalized, libraryId);

        // Get series statistics
        // Group only by normalized name to match frontend behavior
        List<Object[]> seriesData = entityManager.createQuery(
                "SELECT " +
                "  MAX(bm.seriesName), " +                       // 0: representative original series name
                "  COUNT(b.id), " +                              // 1: book count
                "  MIN(bm.seriesNumber), " +                     // 2: min series number
                "  MAX(bm.seriesNumber), " +                     // 3: max series number
                "  COUNT(DISTINCT FLOOR(bm.seriesNumber)) " +    // 4: distinct integer positions
                "FROM BookEntity b " +
                "JOIN b.metadata bm " +
                "WHERE b.library.id = :libraryId " +
                "  AND LOWER(TRIM(bm.seriesName)) = :seriesNameNormalized " +
                "  AND bm.seriesNumber IS NOT NULL",
                Object[].class)
                .setParameter("libraryId", libraryId)
                .setParameter("seriesNameNormalized", seriesNameNormalized)
                .getResultList();

        if (seriesData.isEmpty()) {
            // Series no longer exists or has no books with series numbers
            seriesCompletenessRepository.deleteByLibraryIdAndSeriesNameNormalized(libraryId, seriesNameNormalized);
            log.debug("Deleted series completeness record for: {} (no longer has books)", seriesNameNormalized);
            return;
        }

        Object[] row = seriesData.get(0);
        String seriesName = (String) row[0];
        Long bookCount = (Long) row[1];
        Float minSeriesNumber = (Float) row[2];
        Float maxSeriesNumber = (Float) row[3];
        Long distinctPositions = (Long) row[4]; // Count of distinct FLOOR(seriesNumber)

        if (minSeriesNumber == null || maxSeriesNumber == null || distinctPositions == null) {
            log.warn("Series {} has null min/max series numbers or distinct positions, skipping", seriesName);
            return;
        }

        // Calculate completeness: series can start at 0 or 1 and have no gaps
        boolean startsAtZeroOrOne = Math.abs(minSeriesNumber - 0.0) < 0.01 || Math.abs(minSeriesNumber - 1.0) < 0.01;
        // Calculate expected positions based on the range from min to max
        int expectedPositions = (int) Math.floor(maxSeriesNumber) - (int) Math.floor(minSeriesNumber) + 1;
        boolean hasAllPositions = distinctPositions == expectedPositions;
        boolean isComplete = startsAtZeroOrOne && hasAllPositions;
        boolean isIncomplete = !isComplete;

        // Find or create the entity
        Optional<SeriesCompletenessEntity> existingOpt = 
                seriesCompletenessRepository.findByLibraryIdAndSeriesNameNormalized(libraryId, seriesNameNormalized);

        SeriesCompletenessEntity entity;
        if (existingOpt.isPresent()) {
            entity = existingOpt.get();
            entity.setSeriesName(seriesName);
            entity.setBookCount(bookCount.intValue());
            entity.setMinSeriesNumber(minSeriesNumber.doubleValue());
            entity.setMaxSeriesNumber(maxSeriesNumber.doubleValue());
            entity.setIsComplete(isComplete);
            entity.setIsIncomplete(isIncomplete);
            entity.setLastCalculatedAt(Instant.now());
        } else {
            entity = SeriesCompletenessEntity.builder()
                    .libraryId(libraryId)
                    .seriesName(seriesName)
                    .seriesNameNormalized(seriesNameNormalized)
                    .bookCount(bookCount.intValue())
                    .minSeriesNumber(minSeriesNumber.doubleValue())
                    .maxSeriesNumber(maxSeriesNumber.doubleValue())
                    .isComplete(isComplete)
                    .isIncomplete(isIncomplete)
                    .lastCalculatedAt(Instant.now())
                    .build();
        }

        seriesCompletenessRepository.save(entity);
        log.debug("Updated series completeness for: {} - Complete: {}, Positions: {}/{}", 
                seriesName, isComplete, distinctPositions, expectedPositions);
    }

    /**
     * Check if a series is incomplete
     * This is used by the OPDS filter
     * @param libraryId the library ID
     * @param seriesName the series name (will be normalized)
     * @return true if the series is incomplete, false if complete or not found
     */
    public boolean isSeriesIncomplete(Long libraryId, String seriesName) {
        if (seriesName == null || seriesName.trim().isEmpty()) {
            return false;
        }

        String seriesNameNormalized = seriesName.toLowerCase().trim();
        Optional<Boolean> result = seriesCompletenessRepository.isSeriesIncomplete(libraryId, seriesNameNormalized);
        
        return result.orElse(false);
    }

    /**
     * Get statistics for a library
     * @param libraryId the library ID
     * @return map with statistics (total_series, incomplete_series, complete_series)
     */
    public Map<String, Long> getLibraryStatistics(Long libraryId) {
        long totalSeries = seriesCompletenessRepository.countByLibraryId(libraryId);
        long incompleteSeries = seriesCompletenessRepository.countByLibraryIdAndIsIncompleteTrue(libraryId);
        long completeSeries = totalSeries - incompleteSeries;

        Map<String, Long> stats = new HashMap<>();
        stats.put("total_series", totalSeries);
        stats.put("incomplete_series", incompleteSeries);
        stats.put("complete_series", completeSeries);

        return stats;
    }

    /**
     * Get all incomplete series for a library
     * @param libraryId the library ID
     * @return list of series completeness entities
     */
    public List<SeriesCompletenessEntity> getIncompleteSeries(Long libraryId) {
        return seriesCompletenessRepository.findAllByLibraryIdAndIsIncompleteTrue(libraryId);
    }
}
