package com.adityachandel.booklore.repository;


import com.adityachandel.booklore.model.entity.KoboSnapshotBookEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface KoboSnapshotBookRepository extends JpaRepository<KoboSnapshotBookEntity, Long> {

    Page<KoboSnapshotBookEntity> findBySnapshot_IdAndSyncedFalse(String snapshotId, Pageable pageable);

    @Modifying
    @Query("UPDATE KoboSnapshotBookEntity b SET b.synced = true WHERE b.snapshot.id = :snapshotId AND b.bookId IN :bookIds")
    void markBooksSynced(@Param("snapshotId") String snapshotId, @Param("bookIds") List<Long> bookIds);

    @Query("""
        SELECT curr
        FROM KoboSnapshotBookEntity curr
        WHERE curr.snapshot.id = :currSnapshotId
          AND curr.bookId IN (
              SELECT prev.bookId
              FROM KoboSnapshotBookEntity prev
              WHERE prev.snapshot.id = :prevSnapshotId
          )
    """)
    List<KoboSnapshotBookEntity> findExistingBooksBetweenSnapshots(
            @Param("prevSnapshotId") String prevSnapshotId,
            @Param("currSnapshotId") String currSnapshotId
    );

    @Query("""
            SELECT curr
            FROM KoboSnapshotBookEntity curr
            WHERE curr.snapshot.id = :currSnapshotId
              AND (:unsyncedOnly = false OR curr.synced = false)
              AND curr.bookId NOT IN (
                    SELECT prev.bookId
                    FROM KoboSnapshotBookEntity prev
                    WHERE prev.snapshot.id = :prevSnapshotId
              )
            """)
    Page<KoboSnapshotBookEntity> findNewlyAddedBooks(
            @Param("prevSnapshotId") String prevSnapshotId,
            @Param("currSnapshotId") String currSnapshotId,
            @Param("unsyncedOnly") boolean unsyncedOnly,
            Pageable pageable
    );

    @Query("""
        SELECT prev
        FROM KoboSnapshotBookEntity prev
        WHERE prev.snapshot.id = :prevSnapshotId
          AND prev.bookId NOT IN (
              SELECT curr.bookId
              FROM KoboSnapshotBookEntity curr
              WHERE curr.snapshot.id = :currSnapshotId
          )
          AND prev.bookId NOT IN (
              SELECT p.bookIdSynced
              FROM KoboDeletedBookProgressEntity p
              WHERE p.snapshotId = :currSnapshotId
          )
    """)
    Page<KoboSnapshotBookEntity> findRemovedBooks(
            @Param("prevSnapshotId") String prevSnapshotId,
            @Param("currSnapshotId") String currSnapshotId,
            Pageable pageable
    );
}
