package org.booklore.repository;

import org.booklore.model.entity.SeriesCompletenessEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Repository
public interface SeriesCompletenessRepository extends JpaRepository<SeriesCompletenessEntity, Long> {

    /**
     * Find series completeness by library and normalized series name
     */
    Optional<SeriesCompletenessEntity> findByLibraryIdAndSeriesNameNormalized(Long libraryId, String seriesNameNormalized);

    /**
     * Find all series completeness records for a specific library
     */
    List<SeriesCompletenessEntity> findAllByLibraryId(Long libraryId);

    /**
     * Find all incomplete series across all libraries
     */
    List<SeriesCompletenessEntity> findAllByIsIncompleteTrue();

    /**
     * Find all incomplete series for a specific library
     */
    List<SeriesCompletenessEntity> findAllByLibraryIdAndIsIncompleteTrue(Long libraryId);

    /**
     * Delete all series completeness records for a specific library
     */
    @Modifying
    @Transactional
    void deleteByLibraryId(Long libraryId);

    /**
     * Delete a specific series completeness record
     */
    @Modifying
    @Transactional
    void deleteByLibraryIdAndSeriesNameNormalized(Long libraryId, String seriesNameNormalized);

    /**
     * Count total series in a library
     */
    long countByLibraryId(Long libraryId);

    /**
     * Count incomplete series in a library
     */
    long countByLibraryIdAndIsIncompleteTrue(Long libraryId);

    /**
     * Check if a series exists and is incomplete
     */
    @Query("SELECT sc.isIncomplete FROM SeriesCompletenessEntity sc WHERE sc.libraryId = :libraryId AND sc.seriesNameNormalized = :seriesNameNormalized")
    Optional<Boolean> isSeriesIncomplete(@Param("libraryId") Long libraryId, @Param("seriesNameNormalized") String seriesNameNormalized);
}
