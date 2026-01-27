package com.adityachandel.booklore.repository;

import com.adityachandel.booklore.model.entity.UserBookFileProgressEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserBookFileProgressRepository extends JpaRepository<UserBookFileProgressEntity, Long> {

    Optional<UserBookFileProgressEntity> findByUserIdAndBookFileId(Long userId, Long bookFileId);

    @Query("""
        SELECT ubfp FROM UserBookFileProgressEntity ubfp
        WHERE ubfp.user.id = :userId
          AND ubfp.bookFile.book.id = :bookId
    """)
    List<UserBookFileProgressEntity> findByUserIdAndBookFileBookId(
            @Param("userId") Long userId,
            @Param("bookId") Long bookId
    );

    @Query("""
        SELECT ubfp FROM UserBookFileProgressEntity ubfp
        WHERE ubfp.user.id = :userId
          AND ubfp.bookFile.book.id = :bookId
        ORDER BY ubfp.lastReadTime DESC
        LIMIT 1
    """)
    Optional<UserBookFileProgressEntity> findMostRecentByUserIdAndBookId(
            @Param("userId") Long userId,
            @Param("bookId") Long bookId
    );

    @Query("""
        SELECT ubfp FROM UserBookFileProgressEntity ubfp
        WHERE ubfp.user.id = :userId
          AND ubfp.bookFile.book.id IN :bookIds
    """)
    List<UserBookFileProgressEntity> findByUserIdAndBookFileBookIdIn(
            @Param("userId") Long userId,
            @Param("bookIds") Iterable<Long> bookIds
    );
}
