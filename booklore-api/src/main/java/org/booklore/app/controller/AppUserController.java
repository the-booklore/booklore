package org.booklore.app.controller;

import org.booklore.config.security.service.AuthenticationService;
import org.booklore.app.dto.AppUserInfo;
import org.booklore.model.dto.BookLoreUser;
import org.booklore.service.appsettings.AppSettingService;
import lombok.AllArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@AllArgsConstructor
@RestController
@RequestMapping("/api/v1/app/users")
public class AppUserController {

    private final AuthenticationService authenticationService;
    private final AppSettingService appSettingService;

    @GetMapping("/me")
    public ResponseEntity<AppUserInfo> getCurrentUser() {
        BookLoreUser user = authenticationService.getAuthenticatedUser();
        BookLoreUser.UserPermissions perms = user.getPermissions();

        int maxUploadSizeMb = 100; // default
        try {
            Integer configured = appSettingService.getAppSettings().getMaxFileUploadSizeInMb();
            if (configured != null) {
                maxUploadSizeMb = configured;
            }
        } catch (Exception ignored) {
            // fall back to default
        }

        AppUserInfo info = AppUserInfo.builder()
                .isAdmin(perms.isAdmin())
                .canUpload(perms.isCanUpload())
                .canDownload(perms.isCanDownload())
                .canAccessBookdrop(perms.isCanAccessBookdrop())
                .maxFileUploadSizeMb(maxUploadSizeMb)
                .build();

        return ResponseEntity.ok(info);
    }
}
