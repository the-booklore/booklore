package com.adityachandel.booklore.service.user;

import com.adityachandel.booklore.config.AppProperties;
import com.adityachandel.booklore.exception.ApiError;
import com.adityachandel.booklore.model.dto.UserCreateRequest;
import com.adityachandel.booklore.model.dto.request.InitialUserRequest;
import com.adityachandel.booklore.model.dto.settings.OidcAutoProvisionDetails;
import com.adityachandel.booklore.model.entity.*;
import com.adityachandel.booklore.model.enums.*;
import com.adityachandel.booklore.repository.LibraryRepository;
import com.adityachandel.booklore.repository.UserRepository;
import com.adityachandel.booklore.service.appsettings.AppSettingService;
import org.springframework.dao.DataIntegrityViolationException;
import jakarta.transaction.Transactional;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.RandomStringUtils;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import java.util.concurrent.ConcurrentHashMap;

import java.util.*;
import java.util.regex.Pattern;

@Slf4j
@Service
@AllArgsConstructor
public class UserProvisioningService {

    private static final ConcurrentHashMap<String, Object> USER_CREATION_LOCKS = new ConcurrentHashMap<>();


    private static final Pattern WHITESPACE_PATTERN = Pattern.compile("\\s+");
    private final AppProperties appProperties;
    private final UserRepository userRepository;
    private final LibraryRepository libraryRepository;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
    private final UserDefaultsService userDefaultsService;
    private final AppSettingService appSettingService;

    public boolean isInitialUserAlreadyProvisioned() {
        return userRepository.count() > 0;
    }

    @Transactional
    public void provisionInitialUser(InitialUserRequest request) {
        BookLoreUserEntity user = new BookLoreUserEntity();
        user.setUsername(request.getUsername());
        user.setEmail(request.getEmail());
        user.setName(request.getName());
        user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        user.setDefaultPassword(false);
        user.setProvisioningMethod(ProvisioningMethod.LOCAL);

        UserPermissionsEntity perms = new UserPermissionsEntity();
        perms.setPermissionAdmin(true);
        perms.setPermissionUpload(true);
        perms.setPermissionDownload(true);
        perms.setPermissionEditMetadata(true);
        perms.setPermissionManipulateLibrary(true);
        perms.setPermissionEmailBook(true);
        perms.setPermissionDeleteBook(true);
        perms.setPermissionAccessOpds(true);
        perms.setPermissionSyncKoreader(true);
        perms.setPermissionSyncKobo(true);

        user.setPermissions(perms);
        createUser(user);
    }

    @Transactional
    public void provisionInternalUser(UserCreateRequest request) {
        Optional<BookLoreUserEntity> existingUser = userRepository.findByUsername(request.getUsername());
        if (existingUser.isPresent()) {
            throw ApiError.USERNAME_ALREADY_TAKEN.createException(request.getUsername());
        }

        BookLoreUserEntity user = new BookLoreUserEntity();
        user.setUsername(request.getUsername());
        user.setDefaultPassword(true);
        user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        user.setName(request.getName());
        user.setEmail(request.getEmail());
        user.setProvisioningMethod(ProvisioningMethod.LOCAL);

        UserPermissionsEntity permissions = new UserPermissionsEntity();
        permissions.setUser(user);
        permissions.setPermissionUpload(request.isPermissionUpload());
        permissions.setPermissionDownload(request.isPermissionDownload());
        permissions.setPermissionEditMetadata(request.isPermissionEditMetadata());
        permissions.setPermissionManipulateLibrary(request.isPermissionManipulateLibrary());
        permissions.setPermissionEmailBook(request.isPermissionEmailBook());
        permissions.setPermissionDeleteBook(request.isPermissionDeleteBook());
        permissions.setPermissionAccessOpds(request.isPermissionAccessOpds());
        permissions.setPermissionSyncKoreader(request.isPermissionSyncKoreader());
        permissions.setPermissionSyncKobo(request.isPermissionSyncKobo());
        permissions.setPermissionAdmin(request.isPermissionAdmin());
        user.setPermissions(permissions);

        if (request.getSelectedLibraries() != null && !request.getSelectedLibraries().isEmpty()) {
            List<LibraryEntity> libraries = libraryRepository.findAllById(request.getSelectedLibraries());
            user.setLibraries(new ArrayList<>(libraries));
        }

        createUser(user);
    }

    @Transactional
    public BookLoreUserEntity provisionOidcUser(String username, String email, String name,
                                                OidcAutoProvisionDetails oidcAutoProvisionDetails) {
        log.debug("Attempting to provision OIDC user: username={}, email={}, name={}", username, email, name);
        // Use a per-username lock to avoid race conditions when provisioning the same username concurrently
        Object lock = USER_CREATION_LOCKS.computeIfAbsent(username, k -> new Object());
        synchronized (lock) {
            try {
                // Try to find existing first (re-check inside lock)
                Optional<BookLoreUserEntity> existing = userRepository.findByUsername(username);
                if (existing.isPresent()) {
                    log.debug("Found existing user for username: {}", username);
                    return existing.get();
                }
                
                log.debug("Creating new OIDC user for username: {}", username);

                // Create new user
                BookLoreUserEntity user = new BookLoreUserEntity();
                user.setUsername(username);
                user.setEmail(email);
                user.setName(name);
                // Set an invalid password hash that explicitly fails bcrypt validation
                // Format: "!OIDC_LOCKED:" prefix makes it invalid for bcrypt, preventing any local auth attempts
                user.setPasswordHash("!OIDC_LOCKED:" + UUID.randomUUID());
                user.setDefaultPassword(false);
                user.setProvisioningMethod(ProvisioningMethod.OIDC);

                // Assign default libraries if specified
                List<Long> defaultLibraryIds = oidcAutoProvisionDetails.getDefaultLibraryIds();
                if (defaultLibraryIds != null && !defaultLibraryIds.isEmpty()) {
                    List<LibraryEntity> libraries = libraryRepository.findAllById(defaultLibraryIds);
                    user.setLibraries(new ArrayList<>(libraries));
                }

                UserPermissionsEntity perms = new UserPermissionsEntity();
                perms.setUser(user);

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

                user.setPermissions(perms);

                try {
                    BookLoreUserEntity saved = userRepository.saveAndFlush(user);
                    userDefaultsService.addDefaultShelves(saved);
                    userDefaultsService.addDefaultSettings(saved);
                    log.debug("Successfully created OIDC user: id={}, username={}", saved.getId(), saved.getUsername());
                    return saved;
                } catch (DataIntegrityViolationException e) {
                    if (e.getMessage() != null && e.getMessage().contains("uk_users_username")) {
                        log.debug("Race condition on user creation for {}, fetching existing", username);
                        return userRepository.findByUsername(username)
                                .orElseThrow(() -> new IllegalStateException("User not found after constraint violation"));
                    }
                    // Email conflict or other issue - rethrow
                    throw e;
                }
            } finally {
                // remove the lock to avoid memory leak; use conditional remove
                USER_CREATION_LOCKS.remove(username, lock);
            }
        }
    }

    /**
     * Create and persist a remote-provisioned user based on incoming headers.
     * This is the preferred (non-deprecated) entry point for remote provisioning.
     */
    @Transactional
    public BookLoreUserEntity provisionRemoteUserFromHeaders(String name, String username, String email, String groups) {
        boolean isAdmin = false;
        if (groups != null && appProperties.getRemoteAuth().getAdminGroup() != null) {
            String groupsContent = groups.trim();
            if (groupsContent.length() >= 2 && groupsContent.charAt(0) == '[' && groupsContent.charAt(groupsContent.length() - 1) == ']') {
                groupsContent = groupsContent.substring(1, groupsContent.length() - 1);
            }
            List<String> groupsList = Arrays.asList(WHITESPACE_PATTERN.split(groupsContent));
            isAdmin = groupsList.contains(appProperties.getRemoteAuth().getAdminGroup());
            log.debug("Remote-Auth: user {} will be admin: {}", username, isAdmin);
        }

        BookLoreUserEntity user = new BookLoreUserEntity();
        user.setUsername(username);
        user.setName(name != null ? name : username);
        user.setEmail(email);
        user.setDefaultPassword(false);
        user.setProvisioningMethod(ProvisioningMethod.REMOTE);
        user.setPasswordHash("RemoteUser_" + RandomStringUtils.secure().nextAlphanumeric(32));

        OidcAutoProvisionDetails oidcAutoProvisionDetails = appSettingService.getAppSettings().getOidcAutoProvisionDetails();

        UserPermissionsEntity permissions = new UserPermissionsEntity();
        permissions.setUser(user);

        if (oidcAutoProvisionDetails != null && oidcAutoProvisionDetails.getDefaultPermissions() != null) {
            List<String> defaultPermissions = oidcAutoProvisionDetails.getDefaultPermissions();
            permissions.setPermissionUpload(defaultPermissions.contains("permissionUpload"));
            permissions.setPermissionDownload(defaultPermissions.contains("permissionDownload"));
            permissions.setPermissionEditMetadata(defaultPermissions.contains("permissionEditMetadata"));
            permissions.setPermissionManipulateLibrary(defaultPermissions.contains("permissionManipulateLibrary"));
            permissions.setPermissionEmailBook(defaultPermissions.contains("permissionEmailBook"));
            permissions.setPermissionDeleteBook(defaultPermissions.contains("permissionDeleteBook"));
            permissions.setPermissionAccessOpds(defaultPermissions.contains("permissionAccessOpds"));
            permissions.setPermissionSyncKoreader(defaultPermissions.contains("permissionSyncKoreader"));
            permissions.setPermissionSyncKobo(defaultPermissions.contains("permissionSyncKobo"));
        } else {
            permissions.setPermissionUpload(false);
            permissions.setPermissionDownload(false);
            permissions.setPermissionEditMetadata(false);
            permissions.setPermissionManipulateLibrary(false);
            permissions.setPermissionEmailBook(false);
            permissions.setPermissionAccessOpds(false);
            permissions.setPermissionDeleteBook(false);
            permissions.setPermissionSyncKoreader(false);
            permissions.setPermissionSyncKobo(false);
        }

        permissions.setPermissionAdmin(isAdmin);
        user.setPermissions(permissions);

        if (isAdmin) {
            List<LibraryEntity> libraries = libraryRepository.findAll();
            user.setLibraries(new ArrayList<>(libraries));
        } else if (oidcAutoProvisionDetails != null && oidcAutoProvisionDetails.getDefaultLibraryIds() != null && !oidcAutoProvisionDetails.getDefaultLibraryIds().isEmpty()) {
            List<LibraryEntity> libraries = libraryRepository.findAllById(oidcAutoProvisionDetails.getDefaultLibraryIds());
            user.setLibraries(new ArrayList<>(libraries));
        }

        return createUser(user);
    }

    protected BookLoreUserEntity createUser(BookLoreUserEntity user) {
        BookLoreUserEntity save = userRepository.save(user);
        userDefaultsService.addDefaultShelves(save);
        userDefaultsService.addDefaultSettings(save);
        return save;
    }
}
