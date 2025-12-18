package com.adityachandel.booklore.repository;

import com.adityachandel.booklore.model.entity.PdfViewerPreferencesEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PdfViewerPreferencesRepository extends JpaRepository<PdfViewerPreferencesEntity, Long> {

    Optional<PdfViewerPreferencesEntity> findByBookIdAndUserId(long bookId, Long id);

    List<PdfViewerPreferencesEntity> findByBookIdInAndUserId(List<Long> bookIds, Long userId);
}
