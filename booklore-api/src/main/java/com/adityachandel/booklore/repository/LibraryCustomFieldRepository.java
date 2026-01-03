package com.adityachandel.booklore.repository;

import com.adityachandel.booklore.model.entity.LibraryCustomFieldEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface LibraryCustomFieldRepository extends JpaRepository<LibraryCustomFieldEntity, Long> {

    List<LibraryCustomFieldEntity> findAllByLibrary_IdOrderByNameAsc(Long libraryId);

    Optional<LibraryCustomFieldEntity> findByLibrary_IdAndName(Long libraryId, String name);

    boolean existsByLibrary_IdAndName(Long libraryId, String name);
}
