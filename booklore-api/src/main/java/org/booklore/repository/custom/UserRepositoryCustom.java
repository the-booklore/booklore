package com.adityachandel.booklore.repository.custom;

import com.adityachandel.booklore.model.entity.BookLoreUserEntity;
import com.adityachandel.booklore.model.enums.PermissionType;

import java.util.List;
import java.util.Set;

public interface UserRepositoryCustom {
    List<BookLoreUserEntity> findByPermissions_PermissionTypeIn(Set<PermissionType> permissionTypes);
}