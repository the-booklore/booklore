package com.adityachandel.booklore.service.metadata;

import com.adityachandel.booklore.model.dto.BookMetadata;
import com.adityachandel.booklore.model.dto.request.MetadataRefreshOptions;
import com.adityachandel.booklore.model.enums.MetadataProvider;
import com.adityachandel.booklore.model.enums.MetadataReplaceMode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class MetadataRefreshServiceTitleUpdateTest {

    @InjectMocks
    private MetadataRefreshService metadataRefreshService;

    @Nested
    @DisplayName("Title Update Bug Tests")
    class TitleUpdateBugTests {

        @Test
        @DisplayName("Bug: Title should be updated when EnabledFields is null (defaults should enable title)")
        void buildFetchMetadata_withNullEnabledFields_shouldFetchTitle() {
            Long bookId = 1L;
            MetadataRefreshOptions refreshOptions = new MetadataRefreshOptions();
            refreshOptions.setEnabledFields(null); // Simulating default state
            
            MetadataRefreshOptions.FieldProvider titleProvider = new MetadataRefreshOptions.FieldProvider();
            titleProvider.setP1(MetadataProvider.Google);
            
            MetadataRefreshOptions.FieldOptions fieldOptions = new MetadataRefreshOptions.FieldOptions();
            fieldOptions.setTitle(titleProvider);
            refreshOptions.setFieldOptions(fieldOptions);
            
            BookMetadata googleMetadata = BookMetadata.builder()
                    .title("Fetched Title From Google")
                    .provider(MetadataProvider.Google)
                    .build();
            
            Map<MetadataProvider, BookMetadata> metadataMap = Map.of(MetadataProvider.Google, googleMetadata);

            BookMetadata result = metadataRefreshService.buildFetchMetadata(null, bookId, refreshOptions, metadataMap);

            assertThat(result.getTitle())
                    .as("Title should be fetched when EnabledFields is null (defaults should enable title)")
                    .isEqualTo("Fetched Title From Google");
        }

        @Test
        @DisplayName("Bug: Title should be updated when EnabledFields is default-constructed")
        void buildFetchMetadata_withDefaultEnabledFields_shouldFetchTitle() {
            Long bookId = 1L;
            MetadataRefreshOptions refreshOptions = new MetadataRefreshOptions();
            refreshOptions.setEnabledFields(new MetadataRefreshOptions.EnabledFields()); // Default constructor
            
            MetadataRefreshOptions.FieldProvider titleProvider = new MetadataRefreshOptions.FieldProvider();
            titleProvider.setP1(MetadataProvider.Amazon);
            
            MetadataRefreshOptions.FieldOptions fieldOptions = new MetadataRefreshOptions.FieldOptions();
            fieldOptions.setTitle(titleProvider);
            refreshOptions.setFieldOptions(fieldOptions);
            
            BookMetadata amazonMetadata = BookMetadata.builder()
                    .title("Fetched Title From Amazon")
                    .provider(MetadataProvider.Amazon)
                    .build();
            
            Map<MetadataProvider, BookMetadata> metadataMap = Map.of(MetadataProvider.Amazon, amazonMetadata);

            BookMetadata result = metadataRefreshService.buildFetchMetadata(null, bookId, refreshOptions, metadataMap);

            assertThat(result.getTitle())
                    .as("Title should be fetched when EnabledFields is default-constructed (all fields should be enabled by default)")
                    .isEqualTo("Fetched Title From Amazon");
        }

        @Test
        @DisplayName("EnabledFields default constructor should set all fields to true")
        void enabledFields_defaultConstructor_shouldHaveAllFieldsTrue() {
            MetadataRefreshOptions.EnabledFields enabledFields = new MetadataRefreshOptions.EnabledFields();

            assertThat(enabledFields.isTitle())
                    .as("title should be enabled by default")
                    .isTrue();
            assertThat(enabledFields.isSubtitle())
                    .as("subtitle should be enabled by default")
                    .isTrue();
            assertThat(enabledFields.isDescription())
                    .as("description should be enabled by default")
                    .isTrue();
            assertThat(enabledFields.isAuthors())
                    .as("authors should be enabled by default")
                    .isTrue();
            assertThat(enabledFields.isPublisher())
                    .as("publisher should be enabled by default")
                    .isTrue();
            assertThat(enabledFields.isPublishedDate())
                    .as("publishedDate should be enabled by default")
                    .isTrue();
            assertThat(enabledFields.isSeriesName())
                    .as("seriesName should be enabled by default")
                    .isTrue();
            assertThat(enabledFields.isSeriesNumber())
                    .as("seriesNumber should be enabled by default")
                    .isTrue();
            assertThat(enabledFields.isSeriesTotal())
                    .as("seriesTotal should be enabled by default")
                    .isTrue();
            assertThat(enabledFields.isIsbn13())
                    .as("isbn13 should be enabled by default")
                    .isTrue();
            assertThat(enabledFields.isIsbn10())
                    .as("isbn10 should be enabled by default")
                    .isTrue();
            assertThat(enabledFields.isLanguage())
                    .as("language should be enabled by default")
                    .isTrue();
            assertThat(enabledFields.isCategories())
                    .as("categories should be enabled by default")
                    .isTrue();
            assertThat(enabledFields.isCover())
                    .as("cover should be enabled by default")
                    .isTrue();
            assertThat(enabledFields.isPageCount())
                    .as("pageCount should be enabled by default")
                    .isTrue();
        }
    }

    @Nested
    @DisplayName("Explicit Field Enable/Disable Tests")
    class ExplicitFieldControlTests {

        @Test
        @DisplayName("Title should be updated when explicitly enabled")
        void buildFetchMetadata_withTitleExplicitlyEnabled_shouldFetchTitle() {
            Long bookId = 1L;
            MetadataRefreshOptions refreshOptions = new MetadataRefreshOptions();
            
            MetadataRefreshOptions.EnabledFields enabledFields = new MetadataRefreshOptions.EnabledFields();
            enabledFields.setTitle(true);
            refreshOptions.setEnabledFields(enabledFields);
            
            MetadataRefreshOptions.FieldProvider titleProvider = new MetadataRefreshOptions.FieldProvider();
            titleProvider.setP1(MetadataProvider.Google);
            
            MetadataRefreshOptions.FieldOptions fieldOptions = new MetadataRefreshOptions.FieldOptions();
            fieldOptions.setTitle(titleProvider);
            refreshOptions.setFieldOptions(fieldOptions);
            
            BookMetadata googleMetadata = BookMetadata.builder()
                    .title("Explicitly Enabled Title")
                    .provider(MetadataProvider.Google)
                    .build();
            
            Map<MetadataProvider, BookMetadata> metadataMap = Map.of(MetadataProvider.Google, googleMetadata);

            BookMetadata result = metadataRefreshService.buildFetchMetadata(null, bookId, refreshOptions, metadataMap);

            assertThat(result.getTitle()).isEqualTo("Explicitly Enabled Title");
        }

        @Test
        @DisplayName("Title should NOT be updated when explicitly disabled")
        void buildFetchMetadata_withTitleExplicitlyDisabled_shouldNotFetchTitle() {
            Long bookId = 1L;
            MetadataRefreshOptions refreshOptions = new MetadataRefreshOptions();
            
            MetadataRefreshOptions.EnabledFields enabledFields = new MetadataRefreshOptions.EnabledFields();
            enabledFields.setTitle(false);
            refreshOptions.setEnabledFields(enabledFields);
            
            MetadataRefreshOptions.FieldProvider titleProvider = new MetadataRefreshOptions.FieldProvider();
            titleProvider.setP1(MetadataProvider.Google);
            
            MetadataRefreshOptions.FieldOptions fieldOptions = new MetadataRefreshOptions.FieldOptions();
            fieldOptions.setTitle(titleProvider);
            refreshOptions.setFieldOptions(fieldOptions);
            
            BookMetadata googleMetadata = BookMetadata.builder()
                    .title("Should Not Be Set")
                    .provider(MetadataProvider.Google)
                    .build();
            
            Map<MetadataProvider, BookMetadata> metadataMap = Map.of(MetadataProvider.Google, googleMetadata);

            BookMetadata result = metadataRefreshService.buildFetchMetadata(null, bookId, refreshOptions, metadataMap);

            assertThat(result.getTitle())
                    .as("Title should be null when explicitly disabled")
                    .isNull();
        }
    }

    @Nested
    @DisplayName("Replace All Mode Tests")
    class ReplaceAllModeTests {

        @Test
        @DisplayName("All enabled fields should be fetched with default EnabledFields")
        void buildFetchMetadata_withDefaultEnabledFields_shouldFetchAllFields() {
            Long bookId = 1L;
            MetadataRefreshOptions refreshOptions = new MetadataRefreshOptions();

            MetadataRefreshOptions.FieldProvider provider = new MetadataRefreshOptions.FieldProvider();
            provider.setP1(MetadataProvider.Google);
            
            MetadataRefreshOptions.FieldOptions fieldOptions = new MetadataRefreshOptions.FieldOptions();
            fieldOptions.setTitle(provider);
            fieldOptions.setDescription(provider);
            fieldOptions.setPublisher(provider);
            refreshOptions.setFieldOptions(fieldOptions);
            
            BookMetadata googleMetadata = BookMetadata.builder()
                    .title("New Title")
                    .description("New Description")
                    .publisher("New Publisher")
                    .provider(MetadataProvider.Google)
                    .build();
            
            Map<MetadataProvider, BookMetadata> metadataMap = Map.of(MetadataProvider.Google, googleMetadata);

            BookMetadata result = metadataRefreshService.buildFetchMetadata(null, bookId, refreshOptions, metadataMap);

            assertThat(result.getTitle())
                    .as("Title should be fetched with default enabled fields")
                    .isEqualTo("New Title");
            assertThat(result.getDescription())
                    .as("Description should be fetched with default enabled fields")
                    .isEqualTo("New Description");
            assertThat(result.getPublisher())
                    .as("Publisher should be fetched with default enabled fields")
                    .isEqualTo("New Publisher");
        }

        @Test
        @DisplayName("Bug reproduction: Fetched metadata should include title when using default options")
        void buildFetchMetadata_simulateManualRefresh_shouldIncludeTitleInFetchedMetadata() {
            Long bookId = 224L; // Using the same book ID from the user's log
            
            MetadataRefreshOptions refreshOptions = new MetadataRefreshOptions();
            assertThat(refreshOptions.getEnabledFields().isTitle())
                    .as("Title should be enabled by default in refresh options")
                    .isTrue();
            
            MetadataRefreshOptions.FieldProvider comicvineProvider = new MetadataRefreshOptions.FieldProvider();
            comicvineProvider.setP1(MetadataProvider.Comicvine);
            
            MetadataRefreshOptions.FieldOptions fieldOptions = new MetadataRefreshOptions.FieldOptions();
            fieldOptions.setTitle(comicvineProvider);
            refreshOptions.setFieldOptions(fieldOptions);
            
            BookMetadata comicvineMetadata = BookMetadata.builder()
                    .title("V for Vendetta #3")  // The correct title that should replace the file-based one
                    .provider(MetadataProvider.Comicvine)
                    .build();
            
            Map<MetadataProvider, BookMetadata> metadataMap = Map.of(MetadataProvider.Comicvine, comicvineMetadata);

            BookMetadata result = metadataRefreshService.buildFetchMetadata(null, bookId, refreshOptions, metadataMap);

            assertThat(result.getTitle())
                    .as("Fetched metadata should include the title from Comicvine")
                    .isEqualTo("V for Vendetta #3");
        }
    }

    @Nested
    @DisplayName("ReplaceMode Configuration Tests")
    class ReplaceModeConfigTests {

        @Test
        @DisplayName("MetadataRefreshOptions should have replaceMode field with REPLACE_MISSING as default")
        void metadataRefreshOptions_shouldHaveReplaceModeWithDefaultValue() {
            MetadataRefreshOptions options = new MetadataRefreshOptions();
            
            assertThat(options.getReplaceMode())
                    .as("Default replaceMode should be REPLACE_MISSING")
                    .isEqualTo(MetadataReplaceMode.REPLACE_MISSING);
        }

        @Test
        @DisplayName("User can set replaceMode to REPLACE_ALL to overwrite existing metadata")
        void metadataRefreshOptions_canBeSetToReplaceAll() {
            MetadataRefreshOptions options = new MetadataRefreshOptions();
            options.setReplaceMode(MetadataReplaceMode.REPLACE_ALL);
            
            assertThat(options.getReplaceMode())
                    .as("replaceMode should be configurable to REPLACE_ALL")
                    .isEqualTo(MetadataReplaceMode.REPLACE_ALL);
        }

        @Test
        @DisplayName("Builder should use REPLACE_MISSING as default replaceMode")
        void metadataRefreshOptions_builderShouldUseDefaultReplaceMode() {
            MetadataRefreshOptions options = MetadataRefreshOptions.builder().build();
            
            assertThat(options.getReplaceMode())
                    .as("Builder should default replaceMode to REPLACE_MISSING")
                    .isEqualTo(MetadataReplaceMode.REPLACE_MISSING);
        }

        @Test
        @DisplayName("Builder can override replaceMode to REPLACE_ALL")
        void metadataRefreshOptions_builderCanOverrideReplaceMode() {
            MetadataRefreshOptions options = MetadataRefreshOptions.builder()
                    .replaceMode(MetadataReplaceMode.REPLACE_ALL)
                    .build();
            
            assertThat(options.getReplaceMode())
                    .as("Builder should allow setting replaceMode to REPLACE_ALL")
                    .isEqualTo(MetadataReplaceMode.REPLACE_ALL);
        }
    }
}
