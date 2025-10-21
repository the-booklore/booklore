package com.adityachandel.booklore.controller;

import com.adityachandel.booklore.config.AppProperties;
import com.adityachandel.booklore.config.security.service.AuthenticationService;
import com.adityachandel.booklore.exception.ApiError;
import com.adityachandel.booklore.model.dto.UserCreateRequest;
import com.adityachandel.booklore.model.dto.request.RefreshTokenRequest;
import com.adityachandel.booklore.model.dto.request.UserLoginRequest;
import com.adityachandel.booklore.model.entity.BookLoreUserEntity;
import com.adityachandel.booklore.repository.UserRepository;
import com.adityachandel.booklore.service.user.UserProvisioningService;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Locale;
import java.util.Map;
import java.util.Optional;

@Slf4j
@AllArgsConstructor
@RestController
@RequestMapping("/api/v1/auth")
public class AuthenticationController {

    private final AppProperties appProperties;
    private final UserProvisioningService userProvisioningService;
    private final AuthenticationService authenticationService;
    private final UserRepository userRepository;

    @PostMapping("/register")
    @PreAuthorize("@securityUtil.isAdmin()")
    public ResponseEntity<?> registerUser(@RequestBody @Valid UserCreateRequest userCreateRequest) {
        userProvisioningService.provisionInternalUser(userCreateRequest);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/login")
    public ResponseEntity<Map<String, String>> loginUser(@RequestBody @Valid UserLoginRequest loginRequest) {
        return authenticationService.loginUser(loginRequest);
    }

    @PostMapping("/refresh")
    public ResponseEntity<Map<String, String>> refreshToken(@Valid @RequestBody RefreshTokenRequest request) {
        return authenticationService.refreshToken(request.getRefreshToken());
    }

    @GetMapping("/remote")
    public ResponseEntity<Map<String, String>> loginRemote(@RequestHeader Map<String, String> headers) {
        if (!appProperties.getRemoteAuth().isEnabled()) {
            throw ApiError.REMOTE_AUTH_DISABLED.createException();
        }

        String name = headers.get(appProperties.getRemoteAuth().getHeaderName().toLowerCase(Locale.ROOT));
        String username = headers.get(appProperties.getRemoteAuth().getHeaderUser().toLowerCase(Locale.ROOT));
        String email = headers.get(appProperties.getRemoteAuth().getHeaderEmail().toLowerCase(Locale.ROOT));
        String groups = headers.get(appProperties.getRemoteAuth().getHeaderGroups().toLowerCase(Locale.ROOT));
        log.debug("Remote-Auth: retrieved values from headers: name: {}, username: {}, email: {}, groups: {}", name, username, email, groups);
        log.debug("Remote-Auth: remote auth settings: {}", appProperties.getRemoteAuth());

        if ((username == null || username.isEmpty()) && (email != null && !email.isEmpty())) {
            log.debug("Remote-Auth: username is empty, trying to find user by email: {}", email);
            Optional<BookLoreUserEntity> user = userRepository.findByEmail(email);
            if (user.isPresent()) {
                username = user.get().getUsername();
                log.debug("Remote-Auth: found user by email, username: {}", username);
            }
        }

        return authenticationService.loginRemote(name, username, email, groups);
    }
}
