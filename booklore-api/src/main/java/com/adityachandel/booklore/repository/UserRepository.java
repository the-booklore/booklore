package com.adityachandel.booklore.repository;

import com.adityachandel.booklore.model.entity.BookLoreUserEntity;
import com.adityachandel.booklore.model.enums.PermissionType;
import com.adityachandel.booklore.repository.custom.UserRepositoryCustom;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.Set;

@Repository
public interface UserRepository extends JpaRepository<BookLoreUserEntity, Long>, UserRepositoryCustom {

    Optional<BookLoreUserEntity> findByUsername(String username);

    Optional<BookLoreUserEntity> findByEmail(String email);

    Optional<BookLoreUserEntity> findById(Long id);

    /**
     * Find user by their immutable OIDC subject identifier.
     * This is the preferred method for OIDC user lookup as the 'sub' claim never changes.
     */
    Optional<BookLoreUserEntity> findByOidcSubject(String oidcSubject);

    List<BookLoreUserEntity> findAllByLibraries_Id(Long libraryId);

    List<BookLoreUserEntity> findByPermissions_PermissionTypeIn(Set<PermissionType> permissionTypes);
}
