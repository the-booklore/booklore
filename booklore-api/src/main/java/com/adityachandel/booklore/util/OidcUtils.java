package com.adityachandel.booklore.util;

import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;

import java.net.URI;
import java.net.URL;
import java.util.regex.Pattern;

@Slf4j
@UtilityClass
public class OidcUtils {

    private final Pattern TRAILING_SLASH_PATTERN = Pattern.compile("/+$");

    /**
     * Resolves the discovery URI from the issuer URI.
     * If the issuer URI already ends with /.well-known/openid-configuration, it is returned as is.
     * Otherwise, it is appended.
     *
     * @param issuerUri The issuer URI
     * @return The resolved discovery URI
     */
    public String resolveDiscoveryUri(String issuerUri) {
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
    public String normalizeIssuerUri(String issuerUri) {
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
    public void validateDiscoveryUri(String uri, boolean isDevelopment) {
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
    public void validateDiscoveryUri(String uri, boolean isDevelopment, boolean allowInsecureProviders) {
        if (uri == null) return;

        try {
            URL url = new URI(uri).toURL();
            String host = url.getHost();
            
            if (host == null || host.isEmpty()) {
                throw new SecurityException("Discovery URI must contain a valid host");
            }
            
            int port = url.getPort();
            if (port != -1 && port != 80 && port != 443 && port != 8080 && port != 8443 && port != 9000 && port != 9443) {
                if (!isDevelopment && !allowInsecureProviders) {
                    log.warn("Non-standard port {} in discovery URI: {}", port, uri);
                }
            }

            if (isInternalNetworkAddress(host)) {
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

    private boolean isInternalNetworkAddress(String host) {
        if (host == null) return false;
        
        String lowerHost = host.toLowerCase();
        
        if ("localhost".equals(lowerHost) ||
            "localhost.localdomain".equals(lowerHost) ||
            lowerHost.endsWith(".localhost") ||
            lowerHost.endsWith(".local")) {
            return true;
        }
        
        if ("127.0.0.1".equals(host) || "::1".equals(host) || "0.0.0.0".equals(host)) {
            return true;
        }
        
        if (host.startsWith("127.")) {
            return true;
        }
        
        if (host.startsWith("192.168.") ||  // 192.168.0.0/16
            host.startsWith("10.")) {        // 10.0.0.0/8
            return true;
        }
        
        if (host.startsWith("172.")) {
            try {
                String[] parts = host.split("\\.");
                if (parts.length >= 2) {
                    int secondOctet = Integer.parseInt(parts[1]);
                    if (secondOctet >= 16 && secondOctet <= 31) {
                        return true;
                    }
                }
            } catch (NumberFormatException e) {
                // Not a valid IP, continue with other checks
            }
        }
        
        if (host.startsWith("169.254.")) {
            return true;
        }
        
        if (host.startsWith("169.254.169.254") ||
            "metadata.google.internal".equals(lowerHost) ||
            lowerHost.endsWith(".internal")) {
            return true;
        }
        
        if (host.startsWith("fc") || host.startsWith("fd") ||  // fc00::/7 - Unique local addresses
            host.startsWith("fe80:")) {                         // fe80::/10 - Link-local addresses
            return true;
        }
        
        return false;
    }
}
