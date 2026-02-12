package org.booklore.controller;

import org.booklore.model.dto.BookMetadata;
import org.booklore.model.dto.external.ExternalProviderCapabilities;
import org.booklore.model.dto.settings.CustomMetadataProviderConfig;
import org.booklore.service.metadata.BookMetadataService;
import org.booklore.service.metadata.parser.custom.CustomProviderRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CustomProviderControllerTest {

    @Mock
    private CustomProviderRegistry customProviderRegistry;

    @Mock
    private BookMetadataService bookMetadataService;

    @InjectMocks
    private CustomProviderController controller;

    @Nested
    @DisplayName("validateProvider")
    class ValidateProviderTests {

        @Test
        @DisplayName("Returns 200 with capabilities when provider is reachable")
        void validateProvider_reachable_returns200WithCapabilities() {
            CustomMetadataProviderConfig config = CustomMetadataProviderConfig.builder()
                    .id("test-id")
                    .baseUrl("https://provider.example.com")
                    .build();

            ExternalProviderCapabilities capabilities = ExternalProviderCapabilities.builder()
                    .providerId("ext-id")
                    .providerName("Test Provider")
                    .version("1.0")
                    .capabilities(ExternalProviderCapabilities.Capabilities.builder()
                            .supportsMetadata(true)
                            .supportsCovers(true)
                            .build())
                    .build();

            when(customProviderRegistry.validateProvider(config)).thenReturn(capabilities);

            ResponseEntity<ExternalProviderCapabilities> response = controller.validateProvider(config);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().getProviderName()).isEqualTo("Test Provider");
            assertThat(response.getBody().getCapabilities().getSupportsMetadata()).isTrue();
        }

        @Test
        @DisplayName("Returns 502 when provider is unreachable")
        void validateProvider_unreachable_returns502() {
            CustomMetadataProviderConfig config = CustomMetadataProviderConfig.builder()
                    .id("test-id")
                    .baseUrl("https://unreachable.example.com")
                    .build();

            when(customProviderRegistry.validateProvider(config)).thenReturn(null);

            ResponseEntity<ExternalProviderCapabilities> response = controller.validateProvider(config);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY);
            assertThat(response.getBody()).isNull();
        }
    }

    @Nested
    @DisplayName("refreshRegistry")
    class RefreshRegistryTests {

        @Test
        @DisplayName("Returns 204 and delegates to registry refresh")
        void refreshRegistry_delegatesAndReturns204() {
            ResponseEntity<Void> response = controller.refreshRegistry();

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
            verify(customProviderRegistry).refresh();
        }
    }

    @Nested
    @DisplayName("getDetailedCustomProviderMetadata")
    class DetailedMetadataTests {

        @Test
        @DisplayName("Returns 200 with metadata when provider and item are found")
        void getDetailed_found_returns200() {
            BookMetadata metadata = BookMetadata.builder()
                    .title("Detailed Book")
                    .isbn13("9781234567890")
                    .build();

            when(bookMetadataService.getDetailedCustomProviderMetadata("provider-1", "item-42"))
                    .thenReturn(metadata);

            ResponseEntity<BookMetadata> response = controller.getDetailedCustomProviderMetadata("provider-1", "item-42");

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().getTitle()).isEqualTo("Detailed Book");
        }

        @Test
        @DisplayName("Returns 404 when provider is not found")
        void getDetailed_providerNotFound_returns404() {
            when(bookMetadataService.getDetailedCustomProviderMetadata("nonexistent", "item-42"))
                    .thenReturn(null);

            ResponseEntity<BookMetadata> response = controller.getDetailedCustomProviderMetadata("nonexistent", "item-42");

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
            assertThat(response.getBody()).isNull();
        }

        @Test
        @DisplayName("Returns 404 when item is not found within a valid provider")
        void getDetailed_itemNotFound_returns404() {
            when(bookMetadataService.getDetailedCustomProviderMetadata("provider-1", "nonexistent-item"))
                    .thenReturn(null);

            ResponseEntity<BookMetadata> response = controller.getDetailedCustomProviderMetadata("provider-1", "nonexistent-item");

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }
    }
}
