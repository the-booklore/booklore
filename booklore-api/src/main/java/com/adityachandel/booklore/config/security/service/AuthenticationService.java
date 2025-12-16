package com.adityachandel.booklore.config.security.service;

import com.adityachandel.booklore.config.AppProperties;
import com.adityachandel.booklore.config.security.JwtUtils;
import com.adityachandel.booklore.config.security.userdetails.OpdsUserDetails;
import com.adityachandel.booklore.exception.ApiError;
import com.adityachandel.booklore.mapper.custom.BookLoreUserTransformer;
import com.adityachandel.booklore.model.dto.BookLoreUser;
import com.adityachandel.booklore.model.dto.request.UserLoginRequest;
import com.adityachandel.booklore.model.entity.BookLoreUserEntity;
import com.adityachandel.booklore.model.entity.RefreshTokenEntity;
import com.adityachandel.booklore.model.enums.ProvisioningMethod;
import com.adityachandel.booklore.repository.RefreshTokenRepository;
import com.adityachandel.booklore.repository.UserRepository;
import com.adityachandel.booklore.service.user.DefaultSettingInitializer;
import com.adityachandel.booklore.service.user.UserPersistenceService;
import com.adityachandel.booklore.service.user.UserProvisioningService;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Slf4j
@AllArgsConstructor
@Service
public class AuthenticationService {

    private final AppProperties appProperties;
    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final UserProvisioningService userProvisioningService;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtils jwtUtils;
    private final DefaultSettingInitializer defaultSettingInitializer;
    private final BookLoreUserTransformer bookLoreUserTransformer;

    public BookLoreUser getAuthenticatedUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null) {
            return null;
        }
        Object principal = authentication.getPrincipal();
        if (principal instanceof BookLoreUser user) {
            if (user.getId() != null && user.getId() != -1L) {
                defaultSettingInitializer.ensureDefaultSettings(user);
            }
            return user;
        } else if (principal instanceof String username) {
            // Handle OIDC authentication where principal is username
            BookLoreUserEntity userEntity = userRepository.findByUsername(username)
                    .orElseThrow(() -> new IllegalStateException("User not found for username: " + username));
            BookLoreUser user = bookLoreUserTransformer.toDTO(userEntity);
            if (user.getId() != null && user.getId() != -1L) {
                defaultSettingInitializer.ensureDefaultSettings(user);
            }
            return user;
        }
        throw new IllegalStateException("Authenticated principal is not of type BookLoreUser or String");
    }

    public BookLoreUser getSystemUser() {
        return createSystemUser();
    }

    private BookLoreUser createSystemUser() {
        BookLoreUser.UserPermissions permissions = new BookLoreUser.UserPermissions();
        permissions.setAdmin(true);
        permissions.setCanUpload(true);
        permissions.setCanDownload(true);
        permissions.setCanEditMetadata(true);
        permissions.setCanManipulateLibrary(true);
        permissions.setCanSyncKoReader(true);
        permissions.setCanSyncKobo(true);
        permissions.setCanEmailBook(true);
        permissions.setCanDeleteBook(true);
        permissions.setCanAccessOpds(true);

        return BookLoreUser.builder()
                .id(-1L)
                .username("system")
                .name("System User")
                .email("system@booklore.internal")
                .provisioningMethod(ProvisioningMethod.LOCAL)
                .isDefaultPassword(false)
                .permissions(permissions)
                .assignedLibraries(List.of())
                .userSettings(new BookLoreUser.UserSettings())
                .build();
    }

    public OpdsUserDetails getOpdsUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof OpdsUserDetails opdsUser) {
            return opdsUser;
        }
        throw new IllegalStateException("No OPDS user authenticated");
    }

    public ResponseEntity<Map<String, String>> loginUser(UserLoginRequest loginRequest) {
        BookLoreUserEntity user = userRepository.findByUsername(loginRequest.getUsername()).orElseThrow(() -> ApiError.USER_NOT_FOUND.createException(loginRequest.getUsername()));

        // Allow OIDC users with a local password (set for OPDS or backup) to login with password
        if (user.getProvisioningMethod() == ProvisioningMethod.OIDC && UserPersistenceService.hasLockedOidcPassword(user)) {
            log.warn("Password authentication attempt blocked for OIDC-provisioned user without local password: {}", loginRequest.getUsername());
            throw ApiError.INVALID_CREDENTIALS.createException("OIDC users cannot authenticate with password before setting a local password. Please use your identity provider.");
        }

        if (!passwordEncoder.matches(loginRequest.getPassword(), user.getPasswordHash())) {
            throw ApiError.INVALID_CREDENTIALS.createException();
        }

        return loginUser(user);
    }

    public ResponseEntity<Map<String, String>> loginRemote(String name, String username, String email, String groups) {
        if (username == null || username.isEmpty()) {
            throw ApiError.GENERIC_BAD_REQUEST.createException("Remote-User header is missing");
        }

        Optional<BookLoreUserEntity> user = userRepository.findByUsername(username);
        if (user.isEmpty() && appProperties.getRemoteAuth().isCreateNewUsers()) {
            user = Optional.of(userProvisioningService.provisionRemoteUserFromHeaders(name, username, email, groups));
        }

        if (user.isEmpty()) {
            throw ApiError.INTERNAL_SERVER_ERROR.createException("User not found and remote user creation is disabled");
        }

        return loginUser(user.get());
    }

    public ResponseEntity<Map<String, String>> loginUser(BookLoreUserEntity user) {
        String accessToken = jwtUtils.generateAccessToken(user);
        String refreshToken = jwtUtils.generateRefreshToken(user);

        RefreshTokenEntity refreshTokenEntity = RefreshTokenEntity.builder()
                .user(user)
                .token(refreshToken)
                .expiryDate(Instant.now().plusMillis(JwtUtils.getRefreshTokenExpirationMs()))
                .revoked(false)
                .build();

        refreshTokenRepository.save(refreshTokenEntity);

        boolean isDefaultPassword = user.isDefaultPassword();
        if (user.getProvisioningMethod() == ProvisioningMethod.OIDC && UserPersistenceService.hasLockedOidcPassword(user)) {
            isDefaultPassword = true;
        }

        return ResponseEntity.ok(Map.of(
                "accessToken", accessToken,
                "refreshToken", refreshTokenEntity.getToken(),
                "isDefaultPassword", String.valueOf(isDefaultPassword)
        ));
    }

    public ResponseEntity<Map<String, String>> generateTokensForUser(BookLoreUser userDto) {
        // Fetch the entity from database to ensure we have the latest data
        BookLoreUserEntity user = userRepository.findByUsername(userDto.getUsername())
                .orElseThrow(() -> ApiError.USER_NOT_FOUND.createException(userDto.getUsername()));
        
        return loginUser(user);
    }

    public ResponseEntity<Map<String, String>> refreshToken(String token) {
        RefreshTokenEntity storedToken = refreshTokenRepository.findByToken(token).orElseThrow(() -> ApiError.INVALID_CREDENTIALS.createException("Refresh token not found"));

        if (storedToken.isRevoked() || storedToken.getExpiryDate().isBefore(Instant.now()) || !jwtUtils.validateToken(token)) {
            throw ApiError.INVALID_CREDENTIALS.createException("Invalid or expired refresh token");
        }

        BookLoreUserEntity user = storedToken.getUser();

        storedToken.setRevoked(true);
        storedToken.setRevocationDate(Instant.now());
        refreshTokenRepository.save(storedToken);

        String newRefreshToken = jwtUtils.generateRefreshToken(user);
        RefreshTokenEntity newRefreshTokenEntity = RefreshTokenEntity.builder()
                .user(user)
                .token(newRefreshToken)
                .expiryDate(Instant.now().plusMillis(JwtUtils.getRefreshTokenExpirationMs()))
                .revoked(false)
                .build();

        refreshTokenRepository.save(newRefreshTokenEntity);

        return ResponseEntity.ok(Map.of(
                "accessToken", jwtUtils.generateAccessToken(user),
                "refreshToken", newRefreshToken
        ));
    }
}