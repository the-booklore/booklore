package com.adityachandel.booklore.repository;

import com.adityachandel.booklore.model.entity.BookLoreUserEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<BookLoreUserEntity, Long> {

    Optional<BookLoreUserEntity> findByUsername(String username);

    Optional<BookLoreUserEntity> findByEmail(String email);

    Optional<BookLoreUserEntity> findById(Long id);

    List<BookLoreUserEntity> findAllByLibraries_Id(Long libraryId);
}
