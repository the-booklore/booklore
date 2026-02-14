package org.booklore.service.metadata.parser.custom;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.booklore.mapper.ExternalMetadataMapper;
import org.booklore.model.dto.external.ExternalProviderCapabilities;
import org.booklore.model.dto.settings.CustomMetadataProviderConfig;
import org.booklore.model.dto.settings.MetadataProviderSettings;
import org.booklore.service.appsettings.AppSettingService;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages the lifecycle of {@link CustomBookParser} instances for user-configured
 * external metadata providers.
 * <p>
 * Holds a {@code Map<String, CustomBookParser>} keyed by provider config ID (UUID).
 * On startup, it initializes parsers from stored settings. When settings change
 * (e.g., via the admin UI), call {@link #refresh()} to recreate the parser instances.
 */
@Slf4j
@Service
public class CustomProviderRegistry {

    private final AppSettingService appSettingService;
    private final ExternalMetadataMapper externalMetadataMapper;
    private final RestClient.Builder restClientBuilder;
    private final Map<String, CustomBookParser> parsers = new ConcurrentHashMap<>();

    public CustomProviderRegistry(AppSettingService appSettingService,
                                  ExternalMetadataMapper externalMetadataMapper,
                                  RestClient.Builder restClientBuilder) {
        this.appSettingService = appSettingService;
        this.externalMetadataMapper = externalMetadataMapper;
        this.restClientBuilder = restClientBuilder;
    }

    @PostConstruct
    public void init() {
        refresh();
    }

    /**
     * Rebuilds all custom parser instances from the current app settings.
     * Called on startup and whenever custom provider settings are saved.
     */
    public void refresh() {
        List<CustomMetadataProviderConfig> configs = getCustomProviderConfigs();
        Map<String, CustomBookParser> newParsers = new ConcurrentHashMap<>();

        for (CustomMetadataProviderConfig config : configs) {
            if (config.getId() == null || config.getBaseUrl() == null) {
                log.warn("Skipping custom provider with missing id or baseUrl: {}", config.getName());
                continue;
            }
            try {
                CustomProviderClient client = new CustomProviderClient(restClientBuilder, config);
                CustomBookParser parser = new CustomBookParser(client, externalMetadataMapper);
                newParsers.put(config.getId(), parser);
                log.info("Registered custom metadata provider '{}' ({})", config.getName(), config.getId());
            } catch (Exception e) {
                log.error("Failed to initialize custom provider '{}': {}", config.getName(), e.getMessage());
            }
        }

        parsers.clear();
        parsers.putAll(newParsers);
        log.info("Custom provider registry refreshed: {} providers registered", parsers.size());
    }

    /**
     * Returns the parser for a specific custom provider by its config ID.
     *
     * @param providerId the UUID of the custom provider config
     * @return the parser, or null if not found
     */
    public CustomBookParser getParser(String providerId) {
        return parsers.get(providerId);
    }

    /**
     * Returns all registered custom parsers (both enabled and disabled).
     */
    public Collection<CustomBookParser> getAllParsers() {
        return parsers.values();
    }

    /**
     * Returns all parsers for enabled custom providers.
     */
    public Collection<CustomBookParser> getEnabledParsers() {
        return parsers.values().stream()
                .filter(parser -> isProviderEnabled(parser.getProviderId()))
                .toList();
    }

    /**
     * Checks whether a custom provider is enabled in the current settings.
     */
    public boolean isProviderEnabled(String providerId) {
        return getCustomProviderConfigs().stream()
                .anyMatch(c -> providerId != null && providerId.equals(c.getId()) && c.isEnabled());
    }

    /**
     * Validates a custom provider configuration by attempting to fetch its capabilities.
     * This can be used by the admin UI to test a provider before saving.
     *
     * @param config the provider configuration to validate
     * @return the fetched capabilities, or null if the provider is unreachable
     */
    public ExternalProviderCapabilities validateProvider(CustomMetadataProviderConfig config) {
        try {
            CustomProviderClient client = new CustomProviderClient(restClientBuilder, config);
            return client.fetchCapabilities();
        } catch (Exception e) {
            log.error("Validation failed for custom provider at {}: {}", config.getBaseUrl(), e.getMessage());
            return null;
        }
    }

    private List<CustomMetadataProviderConfig> getCustomProviderConfigs() {
        MetadataProviderSettings settings = appSettingService.getAppSettings().getMetadataProviderSettings();
        if (settings == null || settings.getCustomProviders() == null) {
            return List.of();
        }
        return settings.getCustomProviders();
    }
}
