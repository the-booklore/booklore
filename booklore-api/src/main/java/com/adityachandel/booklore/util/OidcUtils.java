package com.adityachandel.booklore.util;

import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;

import java.net.URI;
import java.net.URL;
import java.util.regex.Pattern;

@Slf4j
@UtilityClass
public class OidcUtils {

    private static final Pattern TRAILING_SLASH_PATTERN = Pattern.compile("/+$");

    /**
     * Resolves the discovery URI from the issuer URI.
     * If the issuer URI already ends with /.well-known/openid-configuration, it is returned as is.
     * Otherwise, it is appended.
     *
     * @param issuerUri The issuer URI
     * @return The resolved discovery URI
     */
    public static String resolveDiscoveryUri(String issuerUri) {
        if (issuerUri == null) {
            return null;
        }
        if (issuerUri.endsWith("/.well-known/openid-configuration")) {
            return issuerUri;
        }
        return normalizeIssuerUri(issuerUri) + "/.well-known/openid-configuration";
    }

    /**
     * Normalizes the issuer URI by removing trailing slashes.
     *
     * @param issuerUri The issuer URI to normalize
     * @return The normalized issuer URI
     */
    public static String normalizeIssuerUri(String issuerUri) {
        if (issuerUri == null) {
            return null;
        }
        return TRAILING_SLASH_PATTERN.matcher(issuerUri).replaceAll("");
    }

    /**
     * Validates the discovery URI to prevent SSRF attacks.
     * Blocks internal network addresses and requires HTTPS (unless in development).
     *
     * @param uri The discovery URI to validate
     * @param isDevelopment Whether the application is running in development mode
     * @throws SecurityException If the URI is unsafe
     */
    public static void validateDiscoveryUri(String uri, boolean isDevelopment) {
        validateDiscoveryUri(uri, isDevelopment, false);  // default to not allowing insecure providers
    }

    /**
     * Validates the discovery URI to prevent SSRF attacks.
     * Blocks internal network addresses and requires HTTPS (unless in development or explicitly allowed).
     *
     * @param uri The discovery URI to validate
     * @param isDevelopment Whether the application is running in development mode
     * @param allowInsecureProviders Whether to allow insecure (HTTP) OIDC providers
     * @throws SecurityException If the URI is unsafe
     */
    public static void validateDiscoveryUri(String uri, boolean isDevelopment, boolean allowInsecureProviders) {
        if (uri == null) return;

        try {
            URL url = new URI(uri).toURL();
            String host = url.getHost();

            // Block internal network addresses
            if ("localhost".equalsIgnoreCase(host) ||
                "127.0.0.1".equals(host) ||
                host.startsWith("192.168.") ||
                host.startsWith("10.") ||
                host.startsWith("172.16.") || // 172.16.x.x to 172.31.x.x
                host.startsWith("169.254.") || // Link-local
                "::1".equals(host) ||
                "0.0.0.0".equals(host)) {

                if (isDevelopment) {
                    log.warn("Allowing internal discovery URI in development mode: {}", uri);
                    return;
                }

                if (allowInsecureProviders) {
                    log.warn("Allowing internal OIDC provider due to allowInsecureOidcProviders=true: {}", uri);
                    return;
                }

                throw new SecurityException("Discovery URI cannot point to internal network addresses. " +
                        "Set booklore.security.oidc.allow-insecure-oidc-providers=true to override (NOT recommended)");
            }

            if (!isDevelopment && !allowInsecureProviders && !"https".equalsIgnoreCase(url.getProtocol())) {
                throw new SecurityException("Discovery URI must use HTTPS in production");
            }

        } catch (Exception e) {
            if (e instanceof SecurityException securityException) {
                throw securityException;
            }
            throw new SecurityException("Invalid discovery URI format: " + e.getMessage());
        }
    }
}
