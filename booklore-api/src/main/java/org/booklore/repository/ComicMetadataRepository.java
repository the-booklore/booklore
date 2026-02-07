package org.booklore.repository;

import org.booklore.model.entity.ComicMetadataEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ComicMetadataRepository extends JpaRepository<ComicMetadataEntity, Long> {

    @Query("SELECT c FROM ComicMetadataEntity c WHERE c.bookId IN :bookIds")
    List<ComicMetadataEntity> findAllByBookIds(@Param("bookIds") List<Long> bookIds);

    List<ComicMetadataEntity> findAllByStoryArcIgnoreCase(String storyArc);

    List<ComicMetadataEntity> findAllByVolumeNameIgnoreCase(String volumeName);

    @Query("SELECT c FROM ComicMetadataEntity c WHERE c.penciller LIKE %:name% OR c.inker LIKE %:name% OR c.colorist LIKE %:name% OR c.letterer LIKE %:name% OR c.coverArtist LIKE %:name% OR c.editor LIKE %:name%")
    List<ComicMetadataEntity> findAllByCreatorName(@Param("name") String name);
}
