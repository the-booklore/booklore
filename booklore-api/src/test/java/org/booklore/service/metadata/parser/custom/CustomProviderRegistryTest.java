package org.booklore.service.metadata.parser.custom;

import org.booklore.mapper.ExternalMetadataMapper;
import org.booklore.model.dto.external.ExternalProviderCapabilities;
import org.booklore.model.dto.settings.AppSettings;
import org.booklore.model.dto.settings.CustomMetadataProviderConfig;
import org.booklore.model.dto.settings.MetadataProviderSettings;
import org.booklore.service.appsettings.AppSettingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

class CustomProviderRegistryTest {

    @Mock
    private AppSettingService appSettingService;

    @Mock
    private ExternalMetadataMapper externalMetadataMapper;

    private CustomProviderRegistry registry;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        RestClient.Builder restClientBuilder = RestClient.builder();
        registry = new CustomProviderRegistry(appSettingService, externalMetadataMapper, restClientBuilder);
    }

    private void configureProviders(List<CustomMetadataProviderConfig> providers) {
        MetadataProviderSettings mps = new MetadataProviderSettings();
        mps.setCustomProviders(new ArrayList<>(providers));
        AppSettings settings = AppSettings.builder()
                .metadataProviderSettings(mps)
                .build();
        when(appSettingService.getAppSettings()).thenReturn(settings);
    }

    @Nested
    @DisplayName("Provider Registration on Refresh")
    class RegistrationTests {

        @Test
        @DisplayName("Registers parsers for all valid provider configs")
        void refresh_validConfigs_allRegistered() {
            configureProviders(List.of(
                    CustomMetadataProviderConfig.builder()
                            .id("provider-1").name("Provider One")
                            .baseUrl("https://one.example.com").enabled(true).build(),
                    CustomMetadataProviderConfig.builder()
                            .id("provider-2").name("Provider Two")
                            .baseUrl("https://two.example.com").enabled(true).build()
            ));

            registry.refresh();

            assertThat(registry.getAllParsers()).hasSize(2);
            assertThat(registry.getParser("provider-1")).isNotNull();
            assertThat(registry.getParser("provider-2")).isNotNull();
        }

        @Test
        @DisplayName("Skips providers with null id")
        void refresh_nullId_skipped() {
            configureProviders(List.of(
                    CustomMetadataProviderConfig.builder()
                            .id(null).name("Bad Provider")
                            .baseUrl("https://example.com").enabled(true).build(),
                    CustomMetadataProviderConfig.builder()
                            .id("valid-id").name("Good Provider")
                            .baseUrl("https://example.com").enabled(true).build()
            ));

            registry.refresh();

            assertThat(registry.getAllParsers()).hasSize(1);
            assertThat(registry.getParser("valid-id")).isNotNull();
        }

        @Test
        @DisplayName("Skips providers with null baseUrl")
        void refresh_nullBaseUrl_skipped() {
            configureProviders(List.of(
                    CustomMetadataProviderConfig.builder()
                            .id("no-url").name("No URL Provider")
                            .baseUrl(null).enabled(true).build()
            ));

            registry.refresh();

            assertThat(registry.getAllParsers()).isEmpty();
        }

        @Test
        @DisplayName("Refresh replaces previously registered parsers entirely")
        void refresh_calledTwice_replacesOldParsers() {
            configureProviders(List.of(
                    CustomMetadataProviderConfig.builder()
                            .id("old-provider").name("Old")
                            .baseUrl("https://old.example.com").enabled(true).build()
            ));
            registry.refresh();
            assertThat(registry.getParser("old-provider")).isNotNull();

            // Reconfigure with a different set of providers
            configureProviders(List.of(
                    CustomMetadataProviderConfig.builder()
                            .id("new-provider").name("New")
                            .baseUrl("https://new.example.com").enabled(true).build()
            ));
            registry.refresh();

            assertThat(registry.getParser("old-provider")).isNull();
            assertThat(registry.getParser("new-provider")).isNotNull();
            assertThat(registry.getAllParsers()).hasSize(1);
        }

        @Test
        @DisplayName("Handles empty provider list gracefully")
        void refresh_emptyList_noProviders() {
            configureProviders(List.of());

            registry.refresh();

            assertThat(registry.getAllParsers()).isEmpty();
        }

        @Test
        @DisplayName("Handles null customProviders list gracefully")
        void refresh_nullCustomProviders_noProviders() {
            MetadataProviderSettings mps = new MetadataProviderSettings();
            mps.setCustomProviders(null);
            AppSettings settings = AppSettings.builder()
                    .metadataProviderSettings(mps)
                    .build();
            when(appSettingService.getAppSettings()).thenReturn(settings);

            registry.refresh();

            assertThat(registry.getAllParsers()).isEmpty();
        }

        @Test
        @DisplayName("Handles null MetadataProviderSettings gracefully")
        void refresh_nullMetadataProviderSettings_noProviders() {
            AppSettings settings = AppSettings.builder()
                    .metadataProviderSettings(null)
                    .build();
            when(appSettingService.getAppSettings()).thenReturn(settings);

            registry.refresh();

            assertThat(registry.getAllParsers()).isEmpty();
        }
    }

    @Nested
    @DisplayName("Enabled Provider Filtering")
    class EnabledFilteringTests {

        @Test
        @DisplayName("getEnabledParsers returns only providers marked as enabled in settings")
        void getEnabledParsers_mixedEnabledDisabled_onlyReturnsEnabled() {
            configureProviders(List.of(
                    CustomMetadataProviderConfig.builder()
                            .id("enabled-1").name("Enabled One")
                            .baseUrl("https://one.example.com").enabled(true).build(),
                    CustomMetadataProviderConfig.builder()
                            .id("disabled-1").name("Disabled One")
                            .baseUrl("https://two.example.com").enabled(false).build(),
                    CustomMetadataProviderConfig.builder()
                            .id("enabled-2").name("Enabled Two")
                            .baseUrl("https://three.example.com").enabled(true).build()
            ));

            registry.refresh();

            Collection<CustomBookParser> enabled = registry.getEnabledParsers();
            assertThat(enabled).hasSize(2);
            assertThat(enabled).allSatisfy(parser ->
                    assertThat(parser.getProviderId()).startsWith("enabled-"));
        }

        @Test
        @DisplayName("isProviderEnabled returns false for disabled providers")
        void isProviderEnabled_disabledProvider_returnsFalse() {
            configureProviders(List.of(
                    CustomMetadataProviderConfig.builder()
                            .id("disabled-provider").name("Disabled")
                            .baseUrl("https://example.com").enabled(false).build()
            ));

            registry.refresh();

            assertThat(registry.isProviderEnabled("disabled-provider")).isFalse();
        }

        @Test
        @DisplayName("isProviderEnabled returns true for enabled providers")
        void isProviderEnabled_enabledProvider_returnsTrue() {
            configureProviders(List.of(
                    CustomMetadataProviderConfig.builder()
                            .id("enabled-provider").name("Enabled")
                            .baseUrl("https://example.com").enabled(true).build()
            ));

            registry.refresh();

            assertThat(registry.isProviderEnabled("enabled-provider")).isTrue();
        }

        @Test
        @DisplayName("isProviderEnabled returns false for unknown provider IDs")
        void isProviderEnabled_unknownId_returnsFalse() {
            configureProviders(List.of());
            registry.refresh();

            assertThat(registry.isProviderEnabled("nonexistent")).isFalse();
        }
    }

    @Nested
    @DisplayName("Provider Lookup")
    class LookupTests {

        @Test
        @DisplayName("getParser returns null for unregistered provider ID")
        void getParser_unknownId_returnsNull() {
            configureProviders(List.of());
            registry.refresh();

            assertThat(registry.getParser("nonexistent")).isNull();
        }

        @Test
        @DisplayName("Registered parser preserves provider identity from config")
        void getParser_registeredId_parserHasCorrectIdentity() {
            configureProviders(List.of(
                    CustomMetadataProviderConfig.builder()
                            .id("my-provider").name("My Provider")
                            .baseUrl("https://example.com").enabled(true).build()
            ));

            registry.refresh();

            CustomBookParser parser = registry.getParser("my-provider");
            assertThat(parser.getProviderId()).isEqualTo("my-provider");
            assertThat(parser.getProviderName()).isEqualTo("My Provider");
        }
    }
}
