package com.adityachandel.booklore.repository;

import com.adityachandel.booklore.model.entity.TaskEntity;
import com.adityachandel.booklore.model.enums.TaskType;
import com.adityachandel.booklore.task.TaskStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TaskRepository extends JpaRepository<TaskEntity, String> {

    @Query("SELECT t FROM TaskEntity t WHERE t.createdAt = " +
           "(SELECT MAX(t2.createdAt) FROM TaskEntity t2 WHERE t2.type = t.type) " +
           "ORDER BY t.createdAt DESC")
    List<TaskEntity> findLatestTaskForEachType();

    @Query("SELECT t FROM TaskEntity t WHERE t.status IN ('IN_PROGRESS', 'ACCEPTED') ORDER BY t.createdAt DESC")
    List<TaskEntity> findRunningTasks();

    @Query("SELECT t FROM TaskEntity t ORDER BY t.createdAt DESC")
    Page<TaskEntity> findAllTasksOrderByCreatedAtDesc(Pageable pageable);

    @Query("SELECT t FROM TaskEntity t WHERE t.status = :status ORDER BY t.createdAt DESC")
    Page<TaskEntity> findTasksByStatus(@Param("status") TaskStatus status, Pageable pageable);

    @Query("SELECT t FROM TaskEntity t WHERE t.type = :type ORDER BY t.createdAt DESC")
    Page<TaskEntity> findTasksByType(@Param("type") TaskType type, Pageable pageable);

    @Query("SELECT t FROM TaskEntity t WHERE t.status = :status AND t.type = :type ORDER BY t.createdAt DESC")
    Page<TaskEntity> findTasksByStatusAndType(@Param("status") TaskStatus status, @Param("type") TaskType type, Pageable pageable);

    Optional<TaskEntity> findById(String id);
}
