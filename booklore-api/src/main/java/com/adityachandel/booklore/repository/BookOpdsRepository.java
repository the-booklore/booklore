package com.adityachandel.booklore.repository;

import com.adityachandel.booklore.model.entity.BookEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
public interface BookOpdsRepository extends JpaRepository<BookEntity, Long>, JpaSpecificationExecutor<BookEntity> {

    // ============================================
    // ALL BOOKS - Two Query Pattern
    // ============================================

    @Query("SELECT b.id FROM BookEntity b WHERE (b.deleted IS NULL OR b.deleted = false)")
    Page<Long> findBookIds(Pageable pageable);

    @EntityGraph(attributePaths = {"metadata", "additionalFiles", "shelves"})
    @Query("SELECT b FROM BookEntity b WHERE b.id IN :ids AND (b.deleted IS NULL OR b.deleted = false)")
    List<BookEntity> findAllWithMetadataByIds(@Param("ids") Collection<Long> ids);

    // ============================================
    // RECENT BOOKS - Two Query Pattern
    // ============================================

    @Query("SELECT b.id FROM BookEntity b WHERE (b.deleted IS NULL OR b.deleted = false) ORDER BY b.addedOn DESC")
    Page<Long> findRecentBookIds(Pageable pageable);

    // Uses same findAllWithMetadataByIds for second query

    // ============================================
    // BOOKS BY LIBRARY IDs - Two Query Pattern
    // ============================================

    @Query("SELECT b.id FROM BookEntity b WHERE b.library.id IN :libraryIds AND (b.deleted IS NULL OR b.deleted = false)")
    Page<Long> findBookIdsByLibraryIds(@Param("libraryIds") Collection<Long> libraryIds, Pageable pageable);

    @EntityGraph(attributePaths = {"metadata", "additionalFiles", "shelves"})
    @Query("SELECT b FROM BookEntity b WHERE b.id IN :ids AND b.library.id IN :libraryIds AND (b.deleted IS NULL OR b.deleted = false)")
    List<BookEntity> findAllWithMetadataByIdsAndLibraryIds(@Param("ids") Collection<Long> ids, @Param("libraryIds") Collection<Long> libraryIds);

    // ============================================
    // RECENT BOOKS BY LIBRARY IDs - Two Query Pattern
    // ============================================

    @Query("SELECT b.id FROM BookEntity b WHERE b.library.id IN :libraryIds AND (b.deleted IS NULL OR b.deleted = false) ORDER BY b.addedOn DESC")
    Page<Long> findRecentBookIdsByLibraryIds(@Param("libraryIds") Collection<Long> libraryIds, Pageable pageable);

    // Uses findAllWithMetadataByIdsAndLibraryIds for second query

    // ============================================
    // BOOKS BY SHELF ID - Two Query Pattern
    // ============================================

    @Query("SELECT DISTINCT b.id FROM BookEntity b JOIN b.shelves s WHERE s.id = :shelfId AND (b.deleted IS NULL OR b.deleted = false)")
    Page<Long> findBookIdsByShelfId(@Param("shelfId") Long shelfId, Pageable pageable);

    @EntityGraph(attributePaths = {"metadata", "additionalFiles", "shelves"})
    @Query("SELECT DISTINCT b FROM BookEntity b JOIN b.shelves s WHERE b.id IN :ids AND s.id = :shelfId AND (b.deleted IS NULL OR b.deleted = false)")
    List<BookEntity> findAllWithMetadataByIdsAndShelfId(@Param("ids") Collection<Long> ids, @Param("shelfId") Long shelfId);

    // ============================================
    // SEARCH BY METADATA - Two Query Pattern
    // ============================================

    @Query("""
            SELECT DISTINCT b.id FROM BookEntity b
            LEFT JOIN b.metadata m
            LEFT JOIN m.authors a
            WHERE (b.deleted IS NULL OR b.deleted = false) AND (
                  LOWER(m.title) LIKE LOWER(CONCAT('%', :text, '%'))
               OR LOWER(m.subtitle) LIKE LOWER(CONCAT('%', :text, '%'))
               OR LOWER(m.seriesName) LIKE LOWER(CONCAT('%', :text, '%'))
               OR LOWER(a.name) LIKE LOWER(CONCAT('%', :text, '%'))
            )
            """)
    Page<Long> findBookIdsByMetadataSearch(@Param("text") String text, Pageable pageable);

    @EntityGraph(attributePaths = {"metadata", "metadata.authors", "metadata.categories", "additionalFiles", "shelves"})
    @Query("SELECT DISTINCT b FROM BookEntity b WHERE b.id IN :ids AND (b.deleted IS NULL OR b.deleted = false)")
    List<BookEntity> findAllWithFullMetadataByIds(@Param("ids") Collection<Long> ids);

    // ============================================
    // SEARCH BY METADATA IN LIBRARIES - Two Query Pattern
    // ============================================

    @Query("""
            SELECT DISTINCT b.id FROM BookEntity b
            LEFT JOIN b.metadata m
            LEFT JOIN m.authors a
            WHERE (b.deleted IS NULL OR b.deleted = false)
              AND b.library.id IN :libraryIds
              AND (
                  LOWER(m.title) LIKE LOWER(CONCAT('%', :text, '%'))
               OR LOWER(m.subtitle) LIKE LOWER(CONCAT('%', :text, '%'))
               OR LOWER(m.seriesName) LIKE LOWER(CONCAT('%', :text, '%'))
               OR LOWER(a.name) LIKE LOWER(CONCAT('%', :text, '%'))
              )
            """)
    Page<Long> findBookIdsByMetadataSearchAndLibraryIds(@Param("text") String text, @Param("libraryIds") Collection<Long> libraryIds, Pageable pageable);

    @EntityGraph(attributePaths = {"metadata", "metadata.authors", "metadata.categories", "additionalFiles", "shelves"})
    @Query("SELECT DISTINCT b FROM BookEntity b WHERE b.id IN :ids AND b.library.id IN :libraryIds AND (b.deleted IS NULL OR b.deleted = false)")
    List<BookEntity> findAllWithFullMetadataByIdsAndLibraryIds(@Param("ids") Collection<Long> ids, @Param("libraryIds") Collection<Long> libraryIds);

    // ============================================
    // RANDOM BOOKS - "Surprise Me" Feed
    // ============================================

    @Query(value = "SELECT b.id FROM BookEntity b WHERE (b.deleted IS NULL OR b.deleted = false) ORDER BY function('RAND')", nativeQuery = false)
    List<Long> findRandomBookIds();

    @Query(value = "SELECT b.id FROM BookEntity b WHERE b.library.id IN :libraryIds AND (b.deleted IS NULL OR b.deleted = false) ORDER BY function('RAND')", nativeQuery = false)
    List<Long> findRandomBookIdsByLibraryIds(@Param("libraryIds") Collection<Long> libraryIds);
}
