package com.adityachandel.booklore.repository;

import com.adityachandel.booklore.model.entity.BookCustomFieldValueEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface BookCustomFieldValueRepository extends JpaRepository<BookCustomFieldValueEntity, Long> {

    List<BookCustomFieldValueEntity> findAllByBook_Id(Long bookId);

    Optional<BookCustomFieldValueEntity> findByBook_IdAndCustomField_Id(Long bookId, Long customFieldId);

    void deleteByBook_IdAndCustomField_Id(Long bookId, Long customFieldId);
}
