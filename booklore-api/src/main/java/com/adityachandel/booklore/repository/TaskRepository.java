package com.adityachandel.booklore.repository;

import com.adityachandel.booklore.model.entity.TaskEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TaskRepository extends JpaRepository<TaskEntity, String> {

    @Query("SELECT t FROM TaskEntity t WHERE t.createdAt = " +
           "(SELECT MAX(t2.createdAt) FROM TaskEntity t2 WHERE t2.type = t.type) " +
           "ORDER BY t.createdAt DESC")
    List<TaskEntity> findLatestTaskForEachType();
}
