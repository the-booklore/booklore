package com.adityachandel.booklore.repository;

import com.adityachandel.booklore.model.entity.UserBookProgressEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.Set;

@Repository
public interface UserBookProgressRepository extends JpaRepository<UserBookProgressEntity, Long> {

    Optional<UserBookProgressEntity> findByUserIdAndBookId(Long userId, Long bookId);

    List<UserBookProgressEntity> findByUserIdAndBookIdIn(Long userId, Set<Long> bookIds);

    @Query("""
        SELECT ubp FROM UserBookProgressEntity ubp
        WHERE ubp.user.id = :userId
          AND ubp.book.id IN (
              SELECT ksb.bookId FROM KoboSnapshotBookEntity ksb
              WHERE ksb.snapshot.id = :snapshotId
          )
          AND (
              (ubp.readStatusModifiedTime IS NOT NULL AND (
                  ubp.koboStatusSentTime IS NULL
                  OR ubp.readStatusModifiedTime > ubp.koboStatusSentTime
              ))
              OR
              (ubp.koboProgressReceivedTime IS NOT NULL AND (
                  ubp.koboProgressSentTime IS NULL
                  OR ubp.koboProgressReceivedTime > ubp.koboProgressSentTime
              ))
          )
    """)
    List<UserBookProgressEntity> findAllBooksNeedingKoboSync(
            @Param("userId") Long userId,
            @Param("snapshotId") String snapshotId
    );
}
