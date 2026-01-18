package com.adityachandel.booklore.repository;

import com.adityachandel.booklore.model.entity.*;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

public interface BookMetadataRepository extends JpaRepository<BookMetadataEntity, Long> {

    @Query("SELECT m FROM BookMetadataEntity m WHERE m.bookId IN :bookIds")
    List<BookMetadataEntity> getMetadataForBookIds(@Param("bookIds") List<Long> bookIds);

    @Modifying
    @Transactional
    @Query("UPDATE BookMetadataEntity m SET m.coverUpdatedOn = :timestamp WHERE m.bookId = :bookId")
    void updateCoverTimestamp(@Param("bookId") Long bookId, @Param("timestamp") Instant timestamp);

    List<BookMetadataEntity> findAllByAuthorsContaining(AuthorEntity author);

    List<BookMetadataEntity> findAllByCategoriesContaining(CategoryEntity category);

    List<BookMetadataEntity> findAllByMoodsContaining(MoodEntity mood);

    List<BookMetadataEntity> findAllByTagsContaining(TagEntity tag);

    List<BookMetadataEntity> findAllBySeriesNameIgnoreCase(String seriesName);

    List<BookMetadataEntity> findAllByPublisherIgnoreCase(String publisher);

    List<BookMetadataEntity> findAllByLanguageIgnoreCase(String language);

    @Query("""
            SELECT DISTINCT m.seriesName FROM BookMetadataEntity m
            JOIN m.book b
            WHERE m.seriesName IS NOT NULL 
              AND m.seriesName != ''
              AND m.seriesNumber IS NOT NULL
              AND (b.deleted IS NULL OR b.deleted = false)
            ORDER BY m.seriesName
            """)
    List<String> findAllDistinctSeriesNames();

    @Query("""
            SELECT m FROM BookMetadataEntity m
            JOIN m.book b
            WHERE m.seriesName = :seriesName
              AND m.seriesNumber IS NOT NULL
              AND (b.deleted IS NULL OR b.deleted = false)
            ORDER BY m.seriesNumber
            """)
    List<BookMetadataEntity> findBooksInSeriesByName(@Param("seriesName") String seriesName);
}
