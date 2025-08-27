package com.adityachandel.booklore.repository;

import com.adityachandel.booklore.model.entity.KoboReadingStateEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface KoboReadingStateRepository extends JpaRepository<KoboReadingStateEntity, Long> {
    Optional<KoboReadingStateEntity> findByEntitlementId(String entitlementId);
}