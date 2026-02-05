package org.booklore.repository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.booklore.model.entity.BookLoreUserEntity;
import org.booklore.model.enums.PermissionType;
import org.booklore.repository.custom.UserRepositoryCustom;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Set;

@Repository
public class UserRepositoryImpl implements UserRepositoryCustom {

    @PersistenceContext
    private EntityManager entityManager;

    @Override
    public List<BookLoreUserEntity> findByPermissions_PermissionTypeIn(Set<PermissionType> permissionTypes) {
        if (permissionTypes == null || permissionTypes.isEmpty()) {
            return List.of();
        }

        StringBuilder queryBuilder = new StringBuilder();
        queryBuilder.append("SELECT DISTINCT u FROM BookLoreUserEntity u JOIN u.permissions p WHERE ");

        boolean firstCondition = true;
        for (PermissionType permissionType : permissionTypes) {
            if (!firstCondition) {
                queryBuilder.append(" OR ");
            }
            queryBuilder.append(getPermissionCheckClause(permissionType));
            firstCondition = false;
        }

        var query = entityManager.createQuery(queryBuilder.toString(), BookLoreUserEntity.class);
        return query.getResultList();
    }

    private String getPermissionCheckClause(PermissionType permissionType) {
        switch (permissionType) {
            case ADMIN:
                return "p.permissionAdmin = true";
            case UPLOAD:
                return "p.permissionUpload = true";
            case DOWNLOAD:
                return "p.permissionDownload = true";
            case EDIT_METADATA:
                return "p.permissionEditMetadata = true";
            case MANAGE_LIBRARY:
                return "p.permissionManageLibrary = true";
            case EMAIL_BOOK:
                return "p.permissionEmailBook = true";
            case DELETE_BOOK:
                return "p.permissionDeleteBook = true";
            case SYNC_KOREADER:
                return "p.permissionSyncKoreader = true";
            case SYNC_KOBO:
                return "p.permissionSyncKobo = true";
            case ACCESS_OPDS:
                return "p.permissionAccessOpds = true";
            case MANAGE_METADATA_CONFIG:
                return "p.permissionManageMetadataConfig = true";
            case ACCESS_BOOKDROP:
                return "p.permissionAccessBookdrop = true";
            case ACCESS_LIBRARY_STATS:
                return "p.permissionAccessLibraryStats = true";
            case ACCESS_USER_STATS:
                return "p.permissionAccessUserStats = true";
            case ACCESS_TASK_MANAGER:
                return "p.permissionAccessTaskManager = true";
            case MANAGE_GLOBAL_PREFERENCES:
                return "p.permissionManageGlobalPreferences = true";
            case MANAGE_ICONS:
                return "p.permissionManageIcons = true";
            case DEMO_USER:
                return "p.permissionDemoUser = true";
            default:
                throw new IllegalArgumentException("Unknown permission type: " + permissionType);
        }
    }
}