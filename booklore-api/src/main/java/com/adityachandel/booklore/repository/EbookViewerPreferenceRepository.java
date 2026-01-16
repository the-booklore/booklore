package com.adityachandel.booklore.repository;

import com.adityachandel.booklore.model.entity.EbookViewerPreferenceEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface EbookViewerPreferenceRepository extends JpaRepository<EbookViewerPreferenceEntity, Long> {

    Optional<EbookViewerPreferenceEntity> findByBookIdAndUserId(Long bookId, Long userId);

}

