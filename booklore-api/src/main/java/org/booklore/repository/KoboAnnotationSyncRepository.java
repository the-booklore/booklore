package org.booklore.repository;

import org.booklore.model.entity.KoboAnnotationSyncEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface KoboAnnotationSyncRepository extends JpaRepository<KoboAnnotationSyncEntity, Long> {

    Optional<KoboAnnotationSyncEntity> findByUserIdAndAnnotationId(Long userId, String annotationId);

    List<KoboAnnotationSyncEntity> findByUserIdAndAnnotationIdIn(Long userId, List<String> annotationIds);

    List<KoboAnnotationSyncEntity> findByUserIdAndBookId(Long userId, Long bookId);
}
