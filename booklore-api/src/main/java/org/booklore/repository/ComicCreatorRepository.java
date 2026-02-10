package org.booklore.repository;

import org.booklore.model.entity.ComicCreatorEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

public interface ComicCreatorRepository extends JpaRepository<ComicCreatorEntity, Long> {

    Optional<ComicCreatorEntity> findByName(String name);

    @Modifying(flushAutomatically = true)
    @Query(value = "DELETE FROM comic_creator WHERE id NOT IN (SELECT DISTINCT creator_id FROM comic_metadata_creator_mapping)", nativeQuery = true)
    void deleteOrphaned();
}
