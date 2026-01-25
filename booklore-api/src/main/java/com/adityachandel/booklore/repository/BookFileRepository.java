package com.adityachandel.booklore.repository;

import com.adityachandel.booklore.model.entity.BookFileEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface BookFileRepository extends JpaRepository<BookFileEntity, Long> {

    @Query("""
            SELECT bf FROM BookFileEntity bf
            WHERE bf.book.libraryPath.id = :libraryPathId
            AND bf.fileSubPath = :fileSubPath
            AND bf.fileName = :fileName
            """)
    Optional<BookFileEntity> findByLibraryPathIdAndFileSubPathAndFileName(
            @Param("libraryPathId") Long libraryPathId,
            @Param("fileSubPath") String fileSubPath,
            @Param("fileName") String fileName);

    @Query("SELECT COUNT(bf) FROM BookFileEntity bf WHERE bf.book.id = :bookId")
    long countByBookId(@Param("bookId") Long bookId);
}
