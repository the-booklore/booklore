package com.adityachandel.booklore.repository;

import com.adityachandel.booklore.model.entity.EpubViewerPreferencesEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface EpubViewerPreferencesRepository extends JpaRepository<EpubViewerPreferencesEntity, Long> {

    Optional<EpubViewerPreferencesEntity> findByBookIdAndUserId(Long bookId, Long userId);

    List<EpubViewerPreferencesEntity> findByBookIdInAndUserId(List<Long> bookIds, Long userId);
}
