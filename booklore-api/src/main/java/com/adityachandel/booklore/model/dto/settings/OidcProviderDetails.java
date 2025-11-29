package com.adityachandel.booklore.model.dto.settings;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class OidcProviderDetails {
    private String providerName;
    private String clientId;
    private String issuerUri;
    private ClaimMapping claimMapping;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ClaimMapping {
        private String username;
        private String name;
        private String email;
    }
}