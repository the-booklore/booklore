package com.adityachandel.booklore.config.security;

import com.adityachandel.booklore.config.security.service.DynamicOidcJwtProcessor;
import com.adityachandel.booklore.config.security.service.OidcProperties;
import com.adityachandel.booklore.model.dto.settings.AppSettings;
import com.adityachandel.booklore.model.dto.settings.OidcProviderDetails;
import com.adityachandel.booklore.service.appsettings.AppSettingService;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.time.Duration;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@WireMockTest(httpPort = 9999) // We run a fake Keycloak on port 9999
class OidcTimeoutTest {

    @Mock
    private AppSettingService appSettingService;

    private DynamicOidcJwtProcessor jwtProcessor;

    @BeforeEach
    void setUp() {
        OidcProperties oidcProperties = new OidcProperties(
                new OidcProperties.Jwks(
                        Duration.ofMillis(1000),
                        Duration.ofMillis(1000),
                        1048576,
                        Duration.ofHours(6),
                        Duration.ofHours(1),
                        false,
                        null,
                        null,
                        null,
                        null,
                        null
                ),
                new OidcProperties.Jwt(Duration.ofSeconds(60))
        );

        jwtProcessor = new DynamicOidcJwtProcessor(appSettingService, oidcProperties);

        // Mock the app settings to return our test issuer URI
        OidcProviderDetails providerDetails = new OidcProviderDetails();
        providerDetails.setIssuerUri("http://localhost:9999/auth/realms/booklore");
        providerDetails.setClientId("booklore-client");
        when(appSettingService.getAppSettings()).thenReturn(
            AppSettings.builder()
                .oidcProviderDetails(providerDetails)
                .build()
        );
    }

    @Test
    void shouldTimeoutWhenJwksEndpointIsSlow() {
        // 1. Mock the OIDC Discovery endpoint (needs to return the JWKS URL)
        stubFor(get("/auth/realms/booklore/.well-known/openid-configuration")
            .willReturn(okJson("""
                {
                    "issuer": "http://localhost:9999/auth/realms/booklore",
                    "jwks_uri": "http://localhost:9999/auth/realms/booklore/protocol/openid-connect/certs"
                }
            """)));

        // 2. Mock the JWKS endpoint with a DELAY
        // We tell WireMock: "When you get this request, wait 2000ms before replying"
        stubFor(get("/auth/realms/booklore/protocol/openid-connect/certs")
            .willReturn(okJson("{\"keys\": []}")
            .withFixedDelay(2000))); // <--- THE TRAP

        // 3. Run the code and expect it to fail FAST (at ~1000ms), not SLOW (at 2000ms)
        // Use a minimal valid JWT format: header.payload.signature
        String dummyJwt = "eyJhbGciOiJSUzI1NiJ9.eyJzdWIiOiIxMjM0NTY3ODkwIiwibmFtZSI6IkpvaG4gRG9lIn0.dummy";
        assertThatThrownBy(() -> jwtProcessor.getProcessor().process(dummyJwt, null))
            .hasCauseInstanceOf(java.net.SocketTimeoutException.class) // The proof your fix works
            .hasMessageContaining("Read timed out");
    }

    @Test
    void shouldNotThrowRateLimitExceptionOnMultipleFailures() {
        // Setup: Discovery works, but JWKS always fails (500 error) immediately
        stubFor(get("/auth/realms/booklore/.well-known/openid-configuration")
            .willReturn(okJson("{\"jwks_uri\": \"http://localhost:9999/jwks\"}")));

        stubFor(get("/jwks").willReturn(serverError())); // 500 Internal Server Error

        // Hammer the endpoint 5 times rapidly
        String testJwt = "eyJhbGciOiJSUzI1NiJ9.eyJzdWIiOiIxMjM0NTY3ODkwIiwibmFtZSI6IkpvaG4gRG9lIn0.dummy";
        for (int i = 0; i < 5; i++) {
            assertThatThrownBy(() -> jwtProcessor.getProcessor().process(testJwt, null))
                .isInstanceOf(Exception.class)
                // Crucial: assert it is NOT a RateLimitReachedException
                .hasRootCauseInstanceOf(java.io.IOException.class);
        }
    }
}
