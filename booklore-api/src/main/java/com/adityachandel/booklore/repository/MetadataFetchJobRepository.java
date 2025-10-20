package com.adityachandel.booklore.repository;

import com.adityachandel.booklore.model.entity.MetadataFetchJobEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

@Repository
public interface MetadataFetchJobRepository extends JpaRepository<MetadataFetchJobEntity, String> {

    int deleteAllByCompletedAtBefore(Instant cutoff);

    @Query("SELECT COUNT(m) FROM MetadataFetchJobEntity m")
    long countAll();

    @Query("SELECT DISTINCT t FROM MetadataFetchJobEntity t LEFT JOIN FETCH t.proposals")
    List<MetadataFetchJobEntity> findAllWithProposals();
}
