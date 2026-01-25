package com.adityachandel.booklore.repository;

import com.adityachandel.booklore.model.entity.BookFileEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface BookFileRepository extends JpaRepository<BookFileEntity, Long> {
}
