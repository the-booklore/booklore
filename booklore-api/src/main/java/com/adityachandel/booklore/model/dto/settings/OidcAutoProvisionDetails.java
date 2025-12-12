package com.adityachandel.booklore.model.dto.settings;

import lombok.Data;

import java.util.List;

@Data
public class OidcAutoProvisionDetails {
    private boolean enableAutoProvisioning;
    private boolean syncPermissions;
    private List<String> defaultPermissions;
    private List<Long> defaultLibraryIds;
    
    /**
     * The OIDC group name that grants admin privileges in Booklore.
     * When a user authenticates via OIDC and their groups claim contains this group,
     * they will automatically be granted the admin role.
     * This is independent of the roleMappings in OidcProviderDetails and provides
     * a simpler way to grant admin access based on IdP group membership.
     */
    private String adminGroup;
}
