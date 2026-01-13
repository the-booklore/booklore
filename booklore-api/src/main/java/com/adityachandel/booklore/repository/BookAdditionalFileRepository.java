package com.adityachandel.booklore.repository;

import com.adityachandel.booklore.model.entity.BookFileEntity;
import com.adityachandel.booklore.model.enums.BookFileType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface BookAdditionalFileRepository extends JpaRepository<BookFileEntity, Long> {

    List<BookFileEntity> findByBookId(Long bookId);

    List<BookFileEntity> findByBookIdAndIsBookFormat(Long bookId, boolean isBookFormat);

    List<BookFileEntity> findByBookIdAndBookType(Long bookId, BookFileType bookType);

    /**
     * Finds a {@link BookFileEntity} by its alternative format current hash.
     * <p>
     * This method queries against the {@code alt_format_current_hash} virtual column, which is indexed
     * and only contains values for files where the {@code additional_file_type} is 'ALTERNATIVE_FORMAT'.
     * This implicitly filters by the file type and provides an efficient lookup.
     *
     * @param altFormatCurrentHash The current hash of the file, which is only considered for alternative format files.
     * @return an {@link Optional} containing the found entity, or an empty {@link Optional} if no match is found.
     */
    Optional<BookFileEntity> findByAltFormatCurrentHash(String altFormatCurrentHash);

    @Query("SELECT bf FROM BookFileEntity bf WHERE bf.book.libraryPath.id = :libraryPathId AND bf.fileSubPath = :fileSubPath AND bf.fileName = :fileName")
    Optional<BookFileEntity> findByLibraryPath_IdAndFileSubPathAndFileName(@Param("libraryPathId") Long libraryPathId,
                                                                                      @Param("fileSubPath") String fileSubPath,
                                                                                      @Param("fileName") String fileName);

    List<BookFileEntity> findByIsBookFormat(boolean isBookFormat);

    List<BookFileEntity> findByBookType(BookFileType bookType);

    @Query("SELECT COUNT(bf) FROM BookFileEntity bf WHERE bf.book.id = :bookId AND bf.isBookFormat = :isBookFormat")
    long countByBookIdAndIsBookFormat(@Param("bookId") Long bookId, @Param("isBookFormat") boolean isBookFormat);

    @Query("SELECT bf FROM BookFileEntity bf WHERE bf.book.library.id = :libraryId")
    List<BookFileEntity> findByLibraryId(@Param("libraryId") Long libraryId);

    @Modifying
    @Query("""
            UPDATE BookFileEntity bf SET
                bf.fileName = :fileName,
                bf.fileSubPath = :fileSubPath
            WHERE bf.id = :bookFileId
            """)
    void updateFileNameAndSubPath(
            @Param("bookFileId") Long bookFileId,
            @Param("fileName") String fileName,
            @Param("fileSubPath") String fileSubPath);
}
