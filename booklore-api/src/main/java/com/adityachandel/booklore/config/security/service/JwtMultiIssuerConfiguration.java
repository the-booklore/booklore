package com.adityachandel.booklore.config.security.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.web.client.RestTemplate;

import java.util.*;

@Slf4j
@Configuration
@ConfigurationProperties(prefix = "booklore.security.oidc")
public class JwtMultiIssuerConfiguration {

    @Value("${booklore.security.oauth2.multi-issuer.enabled:false}")
    private boolean legacyEnabled = false;

    @Value("${booklore.security.oauth2.multi-issuer.issuers[0].internal-url:#{null}}")
    private String legacyIssuer0InternalUrl;

    @Value("${booklore.security.oauth2.multi-issuer.issuers[0].external-url:#{null}}")
    private String legacyIssuer0ExternalUrl;

    @Value("${booklore.security.oauth2.multi-issuer.issuers[1].internal-url:#{null}}")
    private String legacyIssuer1InternalUrl;

    @Value("${booklore.security.oauth2.multi-issuer.issuers[1].external-url:#{null}}")
    private String legacyIssuer1ExternalUrl;

    private boolean enabled = true;

    private List<IssuerMapping> issuers = new ArrayList<>();

    private String issuerUri;
    private String internalIssuerUri;

    // Local dev only, MUST be explicitly enabled
    private boolean enableLocalDevFallback = false;

    private boolean strictIssuerValidation = true;
    private int connectionTimeoutMs = 30000;
    private int readTimeoutMs = 30000;

    private RestTemplate sharedRestTemplate;

    @Bean
    public JwtDecoder jwtDecoder() {
        if (shouldUseLegacyConfig()) {
            log.info("Using legacy multi-issuer configuration for backward compatibility");
            return createLegacyMultiIssuerDecoder();
        }

        if (!enabled) {
            log.warn("JWT issuer configuration is disabled");
            return null;
        }

        if (!issuers.isEmpty()) {
            return createExplicitMultiIssuerDecoder();
        }

        if (issuerUri != null || internalIssuerUri != null) {
            return createEnvironmentBasedDecoder();
        }

        if (enableLocalDevFallback && isLocalDevelopmentEnvironment()) {
            log.warn("Using local development fallback - NOT FOR PRODUCTION");
            return createLocalDevDecoder();
        }

        throw new IllegalStateException(
            "No JWT issuer configuration found. Please configure:\n" +
            "1. app.security.jwt.issuers[] (recommended), or\n" +
            "2. app.security.jwt.issuer-uri + internal-issuer-uri, or\n" +
            "3. Set app.security.jwt.enable-local-dev-fallback=true for local dev only"
        );
    }

    private JwtDecoder createExplicitMultiIssuerDecoder() {
        Map<String, JwtDecoder> decoders = new HashMap<>();

        for (IssuerMapping mapping : issuers) {
            String discoveryUrl = mapping.getInternalUrl() != null
                ? mapping.getInternalUrl()
                : mapping.getExternalUrl();
            String validationUrl = mapping.getExternalUrl();

            NimbusJwtDecoder.JwkSetUriJwtDecoderBuilder builder = NimbusJwtDecoder
                .withIssuerLocation(discoveryUrl);

            if (sharedRestTemplate == null) {
                sharedRestTemplate = createRestTemplateWithTimeouts();
            }
            builder.restOperations(sharedRestTemplate);

            NimbusJwtDecoder decoder = builder.build();

            if (strictIssuerValidation) {
                decoder.setJwtValidator(
                    JwtValidators.createDefaultWithIssuer(validationUrl)
                );
            }

            decoders.put(validationUrl, decoder);
            log.info("Configured issuer: {} (discovery: {}, validation: {})",
                mapping.getName(), discoveryUrl, validationUrl);
        }

        return new MultiIssuerJwtDecoder(decoders);
    }

    private JwtDecoder createEnvironmentBasedDecoder() {
        String discoveryUrl = internalIssuerUri != null ? internalIssuerUri : issuerUri;
        String validationUrl = issuerUri;

        log.info("Using environment-based issuer configuration: discovery={}, validation={}",
            discoveryUrl, validationUrl);

        NimbusJwtDecoder.JwkSetUriJwtDecoderBuilder builder = NimbusJwtDecoder
            .withIssuerLocation(discoveryUrl);

        if (sharedRestTemplate == null) {
            sharedRestTemplate = createRestTemplateWithTimeouts();
        }
        builder.restOperations(sharedRestTemplate);

        NimbusJwtDecoder decoder = builder.build();

        if (strictIssuerValidation && validationUrl != null) {
            decoder.setJwtValidator(
                JwtValidators.createDefaultWithIssuer(validationUrl)
            );
        }

        return decoder;
    }

    private JwtDecoder createLocalDevDecoder() {
        String[] localUrls = {
            "http://localhost:8080",
            "http://host.docker.internal:8080",
            "http://keycloak:8080"
        };

        for (String url : localUrls) {
            try {
                NimbusJwtDecoder decoder = NimbusJwtDecoder
                    .withIssuerLocation(url + "/realms/master")
                    .build();

                decoder.setJwtValidator(jwt -> {
                    log.debug("Local dev mode: Accepting token from issuer: {}", jwt.getIssuer());
                    return org.springframework.security.oauth2.core.OAuth2TokenValidatorResult.success();
                });

                log.warn("LOCAL DEV MODE: Using auto-detected issuer {} - DO NOT USE IN PRODUCTION", url);
                return decoder;
            } catch (Exception e) {
                log.debug("Could not connect to {}: {}", url, e.getMessage());
            }
        }

        throw new IllegalStateException("Local dev fallback enabled but no local OIDC server found");
    }

    private boolean shouldUseLegacyConfig() {
        return legacyEnabled && hasLegacyIssuers() &&
               (issuers.isEmpty() && issuerUri == null && internalIssuerUri == null);
    }

    private boolean hasLegacyIssuers() {
        return (legacyIssuer0InternalUrl != null || legacyIssuer0ExternalUrl != null) ||
               (legacyIssuer1InternalUrl != null || legacyIssuer1ExternalUrl != null);
    }

    private JwtDecoder createLegacyMultiIssuerDecoder() {
        Map<String, JwtDecoder> decoders = new HashMap<>();

        if (legacyIssuer0ExternalUrl != null) {
            addLegacyIssuer(decoders, legacyIssuer0ExternalUrl, legacyIssuer0InternalUrl, "Legacy Issuer 0");
        }
        if (legacyIssuer1ExternalUrl != null) {
            addLegacyIssuer(decoders, legacyIssuer1ExternalUrl, legacyIssuer1InternalUrl, "Legacy Issuer 1");
        }

        return new MultiIssuerJwtDecoder(decoders);
    }

    private void addLegacyIssuer(Map<String, JwtDecoder> decoders, String externalUrl, String internalUrl, String name) {
        String discoveryUrl = internalUrl != null ? internalUrl : externalUrl;

        NimbusJwtDecoder.JwkSetUriJwtDecoderBuilder builder = NimbusJwtDecoder
            .withIssuerLocation(discoveryUrl);

        builder.restOperations(createRestTemplateWithTimeouts());

        NimbusJwtDecoder decoder = builder.build();

        if (strictIssuerValidation) {
            decoder.setJwtValidator(
                JwtValidators.createDefaultWithIssuer(externalUrl)
            );
        }

        decoders.put(externalUrl, decoder);
        log.info("Configured legacy issuer: {} (discovery: {}, validation: {})",
            name, discoveryUrl, externalUrl);
    }

    private static class MultiIssuerJwtDecoder implements JwtDecoder {
        private final Map<String, JwtDecoder> decoders;

        public MultiIssuerJwtDecoder(Map<String, JwtDecoder> decoders) {
            this.decoders = decoders;
        }

        @Override
        public Jwt decode(String token) throws JwtException {
            String issuer = extractIssuerFromToken(token);

            if (issuer != null && decoders.containsKey(issuer)) {
                return decoders.get(issuer).decode(token);
            }

            List<String> failures = new ArrayList<>();
            for (Map.Entry<String, JwtDecoder> entry : decoders.entrySet()) {
                try {
                    return entry.getValue().decode(token);
                } catch (JwtException e) {
                    failures.add(entry.getKey() + ": " + e.getMessage());
                }
            }

            throw new JwtValidationException(
                "Token validation failed for all issuers. Failures: " + String.join("; ", failures),
                Collections.emptyList()
            );
        }

        private String extractIssuerFromToken(String token) {
            try {
                String[] parts = token.split("\\.");
                if (parts.length >= 2) {
                    String payload = new String(Base64.getUrlDecoder().decode(parts[1]));
                    int issStart = payload.indexOf("\"iss\":\"");
                    if (issStart != -1) {
                        issStart += 7;
                        int issEnd = payload.indexOf("\"", issStart);
                        if (issEnd != -1) {
                            return payload.substring(issStart, issEnd);
                        }
                    }
                }
            } catch (Exception e) {
                // Ignore
            }
            return null;
        }
    }

    private boolean isLocalDevelopmentEnvironment() {
        String env = System.getenv("SPRING_PROFILES_ACTIVE");
        String[] localProfiles = {"local", "dev", "development"};

        if (env != null) {
            for (String profile : localProfiles) {
                if (env.contains(profile)) {
                    return true;
                }
            }
        }

        String javaCommand = System.getProperty("sun.java.command", "");
        return javaCommand.contains("IntellijIdeaRulezzz") ||
               javaCommand.contains("org.eclipse.jdt");
    }

    private RestTemplate createRestTemplateWithTimeouts() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(connectionTimeoutMs);
        factory.setReadTimeout(readTimeoutMs);

        RestTemplate restTemplate = new RestTemplate();
        restTemplate.setRequestFactory(factory);
        return restTemplate;
    }

    public static class IssuerMapping {
        private String name;
        private String externalUrl;  // URL in JWT iss claim
        private String internalUrl;  // URL for OIDC discovery (optional)

        // Constructor for backward compatibility
        public IssuerMapping(String name, String externalUrl, String internalUrl) {
            this.name = name != null ? name : "Legacy Config";
            this.externalUrl = externalUrl;
            this.internalUrl = internalUrl;
        }

        // Getters/Setters
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }

        public String getExternalUrl() { return externalUrl; }
        public void setExternalUrl(String externalUrl) { this.externalUrl = externalUrl; }

        public String getInternalUrl() { return internalUrl; }
        public void setInternalUrl(String internalUrl) { this.internalUrl = internalUrl; }
    }

    // Backward compatibility: old configuration format
    public static class IssuerConfig {
        private String internalUrl; // http://keycloak:8080/realms/master
        private String externalUrl; // https://auth.mydomain.com/realms/master

        // Getters/Setters
        public String getInternalUrl() { return internalUrl; }
        public void setInternalUrl(String internalUrl) { this.internalUrl = internalUrl; }

        public String getExternalUrl() { return externalUrl; }
        public void setExternalUrl(String externalUrl) { this.externalUrl = externalUrl; }
    }

    // Getters/Setters
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public List<IssuerMapping> getIssuers() { return issuers; }
    public void setIssuers(List<IssuerMapping> issuers) { this.issuers = issuers; }

    public String getIssuerUri() { return issuerUri; }
    public void setIssuerUri(String issuerUri) { this.issuerUri = issuerUri; }

    public String getInternalIssuerUri() { return internalIssuerUri; }
    public void setInternalIssuerUri(String internalIssuerUri) {
        this.internalIssuerUri = internalIssuerUri;
    }

    public boolean isEnableLocalDevFallback() { return enableLocalDevFallback; }
    public void setEnableLocalDevFallback(boolean enable) {
        this.enableLocalDevFallback = enable;
    }

    public boolean isStrictIssuerValidation() { return strictIssuerValidation; }
    public void setStrictIssuerValidation(boolean strict) {
        this.strictIssuerValidation = strict;
    }
}
