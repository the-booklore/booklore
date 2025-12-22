package com.adityachandel.booklore.model.entity;

import jakarta.persistence.*;
import lombok.*;

@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "user_permissions")
public class UserPermissionsEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private BookLoreUserEntity user;

    @Column(name = "permission_admin", nullable = false)
    private boolean permissionAdmin;

    @Column(name = "permission_upload", nullable = false)
    @Builder.Default
    private boolean permissionUpload = false;

    @Column(name = "permission_download", nullable = false)
    @Builder.Default
    private boolean permissionDownload = false;

    @Column(name = "permission_edit_metadata", nullable = false)
    @Builder.Default
    private boolean permissionEditMetadata = false;

    @Column(name = "permission_manipulate_library", nullable = false)
    @Builder.Default
    private boolean permissionManageLibrary = false;

    @Column(name = "permission_email_book", nullable = false)
    @Builder.Default
    private boolean permissionEmailBook = false;

    @Column(name = "permission_delete_book", nullable = false)
    @Builder.Default
    private boolean permissionDeleteBook = false;

    @Column(name = "permission_sync_koreader", nullable = false)
    @Builder.Default
    private boolean permissionSyncKoreader = false;

    @Column(name = "permission_access_opds", nullable = false)
    @Builder.Default
    private boolean permissionAccessOpds = false;

    @Column(name = "permission_sync_kobo", nullable = false)
    @Builder.Default
    private boolean permissionSyncKobo = false;

    @Column(name = "permission_manage_metadata_config", nullable = false)
    @Builder.Default
    private boolean permissionManageMetadataConfig = false;

    @Column(name = "permission_access_bookdrop", nullable = false)
    @Builder.Default
    private boolean permissionAccessBookdrop = false;

    @Column(name = "permission_access_library_stats", nullable = false)
    @Builder.Default
    private boolean permissionAccessLibraryStats = false;

    @Column(name = "permission_access_user_stats", nullable = false)
    @Builder.Default
    private boolean permissionAccessUserStats = false;

    @Column(name = "permission_access_task_manager", nullable = false)
    @Builder.Default
    private boolean permissionAccessTaskManager = false;

    @Column(name = "permission_manage_global_preferences", nullable = false)
    @Builder.Default
    private boolean permissionManageGlobalPreferences = false;

    @Column(name = "permission_manage_icons", nullable = false)
    @Builder.Default
    private boolean permissionManageIcons = false;

    @Column(name = "permission_demo_user", nullable = false)
    @Builder.Default
    private boolean permissionDemoUser = false;
}