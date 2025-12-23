package com.adityachandel.booklore.service.user;

import com.adityachandel.booklore.config.AppProperties;
import com.adityachandel.booklore.config.security.service.DynamicOidcJwtProcessor;
import com.adityachandel.booklore.exception.ApiError;
import com.adityachandel.booklore.exception.OidcProvisioningException;
import com.adityachandel.booklore.model.dto.BookLoreUser;
import com.adityachandel.booklore.model.dto.UserCreateRequest;
import com.adityachandel.booklore.model.dto.request.InitialUserRequest;
import com.adityachandel.booklore.model.dto.settings.OidcAutoProvisionDetails;
import com.adityachandel.booklore.model.dto.settings.OidcProviderDetails;
import com.adityachandel.booklore.model.entity.*;
import com.adityachandel.booklore.model.enums.*;
import com.adityachandel.booklore.repository.LibraryRepository;
import com.adityachandel.booklore.repository.UserRepository;
import com.adityachandel.booklore.service.appsettings.AppSettingService;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jose.proc.BadJOSEException;

import org.springframework.transaction.annotation.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.RandomStringUtils;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.Duration;


import java.util.*;
import java.util.regex.Pattern;

@Slf4j
@Service
@lombok.RequiredArgsConstructor
public class UserProvisioningService {

    private static final Pattern REGEX = Pattern.compile("[^a-zA-Z0-9@._-]");
    private static final Pattern GROUPS_SPLIT_PATTERN = Pattern.compile("[,\\s]+");

    // Cache for OIDC user lookups to reduce DB calls on repeated token validation
    // Key: oidcSubject, Value: CachedUser (User DTO + groups hash)
    private final Cache<String, CachedUser> oidcUserCache = Caffeine.newBuilder()
            .expireAfterWrite(Duration.ofMinutes(5))
            .maximumSize(1000)
            .build();

    private record CachedUser(BookLoreUser user, int groupsHash) {}


    private final AppProperties appProperties;
    private final UserRepository userRepository;
    private final LibraryRepository libraryRepository;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
    private final UserDefaultsService userDefaultsService;
    private final AppSettingService appSettingService;
    private final DynamicOidcJwtProcessor dynamicOidcJwtProcessor;
    private final UserPersistenceService userPersistenceService;

    public boolean isInitialUserAlreadyProvisioned() {
        return userRepository.count() > 0;
    }

    /**
     * Validates an OIDC token and provisions/returns the user.
     * This method is used by the token exchange endpoint.
     *
     * Uses subject-first lookup for stable user identity:
     * 1. Try to find user by OIDC subject (immutable)
     * 2. Fall back to username lookup (migration path)
     * 3. Link subject to existing user if found by username
     * 4. Sync profile if claims changed
     * 5. Apply group-based role mappings if configured
     *
     * @param oidcToken The OIDC ID token to validate
     * @return BookLoreUser DTO if validation successful, null otherwise
     */
    public BookLoreUser provisionUserFromOidcToken(String oidcToken) {
        try {
            log.debug("Validating OIDC token and provisioning user");

            JWTClaimsSet claims = dynamicOidcJwtProcessor.process(oidcToken);

            String subject = claims.getSubject();
            String issuer = claims.getIssuer();
            if (subject == null || subject.trim().isEmpty()) {
                log.error("OIDC token missing required 'sub' claim");
                return null;
            }

            String oidcSubject = (issuer != null ? issuer : "unknown") + ":" + subject;

            OidcProviderDetails providerDetails = appSettingService.getAppSettings().getOidcProviderDetails();
            OidcProviderDetails.ClaimMapping claimMapping = providerDetails != null ? providerDetails.getClaimMapping() : null;

            List<String> groups = Collections.emptyList();
            if (claimMapping != null && claimMapping.getGroups() != null && !claimMapping.getGroups().isEmpty()) {
                groups = dynamicOidcJwtProcessor.extractGroups(claims, claimMapping.getGroups());
                if (!groups.isEmpty()) {
                    log.debug("Extracted groups from OIDC token: {}", groups);
                }
            }
            int groupsHash = groups.hashCode();

            CachedUser cached = oidcUserCache.getIfPresent(oidcSubject);
            if (cached != null && cached.groupsHash() == groupsHash) {
                log.debug("Returning cached OIDC user for subject: {}", oidcSubject);
                return cached.user();
            }

            String username = extractClaimValue(claims, claimMapping != null ? claimMapping.getUsername() : null, oidcSubject);
            String email = extractClaimValue(claims, claimMapping != null ? claimMapping.getEmail() : "email", null);
            if (email != null) {
                email = email.toLowerCase(Locale.ROOT);
            }
            String name = extractClaimValue(claims, claimMapping != null ? claimMapping.getName() : "name", null);

            username = sanitizeUsername(username);

            log.info("OIDC token validated for user: sub={}, username={}, email={}",
                     oidcSubject.substring(0, Math.min(8, oidcSubject.length())) + "...", username, email);

            OidcAutoProvisionDetails provisionDetails = appSettingService.getAppSettings().getOidcAutoProvisionDetails();

            BookLoreUserEntity userEntity = provisionOidcUserWithSubject(oidcSubject, username, email, name, groups, provisionDetails);

            UserPermissionsEntity permsEntity = userEntity.getPermissions();
            BookLoreUser.UserPermissions permissions = new BookLoreUser.UserPermissions();
            permissions.setAdmin(permsEntity.isPermissionAdmin());
            permissions.setCanUpload(permsEntity.isPermissionUpload());
            permissions.setCanDownload(permsEntity.isPermissionDownload());
            permissions.setCanEditMetadata(permsEntity.isPermissionEditMetadata());
            permissions.setCanManageLibrary(permsEntity.isPermissionManageLibrary());
            permissions.setCanEmailBook(permsEntity.isPermissionEmailBook());
            permissions.setCanDeleteBook(permsEntity.isPermissionDeleteBook());
            permissions.setCanAccessOpds(permsEntity.isPermissionAccessOpds());
            permissions.setCanSyncKoReader(permsEntity.isPermissionSyncKoreader());
            permissions.setCanSyncKobo(permsEntity.isPermissionSyncKobo());

            BookLoreUser userDto = BookLoreUser.builder()
                .id(userEntity.getId())
                .username(userEntity.getUsername())
                .email(userEntity.getEmail())
                .name(userEntity.getName())
                .permissions(permissions)
                .provisioningMethod(userEntity.getProvisioningMethod())
                .isDefaultPassword(false)
                .build();

            oidcUserCache.put(oidcSubject, new CachedUser(userDto, groupsHash));

            return userDto;

        } catch (BadJOSEException e) {
            log.warn("OIDC Token Validation Failed: {}", e.getMessage());
            return null;
        } catch (Exception e) {
            log.error("Failed to validate OIDC token and provision user: {}", e.getMessage(), e);
            return null;
        }
    }

    /**
     * Sanitizes the username to prevent security issues.
     * Removes dangerous characters and limits length.
     */
    private String sanitizeUsername(String username) {
        if (username == null) return null;
        // Remove dangerous characters (allow alphanumeric, @, ., _, -)
        String sanitized = REGEX.matcher(username).replaceAll("");
        // Limit length to 255 characters
        return sanitized.substring(0, Math.min(sanitized.length(), 255));
    }

    /**
     * Extracts a claim value from JWT claims using the configured claim name.
     * Falls back to default value if claim is not found or empty.
     */
    private String extractClaimValue(JWTClaimsSet claims, String claimName, String defaultValue) {
        if (claimName == null || claimName.isEmpty()) {
            return defaultValue;
        }
        try {
            Object value = claims.getClaim(claimName);
            if (value instanceof String str && !str.isEmpty()) {
                return str;
            }
        } catch (Exception e) {
            log.debug("Failed to extract claim '{}': {}", claimName, e.getMessage());
        }
        return defaultValue;
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
        perms.setPermissionManageLibrary(true);
        perms.setPermissionEmailBook(true);
        perms.setPermissionDeleteBook(true);
        perms.setPermissionAccessOpds(true);
        perms.setPermissionSyncKoreader(true);
        perms.setPermissionSyncKobo(true);
        perms.setPermissionChangePassword(true);
        perms.setPermissionManageMetadataConfig(true);
        perms.setPermissionAccessBookdrop(true);
        perms.setPermissionAccessLibraryStats(true);
        perms.setPermissionAccessUserStats(true);
        perms.setPermissionAccessTaskManager(true);
        perms.setPermissionManageGlobalPreferences(true);
        perms.setPermissionManageIcons(true);

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
        permissions.setPermissionManageLibrary(request.isPermissionManageLibrary());
        permissions.setPermissionEmailBook(request.isPermissionEmailBook());
        permissions.setPermissionDeleteBook(request.isPermissionDeleteBook());
        permissions.setPermissionAccessOpds(request.isPermissionAccessOpds());
        permissions.setPermissionSyncKoreader(request.isPermissionSyncKoreader());
        permissions.setPermissionSyncKobo(request.isPermissionSyncKobo());
        permissions.setPermissionChangePassword(request.isPermissionChangePassword());
        permissions.setPermissionAdmin(request.isPermissionAdmin());
        permissions.setPermissionManageMetadataConfig(request.isPermissionManageMetadataConfig());
        permissions.setPermissionAccessBookdrop(request.isPermissionAccessBookdrop());
        permissions.setPermissionAccessLibraryStats(request.isPermissionAccessLibraryStats());
        permissions.setPermissionAccessUserStats(request.isPermissionAccessUserStats());
        permissions.setPermissionAccessTaskManager(request.isPermissionAccessTaskManager());
        permissions.setPermissionManageGlobalPreferences(request.isPermissionManageGlobalPreferences());
        permissions.setPermissionManageIcons(request.isPermissionManageIcons());
        user.setPermissions(permissions);

        if (request.getSelectedLibraries() != null && !request.getSelectedLibraries().isEmpty()) {
            List<LibraryEntity> libraries = libraryRepository.findAllById(request.getSelectedLibraries());
            user.setLibraries(new ArrayList<>(libraries));
        }

        createUser(user);
    }

@Transactional
public BookLoreUserEntity provisionOidcUser(String username, String email, String name, OidcAutoProvisionDetails oidcAutoProvisionDetails) {
    BookLoreUserEntity user = new BookLoreUserEntity();
    user.setUsername(username);
    user.setEmail(email);
    user.setName(name);
    user.setDefaultPassword(false);
    user.setPasswordHash("OIDC_USER_" + UUID.randomUUID());
    user.setProvisioningMethod(ProvisioningMethod.OIDC);

    UserPermissionsEntity perms = new UserPermissionsEntity();
    List<String> defaultPermissions = oidcAutoProvisionDetails.getDefaultPermissions();
    if (defaultPermissions != null) {
        perms.setPermissionUpload(defaultPermissions.contains("permissionUpload"));
        perms.setPermissionDownload(defaultPermissions.contains("permissionDownload"));
        perms.setPermissionEditMetadata(defaultPermissions.contains("permissionEditMetadata"));
        perms.setPermissionManageLibrary(defaultPermissions.contains("permissionManageLibrary"));
        perms.setPermissionEmailBook(defaultPermissions.contains("permissionEmailBook"));
        perms.setPermissionDeleteBook(defaultPermissions.contains("permissionDeleteBook"));
        perms.setPermissionAccessOpds(defaultPermissions.contains("permissionAccessOpds"));
        perms.setPermissionSyncKoreader(defaultPermissions.contains("permissionSyncKoreader"));
        perms.setPermissionSyncKobo(defaultPermissions.contains("permissionSyncKobo"));
        perms.setPermissionManageMetadataConfig(defaultPermissions.contains("permissionManageMetadataConfig"));
        perms.setPermissionAccessBookdrop(defaultPermissions.contains("permissionAccessBookdrop"));
        perms.setPermissionAccessLibraryStats(defaultPermissions.contains("permissionAccessLibraryStats"));
        perms.setPermissionAccessUserStats(defaultPermissions.contains("permissionAccessUserStats"));
        perms.setPermissionAccessTaskManager(defaultPermissions.contains("permissionAccessTaskManager"));
        perms.setPermissionManageGlobalPreferences(defaultPermissions.contains("permissionManageGlobalPreferences"));
        perms.setPermissionManageIcons(defaultPermissions.contains("permissionManageIcons"));
    }
    user.setPermissions(perms);

    List<Long> defaultLibraryIds = oidcAutoProvisionDetails.getDefaultLibraryIds();
    if (defaultLibraryIds != null && !defaultLibraryIds.isEmpty()) {
        List<LibraryEntity> libraries = libraryRepository.findAllById(defaultLibraryIds);
        user.setLibraries(new ArrayList<>(libraries));
    }

    return createUser(user);
}

/**
 * Provisions or finds an OIDC user with subject-first lookup strategy.
 *
 * The lookup order is:
 * 1. Try to find by oidcSubject (immutable, preferred)
 * 2. Fall back to username lookup (migration path for existing users)
 * 3. Link oidcSubject to user found by username (migration)
 * 4. Sync profile if username changed in IdP
 * 5. Create new user if not found
 *
 * @param oidcSubject The immutable 'sub' claim from the OIDC token
 * @param username The username/preferred_username claim
 * @param email The email claim
 * @param name The name claim
 * @param groups The groups/roles from the OIDC token for role mapping
 * @param oidcAutoProvisionDetails Auto-provisioning configuration
 * @return The found or created user entity
 */
public BookLoreUserEntity provisionOidcUserWithSubject(String oidcSubject, String username, String email,
                                                       String name, List<String> groups,
                                                       OidcAutoProvisionDetails oidcAutoProvisionDetails) {
    String displayName = name;
    if (displayName == null || displayName.trim().isEmpty()) {
        displayName = username; // Fallback to username
        log.warn("OIDC IdP did not provide 'name' claim. Using username '{}' as display name.", username);
        log.warn("To fix: Configure your IdP to include 'name' claim in ID tokens.");
    }

    log.info("Provisioning OIDC user: sub='{}', username='{}', email='{}', name='{}'",
             oidcSubject != null ? oidcSubject.substring(0, Math.min(8, oidcSubject.length())) + "..." : "null",
             username, email, displayName);

    // Step 1: Try to find by oidcSubject first (immutable identifier)
    if (oidcSubject != null && !oidcSubject.trim().isEmpty()) {
        Optional<BookLoreUserEntity> bySubject = userRepository.findByOidcSubject(oidcSubject);
        if (bySubject.isPresent()) {
            BookLoreUserEntity user = bySubject.get();
            log.debug("Found user by OIDC subject: {}", username);

            syncUserProfile(user, username, email, displayName);

            user.getPermissions().setPermissionChangePassword(true);

            user = userRepository.save(user);

            if (groups != null && !groups.isEmpty()) {
                userPersistenceService.syncUserPermissionsFromGroups(user, groups, oidcAutoProvisionDetails);
            }

            return user;
        }
    }
    Optional<BookLoreUserEntity> byUsername = userRepository.findByUsername(username);
    if (byUsername.isPresent()) {
        BookLoreUserEntity user = byUsername.get();
        log.debug("Found user by username: {} (migration path)", username);

        boolean isLocalUser = user.getProvisioningMethod() == null || user.getProvisioningMethod() != ProvisioningMethod.OIDC;

        if (isLocalUser && user.getOidcSubject() == null) {
            if (email != null && email.equalsIgnoreCase(user.getEmail())) {
                log.info("Linking existing LOCAL user '{}' to OIDC (email verified)", username);
                user.setOidcSubject(oidcSubject);
                user.setProvisioningMethod(ProvisioningMethod.OIDC);
            } else {
                throw new OidcProvisioningException(
                    "Cannot link OIDC account to existing local user. " +
                    "Please log in with your password first and update your email to match your OIDC email, " +
                    "or contact an administrator."
                );
            }
        } else if (user.getOidcSubject() != null && !user.getOidcSubject().equals(oidcSubject)) {
            if (email != null && email.equalsIgnoreCase(user.getEmail())) {
                log.info("OIDC subject mismatch for user '{}'. Updating from '{}' to '{}' based on email match.", username, user.getOidcSubject(), oidcSubject);
                user.setOidcSubject(oidcSubject);
            } else {
                log.error("Security Alert: Username '{}' is already linked to a different OIDC account", username);
                throw new OidcProvisioningException(
                        "This username is already associated with a different OIDC account. " +
                                "Please use a different username or contact an administrator."
                );
            }
        } else if (user.getOidcSubject() == null) {
             log.info("Linking OIDC subject to existing OIDC user '{}'", username);
             user.setOidcSubject(oidcSubject);
        }

        syncUserProfile(user, username, email, displayName);

        user.getPermissions().setPermissionChangePassword(true);

        user = userRepository.save(user);

        if (groups != null && !groups.isEmpty()) {
            userPersistenceService.syncUserPermissionsFromGroups(user, groups, oidcAutoProvisionDetails);
        }

        return user;
    }

    if (email != null) {
        Optional<BookLoreUserEntity> byEmail = userRepository.findByEmail(email);
        if (byEmail.isPresent()) {
            BookLoreUserEntity user = byEmail.get();
            log.debug("Found user by email: {} (migration path)", email);

            boolean isLocalUser = user.getProvisioningMethod() == null || user.getProvisioningMethod() != ProvisioningMethod.OIDC;

            if (isLocalUser && user.getOidcSubject() == null) {
                if (email.equalsIgnoreCase(user.getEmail())) {
                    log.info("Linking existing LOCAL user '{}' to OIDC (found by email)", user.getUsername());
                    user.setOidcSubject(oidcSubject);
                    user.setProvisioningMethod(ProvisioningMethod.OIDC);
                } else {
                    throw new OidcProvisioningException(
                        "Cannot link OIDC account to existing local user. Email mismatch."
                    );
                }
            } else if (user.getOidcSubject() != null && !user.getOidcSubject().equals(oidcSubject)) {
                log.info("OIDC subject mismatch for user '{}'. Updating from '{}' to '{}' based on email match.", user.getUsername(), user.getOidcSubject(), oidcSubject);
                user.setOidcSubject(oidcSubject);
            } else if (user.getOidcSubject() == null) {
                log.info("Linking OIDC subject to existing OIDC user '{}' (found by email)", user.getUsername());
                user.setOidcSubject(oidcSubject);
            }

            syncUserProfile(user, username, email, displayName);

            user.getPermissions().setPermissionChangePassword(true);

            user = userRepository.save(user);

            if (groups != null && !groups.isEmpty()) {
                userPersistenceService.syncUserPermissionsFromGroups(user, groups, oidcAutoProvisionDetails);
            }

            return user;
        }
    }

    try {
        BookLoreUserEntity newUser = userPersistenceService.provisionOidcUserInternal(oidcSubject, username, email, displayName, groups, oidcAutoProvisionDetails);
        return newUser;
    } catch (DuplicateUserRetryException e) {
        log.debug("Retrying user lookup after duplicate key exception for username: {}", username);
        return userRepository.findByUsername(username)
                .or(() -> oidcSubject != null ? userRepository.findByOidcSubject(oidcSubject) : Optional.empty())
                .or(() -> email != null ? userRepository.findByEmail(email) : Optional.empty())
                .orElseThrow(() -> new OidcProvisioningException(
                        String.format("Concurrency Error: User '%s' could not be created, nor found after a unique constraint violation.", username)));
    }
}

/**
 * Syncs user profile fields if they have changed in the IdP.
 */
private void syncUserProfile(BookLoreUserEntity user, String username, String email, String name) {
    if (user.getProvisioningMethod() == ProvisioningMethod.OIDC &&
        username != null && !username.equals(user.getUsername())) {
        log.info("Syncing username change from IdP: '{}' -> '{}'", user.getUsername(), username);
        user.setUsername(username);
    }

    if (email != null && !email.equals(user.getEmail())) {
        log.debug("Syncing email from IdP for user '{}'", user.getUsername());
        user.setEmail(email);
    }

    if (name != null && !name.equals(user.getName())) {
        log.debug("Syncing name from IdP for user '{}'", user.getUsername());
        user.setName(name);
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
            List<String> groupsList = Arrays.asList(GROUPS_SPLIT_PATTERN.split(groupsContent));
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
        permissions.setPermissionChangePassword(false);

        if (oidcAutoProvisionDetails != null && oidcAutoProvisionDetails.getDefaultPermissions() != null) {
            List<String> defaultPermissions = oidcAutoProvisionDetails.getDefaultPermissions();
            permissions.setPermissionUpload(defaultPermissions.contains("permissionUpload"));
            permissions.setPermissionDownload(defaultPermissions.contains("permissionDownload"));
            permissions.setPermissionEditMetadata(defaultPermissions.contains("permissionEditMetadata"));
            permissions.setPermissionManageLibrary(defaultPermissions.contains("permissionManageLibrary"));
            permissions.setPermissionEmailBook(defaultPermissions.contains("permissionEmailBook"));
            permissions.setPermissionDeleteBook(defaultPermissions.contains("permissionDeleteBook"));
            permissions.setPermissionAccessOpds(defaultPermissions.contains("permissionAccessOpds"));
            permissions.setPermissionSyncKoreader(defaultPermissions.contains("permissionSyncKoreader"));
            permissions.setPermissionSyncKobo(defaultPermissions.contains("permissionSyncKobo"));
            permissions.setPermissionManageMetadataConfig(defaultPermissions.contains("permissionManageMetadataConfig"));
            permissions.setPermissionAccessBookdrop(defaultPermissions.contains("permissionAccessBookdrop"));
            permissions.setPermissionAccessLibraryStats(defaultPermissions.contains("permissionAccessLibraryStats"));
            permissions.setPermissionAccessUserStats(defaultPermissions.contains("permissionAccessUserStats"));
            permissions.setPermissionAccessTaskManager(defaultPermissions.contains("permissionAccessTaskManager"));
            permissions.setPermissionManageGlobalPreferences(defaultPermissions.contains("permissionManageGlobalPreferences"));
            permissions.setPermissionManageIcons(defaultPermissions.contains("permissionManageIcons"));
        } else {
            permissions.setPermissionUpload(false);
            permissions.setPermissionDownload(false);
            permissions.setPermissionEditMetadata(false);
            permissions.setPermissionManageLibrary(false);
            permissions.setPermissionEmailBook(false);
            permissions.setPermissionAccessOpds(false);
            permissions.setPermissionDeleteBook(false);
            permissions.setPermissionSyncKoreader(false);
            permissions.setPermissionSyncKobo(false);
            permissions.setPermissionManageMetadataConfig(false);
            permissions.setPermissionAccessBookdrop(false);
            permissions.setPermissionAccessLibraryStats(false);
            permissions.setPermissionAccessUserStats(false);
            permissions.setPermissionAccessTaskManager(false);
            permissions.setPermissionManageGlobalPreferences(false);
            permissions.setPermissionManageIcons(false);
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
