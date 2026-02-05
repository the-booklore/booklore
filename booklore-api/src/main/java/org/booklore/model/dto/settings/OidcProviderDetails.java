package org.booklore.model.dto.settings;

import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class OidcProviderDetails {
    private String providerName;
    private String clientId;
    private String issuerUri;
    private String discoveryUri;
    private ClaimMapping claimMapping;
    
    /**
     * Maps OIDC groups/roles to Booklore permissions.
     * Key: group/role name from IdP (case-sensitive)
     * Value: list of Booklore permissions to grant (e.g., "permissionAdmin", "permissionUpload")
     * 
     * Example for Authentik/Authelia (groups claim):
     *   "booklore-admins" -> ["permissionAdmin", "permissionUpload", "permissionDownload"]
     *   "booklore-users" -> ["permissionDownload"]
     * 
     * Example for Keycloak (realm_access.roles):
     *   "admin" -> ["permissionAdmin"]
     */
    private Map<String, List<String>> roleMappings;

    @Data
    public static class ClaimMapping {
        private String username;
        private String name;
        private String email;
        /**
         * The claim name for groups/roles. Common values:
         * - "groups" (Authentik, Authelia, PocketID)
         * - "realm_access.roles" (Keycloak realm roles)
         * - "resource_access.CLIENT_ID.roles" (Keycloak client roles)
         */
        private String groups;
    }
}