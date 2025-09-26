package com.adityachandel.booklore.repository;

import com.adityachandel.booklore.model.entity.LibraryPathEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface LibraryPathRepository extends JpaRepository<LibraryPathEntity, Long> {
    Optional<LibraryPathEntity> findByLibraryIdAndPath(Long libraryId, String path);
}
