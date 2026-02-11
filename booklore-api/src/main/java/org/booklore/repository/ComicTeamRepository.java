package org.booklore.repository;

import org.booklore.model.entity.ComicTeamEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

public interface ComicTeamRepository extends JpaRepository<ComicTeamEntity, Long> {

    Optional<ComicTeamEntity> findByName(String name);

    @Modifying(flushAutomatically = true)
    @Query(value = "DELETE FROM comic_team WHERE id NOT IN (SELECT DISTINCT team_id FROM comic_metadata_team_mapping)", nativeQuery = true)
    void deleteOrphaned();
}
