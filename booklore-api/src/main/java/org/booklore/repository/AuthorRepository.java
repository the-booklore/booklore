package org.booklore.repository;

import org.booklore.model.entity.AuthorEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AuthorRepository extends JpaRepository<AuthorEntity, Long> {

    Optional<AuthorEntity> findByName(String name);

    Optional<AuthorEntity> findByNameIgnoreCase(String name);

    @Query("SELECT a FROM AuthorEntity a JOIN a.bookMetadataEntityList bm WHERE bm.bookId = :bookId")
    List<AuthorEntity> findAuthorsByBookId(@Param("bookId") Long bookId);

    Optional<AuthorEntity> findByAsin(String asin);

    @Query("SELECT a, COUNT(bm) FROM AuthorEntity a LEFT JOIN a.bookMetadataEntityList bm GROUP BY a ORDER BY a.name")
    List<Object[]> findAllWithBookCount();
}
