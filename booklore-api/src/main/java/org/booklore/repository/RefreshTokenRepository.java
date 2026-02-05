package org.booklore.repository;

import org.booklore.model.entity.BookLoreUserEntity;
import org.booklore.model.entity.RefreshTokenEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface RefreshTokenRepository extends JpaRepository<RefreshTokenEntity, Long> {
    Optional<RefreshTokenEntity> findByToken(String token);
    void deleteByUser(BookLoreUserEntity user);
}
