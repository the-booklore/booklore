package org.booklore.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.booklore.config.security.oidc.BackchannelLogoutService;
import org.booklore.config.security.oidc.OidcAuthService;
import org.booklore.config.security.oidc.OidcCallbackRequest;
import org.booklore.config.security.oidc.OidcStateService;
import org.booklore.exception.ApiError;
import org.booklore.model.enums.AuditAction;
import org.booklore.service.audit.AuditService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;

@Slf4j
@AllArgsConstructor
@RestController
@RequestMapping("/api/v1/auth/oidc")
public class OidcAuthController {

    private final OidcAuthService oidcAuthService;
    private final BackchannelLogoutService backchannelLogoutService;
    private final OidcStateService oidcStateService;
    private final AuditService auditService;

    @GetMapping("/state")
    public ResponseEntity<Map<String, String>> generateState() {
        return ResponseEntity.ok(Map.of("state", oidcStateService.generateState()));
    }

    @PostMapping("/callback")
    public ResponseEntity<Map<String, String>> handleCallback(
            @RequestBody @Valid OidcCallbackRequest request,
            HttpServletRequest httpRequest) {
        log.info("OIDC callback received");
        oidcStateService.validateAndConsume(request.state());
        try {
            return oidcAuthService.exchangeCodeForTokens(
                    request.code(),
                    request.codeVerifier(),
                    request.redirectUri(),
                    request.nonce(),
                    httpRequest
            );
        } catch (Exception e) {
            auditService.log(AuditAction.OIDC_LOGIN_FAILED, "OIDC callback login failed");
            throw e;
        }
    }

    @GetMapping("/redirect")
    public ResponseEntity<Void> handleRedirect(
            @RequestParam("code") String code,
            @RequestParam("code_verifier") String codeVerifier,
            @RequestParam("redirect_uri") String redirectUri,
            @RequestParam("nonce") String nonce,
            @RequestParam("state") String state,
            @RequestParam("app_redirect_uri") String appRedirectUri,
            HttpServletRequest httpRequest) {

        oidcStateService.validateAndConsume(state);
        oidcAuthService.validateAppRedirectUri(appRedirectUri);

        try {
            ResponseEntity<Map<String, String>> tokenResponse = oidcAuthService.exchangeCodeForTokens(
                    code, codeVerifier, redirectUri, nonce, httpRequest);
            Map<String, String> tokens = tokenResponse.getBody();

            if (tokens == null) {
                throw ApiError.GENERIC_UNAUTHORIZED.createException("Failed to obtain tokens");
            }

            StringBuilder fragment = new StringBuilder();
            fragment.append("access_token=").append(URLEncoder.encode(tokens.get("accessToken"), StandardCharsets.UTF_8));
            fragment.append("&refresh_token=").append(URLEncoder.encode(tokens.get("refreshToken"), StandardCharsets.UTF_8));

            if (tokens.containsKey("isDefaultPassword")) {
                fragment.append("&is_default_password=").append(URLEncoder.encode(tokens.get("isDefaultPassword"), StandardCharsets.UTF_8));
            }

            String redirectUrl = appRedirectUri + "#" + fragment;

            HttpHeaders headers = new HttpHeaders();
            headers.setLocation(URI.create(redirectUrl));
            return new ResponseEntity<>(headers, HttpStatus.FOUND);

        } catch (Exception e) {
            auditService.log(AuditAction.OIDC_LOGIN_FAILED, "OIDC redirect login failed");
            String errorRedirect = appRedirectUri + "#error=" + URLEncoder.encode("Authentication failed", StandardCharsets.UTF_8);

            HttpHeaders headers = new HttpHeaders();
            headers.setLocation(URI.create(errorRedirect));
            return new ResponseEntity<>(headers, HttpStatus.FOUND);
        }
    }

    @PostMapping("/mobile/callback")
    public ResponseEntity<Map<String, String>> handleMobileCallback(
            @RequestParam("code") String code,
            @RequestParam("code_verifier") String codeVerifier,
            @RequestParam("redirect_uri") String redirectUri,
            @RequestParam("nonce") String nonce,
            @RequestParam("state") String state,
            HttpServletRequest httpRequest) {

        oidcStateService.validateAndConsume(state);
        try {
            return oidcAuthService.exchangeCodeForTokens(code, codeVerifier, redirectUri, nonce, httpRequest);
        } catch (Exception e) {
            auditService.log(AuditAction.OIDC_LOGIN_FAILED, "OIDC mobile callback login failed");
            throw e;
        }
    }

    @PostMapping(value = "/backchannel-logout", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    public ResponseEntity<Void> backchannelLogout(@RequestParam("logout_token") String logoutToken) {
        try {
            backchannelLogoutService.handleLogoutToken(logoutToken);
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            log.warn("Back-channel logout failed: {}", e.getMessage());
            return ResponseEntity.badRequest().build();
        }
    }
}
