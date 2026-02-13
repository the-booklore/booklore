package org.booklore.repository;

import org.booklore.model.entity.ComicLocationEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

public interface ComicLocationRepository extends JpaRepository<ComicLocationEntity, Long> {

    Optional<ComicLocationEntity> findByName(String name);

    @Modifying(flushAutomatically = true)
    @Query(value = "DELETE FROM comic_location WHERE id NOT IN (SELECT DISTINCT location_id FROM comic_metadata_location_mapping)", nativeQuery = true)
    void deleteOrphaned();
}
