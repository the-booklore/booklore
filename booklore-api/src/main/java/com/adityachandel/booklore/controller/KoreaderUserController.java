package com.adityachandel.booklore.controller;

import com.adityachandel.booklore.model.dto.KoreaderUser;
import com.adityachandel.booklore.service.koreader.KoreaderUserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/v1/koreader-users")
@RequiredArgsConstructor
public class KoreaderUserController {

    private final KoreaderUserService koreaderUserService;

    @GetMapping("/me")
    @PreAuthorize("@securityUtil.canSyncKoReader() or @securityUtil.isAdmin()")
    public ResponseEntity<KoreaderUser> getCurrentUser() {
        return ResponseEntity.ok(koreaderUserService.getUser());
    }

    @PutMapping("/me")
    @PreAuthorize("@securityUtil.canSyncKoReader() or @securityUtil.isAdmin()")
    public ResponseEntity<KoreaderUser> upsertCurrentUser(@RequestBody Map<String, String> userData) {
        KoreaderUser user = koreaderUserService.upsertUser(userData.get("username"), userData.get("password"));
        return ResponseEntity.ok(user);
    }

    @PatchMapping("/me/sync")
    @PreAuthorize("@securityUtil.canSyncKoReader() or @securityUtil.isAdmin()")
    public ResponseEntity<Void> updateSyncEnabled(@RequestParam boolean enabled) {
        koreaderUserService.toggleSync(enabled);
        return ResponseEntity.noContent().build();
    }
}
