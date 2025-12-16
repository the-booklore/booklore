package com.adityachandel.booklore.service.user;

import com.adityachandel.booklore.model.dto.settings.OidcAutoProvisionDetails;
import com.adityachandel.booklore.model.dto.settings.OidcProviderDetails;
import com.adityachandel.booklore.model.entity.BookLoreUserEntity;
import com.adityachandel.booklore.model.entity.LibraryEntity;
import com.adityachandel.booklore.model.entity.UserPermissionsEntity;
import com.adityachandel.booklore.model.enums.ProvisioningMethod;
import com.adityachandel.booklore.repository.LibraryRepository;
import com.adityachandel.booklore.repository.UserRepository;
import com.adityachandel.booklore.service.appsettings.AppSettingService;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserPersistenceService {

    // Use Caffeine cache for locks with expiration to prevent stale locks
    private static final Cache<String, Object> USER_CREATION_LOCKS = Caffeine.newBuilder()
            .expireAfterAccess(Duration.ofMinutes(5))
            .build();
            
    public static final String OIDC_LOCKED_PASSWORD_HASH = "$OIDC_LOCKED$";

    public static boolean hasLockedOidcPassword(BookLoreUserEntity user) {
        return OIDC_LOCKED_PASSWORD_HASH.equals(user.getPasswordHash());
    }

    private final UserRepository userRepository;
    private final LibraryRepository libraryRepository;
    private final UserDefaultsService userDefaultsService;
    private final AppSettingService appSettingService;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public BookLoreUserEntity provisionOidcUserInternal(String oidcSubject, String username, String email, String name,
                                                        List<String> groups, OidcAutoProvisionDetails oidcAutoProvisionDetails) {
        // Use a per-username lock to avoid race conditions when provisioning the same username concurrently
        Object lock = USER_CREATION_LOCKS.get(username, k -> new Object());
        synchronized (lock) {
            // Double-check inside lock - check by subject first, then username
            if (oidcSubject != null) {
                Optional<BookLoreUserEntity> bySubject = userRepository.findByOidcSubject(oidcSubject);
                if (bySubject.isPresent()) {
                    log.debug("Found existing user by OIDC subject (created by concurrent thread)");
                    return bySubject.get();
                }
            }

            Optional<BookLoreUserEntity> existing = userRepository.findByUsername(username);
            if (existing.isPresent()) {
                log.debug("Found existing user for username: {} (created by concurrent thread)", username);
                // Link oidcSubject if missing
                BookLoreUserEntity user = existing.get();
                if (oidcSubject != null && user.getOidcSubject() == null) {
                    user.setOidcSubject(oidcSubject);
                    userRepository.save(user);
                }
                return user;
            }

            // Check by email as well to avoid unique constraint violations
            if (email != null) {
                Optional<BookLoreUserEntity> byEmail = userRepository.findByEmail(email);
                if (byEmail.isPresent()) {
                    log.debug("Found existing user by email: {} (created by concurrent thread or manual)", email);
                    BookLoreUserEntity user = byEmail.get();
                    // Link oidcSubject if missing
                    if (oidcSubject != null && user.getOidcSubject() == null) {
                        user.setOidcSubject(oidcSubject);
                        userRepository.save(user);
                    }
                    return user;
                }
            }

            log.debug("Creating new OIDC user for username: {}", username);

            // Create new user
            BookLoreUserEntity user = new BookLoreUserEntity();
            user.setUsername(username);
            user.setEmail(email);
            user.setName(name);
            user.setOidcSubject(oidcSubject); // Store the immutable subject identifier
            // Set an invalid password hash that explicitly fails bcrypt validation
            user.setPasswordHash(OIDC_LOCKED_PASSWORD_HASH);
            user.setDefaultPassword(true);
            user.setProvisioningMethod(ProvisioningMethod.OIDC);

            // Assign default libraries if specified
            if (oidcAutoProvisionDetails != null) {
                List<Long> defaultLibraryIds = oidcAutoProvisionDetails.getDefaultLibraryIds();
                if (defaultLibraryIds != null && !defaultLibraryIds.isEmpty()) {
                    List<LibraryEntity> libraries = libraryRepository.findAllById(defaultLibraryIds);
                    user.setLibraries(new ArrayList<>(libraries));
                }
            }

            UserPermissionsEntity perms = new UserPermissionsEntity();
            perms.setUser(user);

            // OIDC users can set a local password for OPDS and backup authentication
            perms.setPermissionChangePassword(true);

            if (oidcAutoProvisionDetails != null) {
                List<String> defaultPermissions = oidcAutoProvisionDetails.getDefaultPermissions();
                if (defaultPermissions != null) {
                    perms.setPermissionUpload(defaultPermissions.contains("permissionUpload"));
                    perms.setPermissionDownload(defaultPermissions.contains("permissionDownload"));
                    perms.setPermissionEditMetadata(defaultPermissions.contains("permissionEditMetadata"));
                    perms.setPermissionManipulateLibrary(defaultPermissions.contains("permissionManipulateLibrary"));
                    perms.setPermissionEmailBook(defaultPermissions.contains("permissionEmailBook"));
                    perms.setPermissionDeleteBook(defaultPermissions.contains("permissionDeleteBook"));
                    perms.setPermissionAccessOpds(defaultPermissions.contains("permissionAccessOpds"));
                    perms.setPermissionSyncKoreader(defaultPermissions.contains("permissionSyncKoreader"));
                    perms.setPermissionSyncKobo(defaultPermissions.contains("permissionSyncKobo"));
                    perms.setPermissionAdmin(defaultPermissions.contains("permissionAdmin"));
                }

                // Check if user should be admin based on OIDC admin group membership
                boolean isAdmin = checkAdminGroupMembership(groups, oidcAutoProvisionDetails);
                if (isAdmin) {
                    perms.setPermissionAdmin(true);
                    log.info("Granting admin role to user '{}' based on OIDC group membership", username);
                    // Admins get access to all libraries
                    List<LibraryEntity> allLibraries = libraryRepository.findAll();
                    user.setLibraries(new ArrayList<>(allLibraries));
                }
            }

            user.setPermissions(perms);

            try {
                BookLoreUserEntity saved = userRepository.saveAndFlush(user);
                userDefaultsService.addDefaultShelves(saved);
                userDefaultsService.addDefaultSettings(saved);

                // Apply group-based permissions if groups are provided
                if (groups != null && !groups.isEmpty() && oidcAutoProvisionDetails != null) {
                    syncUserPermissionsFromGroups(saved, groups, oidcAutoProvisionDetails);
                }

                return saved;
            } catch (org.springframework.dao.DataIntegrityViolationException e) {
                log.warn("Race condition detected during user creation for '{}'. Retrying...", username);
                throw new DuplicateUserRetryException(username);
            }
        }
    }

    /**
     * Syncs user permissions based on OIDC groups/roles claim.
     * Uses the roleMappings configuration from OidcProviderDetails.
     *
     * This method strictly enforces the state from the IdP for managed permissions.
     * If the IdP says they don't have the role, BookLore will revoke it,
     * BUT ONLY if that permission is mapped to at least one role in the configuration.
     * This prevents accidental revocation of manually granted permissions that are not managed by OIDC.
     */
    @Transactional
    public void syncUserPermissionsFromGroups(BookLoreUserEntity user, List<String> groups, OidcAutoProvisionDetails provisionDetails) {
        if (provisionDetails == null) {
            return;
        }

        boolean updated = syncAdminGroupMembership(user, groups, provisionDetails);

        if (provisionDetails.isSyncPermissions()) {
            updated |= syncDetailedPermissions(user, groups);
        }

        if (updated) {
            userRepository.save(user);
        }
    }

    private boolean syncAdminGroupMembership(BookLoreUserEntity user, List<String> groups, OidcAutoProvisionDetails provisionDetails) {
        UserPermissionsEntity perms = user.getPermissions();
        if (perms == null) {
            log.warn("User {} has no permissions entity, skipping group sync", user.getUsername());
            return false;
        }

        boolean isAdmin = checkAdminGroupMembership(groups, provisionDetails);
        if (isAdmin && !perms.isPermissionAdmin()) {
            perms.setPermissionAdmin(true);
            log.info("Granting admin role to user '{}' based on OIDC admin group membership", user.getUsername());
            List<LibraryEntity> allLibraries = libraryRepository.findAll();
            Set<LibraryEntity> currentLibraries = new HashSet<>(user.getLibraries());
            currentLibraries.addAll(allLibraries);
            user.setLibraries(new ArrayList<>(currentLibraries));
            return true;
        }
        return false;
    }

    private boolean syncDetailedPermissions(BookLoreUserEntity user, List<String> groups) {
        UserPermissionsEntity perms = user.getPermissions();
        if (perms == null) return false;

        OidcProviderDetails providerDetails = appSettingService.getAppSettings().getOidcProviderDetails();
        if (providerDetails == null || providerDetails.getRoleMappings() == null || providerDetails.getRoleMappings().isEmpty()) {
            log.debug("No role mappings configured, skipping group-based permission sync");
            return false;
        }

        Map<String, List<String>> roleMappings = providerDetails.getRoleMappings();
        boolean updated = false;

        // 1. Calculate the "Target State" (All permissions the user SHOULD have)
        Set<String> targetPermissions = new HashSet<>();
        for (String group : groups) {
            List<String> mappedPermissions = roleMappings.get(group);
            if (mappedPermissions != null) {
                targetPermissions.addAll(mappedPermissions);
            }
        }

        // 2. Identify "Managed" Permissions (Permissions that are actually controlled by OIDC)
        Set<String> managedPermissions = roleMappings.values().stream()
                .flatMap(List::stream)
                .collect(java.util.stream.Collectors.toSet());

        // Helper function to update permission state safely
        updated |= updatePermission(targetPermissions, managedPermissions, "permissionAdmin", perms::setPermissionAdmin, perms::isPermissionAdmin);
        updated |= updatePermission(targetPermissions, managedPermissions, "permissionUpload", perms::setPermissionUpload, perms::isPermissionUpload);
        updated |= updatePermission(targetPermissions, managedPermissions, "permissionDownload", perms::setPermissionDownload, perms::isPermissionDownload);
        updated |= updatePermission(targetPermissions, managedPermissions, "permissionEditMetadata", perms::setPermissionEditMetadata, perms::isPermissionEditMetadata);
        updated |= updatePermission(targetPermissions, managedPermissions, "permissionManipulateLibrary", perms::setPermissionManipulateLibrary, perms::isPermissionManipulateLibrary);
        updated |= updatePermission(targetPermissions, managedPermissions, "permissionEmailBook", perms::setPermissionEmailBook, perms::isPermissionEmailBook);
        updated |= updatePermission(targetPermissions, managedPermissions, "permissionDeleteBook", perms::setPermissionDeleteBook, perms::isPermissionDeleteBook);
        updated |= updatePermission(targetPermissions, managedPermissions, "permissionAccessOpds", perms::setPermissionAccessOpds, perms::isPermissionAccessOpds);
        updated |= updatePermission(targetPermissions, managedPermissions, "permissionSyncKoreader", perms::setPermissionSyncKoreader, perms::isPermissionSyncKoreader);
        updated |= updatePermission(targetPermissions, managedPermissions, "permissionSyncKobo", perms::setPermissionSyncKobo, perms::isPermissionSyncKobo);

        // 3. Handle Library Access (Admins always get all libraries)
        if (targetPermissions.contains("permissionAdmin") && !perms.isPermissionAdmin()) {
            List<LibraryEntity> allLibraries = libraryRepository.findAll();
            Set<LibraryEntity> currentLibraries = new HashSet<>(user.getLibraries());
            currentLibraries.addAll(allLibraries);
            user.setLibraries(new ArrayList<>(currentLibraries));
            updated = true;
        }

        if (updated) {
            log.info("Synced permissions for user '{}'. Target: {}", user.getUsername(), targetPermissions);
        }
        return updated;
    }

    // Robust helper to handle the "Grant vs Revoke" logic
    private boolean updatePermission(Set<String> targetPermissions,
                                     Set<String> managedPermissions,
                                     String permName,
                                     Consumer<Boolean> setter,
                                     BooleanSupplier getter) {
        boolean shouldHave = targetPermissions.contains(permName);
        boolean currentlyHas = getter.getAsBoolean();
        boolean isManaged = managedPermissions.contains(permName);

        if (shouldHave && !currentlyHas) {
            setter.accept(true); // Grant
            return true;
        } else if (!shouldHave && currentlyHas && isManaged) {
            setter.accept(false); // Revoke (Only if it's a managed permission)
            log.debug("Revoking permission '{}' as it is no longer granted by any OIDC group", permName);
            return true;
        }
        return false;
    }

    /**
     * Checks if the user should be granted admin role based on OIDC group membership.
     * This is a simpler alternative to the full roleMappings configuration.
     *
     * @param groups The list of groups from the OIDC token
     * @param provisionDetails The auto-provisioning configuration
     * @return true if the user should be granted admin role
     */
    private boolean checkAdminGroupMembership(List<String> groups, OidcAutoProvisionDetails provisionDetails) {
        if (groups == null || groups.isEmpty() || provisionDetails == null) {
            return false;
        }

        String adminGroup = provisionDetails.getAdminGroup();
        if (adminGroup == null || adminGroup.trim().isEmpty()) {
            return false;
        }

        boolean isAdmin = groups.contains(adminGroup.trim());
        if (isAdmin) {
            log.debug("User has admin group '{}' in OIDC groups: {}", adminGroup, groups);
        }
        return isAdmin;
    }
}
