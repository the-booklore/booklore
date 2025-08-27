package com.adityachandel.booklore.repository;

import com.adityachandel.booklore.model.entity.ShelfEntity;
import jakarta.validation.constraints.NotBlank;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Repository
public interface ShelfRepository extends JpaRepository<ShelfEntity, Long> {

    boolean existsByUserIdAndName(Long id, String name);

    List<ShelfEntity> findByUserId(Long id);

    Optional<ShelfEntity> findByUserIdAndName(Long id, String name);
}
