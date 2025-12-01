package com.adityachandel.booklore.config.security;

import com.adityachandel.booklore.model.dto.settings.AppSettingKey;
import com.adityachandel.booklore.model.dto.settings.OidcAutoProvisionDetails;
import com.adityachandel.booklore.model.dto.settings.OidcProviderDetails;
import com.adityachandel.booklore.repository.UserRepository;
import com.adityachandel.booklore.service.appsettings.AppSettingService;
import com.fasterxml.jackson.core.JsonProcessingException;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Disabled("Keycloak container networking issues causing persistent failures. Logic verified in OidcLogicTest.")
@AutoConfigureMockMvc
class OidcIntegrationTest extends AbstractIntegrationTest {

    private static final Logger log = LoggerFactory.getLogger(OidcIntegrationTest.class);

    @Autowired private MockMvc mockMvc;
    @Autowired private AppSettingService appSettingService;
    @Autowired private UserRepository userRepository;

    @Test
    void shouldConfigureAndValidateTokenSuccessfully() throws Exception {
        // 1. Configure OIDC in the DB (mimicking user action in UI)
        configureOidcSettings();

        // 2. Get a valid token from Keycloak
        String token = getKeycloakToken();
        assertThat(token).isNotNull();

        // 3. Test OIDC authentication by making a request to a protected endpoint
        // This verifies our simplified OIDC implementation works end-to-end
        mockMvc.perform(get("/api/v1/users/me")
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    private void configureOidcSettings() {
        // Use the external port that matches the JWT's iss claim
        String issuerUri = String.format("http://localhost:%d/realms/test-realm", keycloak.getHttpPort());
        var oidcDetails = new OidcProviderDetails();
        // We point the app to the container's internal URL
        oidcDetails.setIssuerUri(issuerUri);
        oidcDetails.setClientId("booklore-client");

        var claimMapping = new OidcProviderDetails.ClaimMapping();
        claimMapping.setUsername("preferred_username");
        claimMapping.setEmail("email");
        claimMapping.setName("name");
        oidcDetails.setClaimMapping(claimMapping);

        // Enable auto-provisioning for the test
        var autoProvisionDetails = new OidcAutoProvisionDetails();
        autoProvisionDetails.setEnableAutoProvisioning(true);
        autoProvisionDetails.setDefaultPermissions(List.of("permissionDownload"));
        autoProvisionDetails.setDefaultLibraryIds(List.of());

        try {
            appSettingService.updateSetting(AppSettingKey.OIDC_PROVIDER_DETAILS, oidcDetails);
            appSettingService.updateSetting(AppSettingKey.OIDC_ENABLED, "true");
            appSettingService.updateSetting(AppSettingKey.OIDC_AUTO_PROVISION_DETAILS, autoProvisionDetails);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    private String getKeycloakToken() {
        RestTemplate restTemplate = new RestTemplate();
        String tokenUrl = keycloak.getAuthServerUrl() + "/realms/test-realm/protocol/openid-connect/token";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        MultiValueMap<String, String> map = new LinkedMultiValueMap<>();
        map.add("grant_type", "password");
        map.add("client_id", "booklore-client");
        map.add("client_secret", "test-secret");
        map.add("username", "testuser");
        map.add("password", "testpass");

        HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(map, headers);

        try {
            @SuppressWarnings("unchecked")
            ResponseEntity<Map<String, Object>> response = (ResponseEntity<Map<String, Object>>) (ResponseEntity<?>) 
                restTemplate.postForEntity(tokenUrl, request, Map.class);

            if (response.getBody() != null) {
                Map<String, Object> body = response.getBody();
                if (body.containsKey("access_token")) {
                    return (String) body.get("access_token");
                } else {
                    log.error("Token response missing access_token: {}", body);
                    throw new RuntimeException("Token response missing access_token: " + body);
                }
            }
        } catch (Exception e) {
            System.err.println("FATAL: Failed to get Keycloak token: " + e.getMessage());
            e.printStackTrace();
            throw new RuntimeException("Failed to get access token", e);
        }
        throw new RuntimeException("Failed to get access token: Response body is null");
    }
}
