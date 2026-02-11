package org.booklore.model.dto.settings;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.booklore.model.dto.external.ExternalProviderCapabilities;

/**
 * Configuration for a single custom external metadata provider.
 * Stored as part of {@link MetadataProviderSettings#getCustomProviders()}.
 * Each instance represents a user-configured server that implements the
 * Book Metadata Provider API spec.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CustomMetadataProviderConfig {

    /**
     * Unique identifier for this provider instance (UUID, generated on creation).
     */
    private String id;

    /**
     * Human-readable display name. Auto-populated from the /capabilities endpoint
     * if not explicitly set by the user.
     */
    private String name;

    /**
     * Base URL of the provider API (e.g., "https://my-provider.example.com/v1").
     */
    private String baseUrl;

    /**
     * Bearer token for authentication. Null if the provider does not require auth.
     */
    private String bearerToken;

    /**
     * Whether this provider is enabled for use.
     */
    private boolean enabled;

    /**
     * Cached capabilities fetched from the provider's /capabilities endpoint.
     */
    private ExternalProviderCapabilities.Capabilities capabilities;

    /**
     * Cached rate limit info from the provider's /capabilities endpoint.
     */
    private ExternalProviderCapabilities.RateLimit rateLimit;
}
