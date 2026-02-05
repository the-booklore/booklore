package org.booklore.repository.custom;

import org.booklore.model.entity.BookLoreUserEntity;
import org.booklore.model.enums.PermissionType;

import java.util.List;
import java.util.Set;

public interface UserRepositoryCustom {
    List<BookLoreUserEntity> findByPermissions_PermissionTypeIn(Set<PermissionType> permissionTypes);
}