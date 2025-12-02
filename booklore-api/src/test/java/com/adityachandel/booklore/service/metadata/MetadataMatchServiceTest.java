
package com.adityachandel.booklore.service.metadata;

import com.adityachandel.booklore.model.dto.settings.AppSettings;
import com.adityachandel.booklore.model.dto.settings.MetadataMatchWeights;
import com.adityachandel.booklore.model.entity.BookEntity;
import com.adityachandel.booklore.model.entity.BookMetadataEntity;
import com.adityachandel.booklore.service.appsettings.AppSettingService;
import com.adityachandel.booklore.service.book.BookQueryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MetadataMatchServiceTest {

    @Mock
    private AppSettingService appSettingsService;

    @Mock
    private BookQueryService bookQueryService;

    @InjectMocks
    private MetadataMatchService metadataMatchService;

    private MetadataMatchWeights weights;

    @BeforeEach
    void setUp() {
        weights = MetadataMatchWeights.builder()
                .title(10)
                .subtitle(5)
                .description(5)
                .authors(10)
                .build(); // other fields default to 0

        AppSettings appSettings = AppSettings.builder()
                .metadataMatchWeights(weights)
                .build();

        when(appSettingsService.getAppSettings()).thenReturn(appSettings);
    }

    @Test
    void calculateMatchScore_shouldScoreOnlyPresentFields() {
        BookMetadataEntity metadata = BookMetadataEntity.builder()
                .title("Some Title")
                .build(); 

        BookEntity book = BookEntity.builder()
                .metadata(metadata)
                .build();

        Float score = metadataMatchService.calculateMatchScore(book);
        
        assertEquals(10f / 30f * 100f, score, 0.01f);
    }
    
    @Test
    void calculateMatchScore_shouldScoreLockedEmptySubtitle() {
        BookMetadataEntity metadata = BookMetadataEntity.builder()
                .title("Some Title")
                .subtitleLocked(true) // Empty but locked
                .build();

        BookEntity book = BookEntity.builder()
                .metadata(metadata)
                .build();

        Float score = metadataMatchService.calculateMatchScore(book);

        assertEquals(50.0f, score, 0.01f);
    }

    @Test
    void calculateMatchScore_shouldScoreLockedNullSeriesNumber() {
         weights = MetadataMatchWeights.builder()
                .title(10)
                .seriesNumber(5)
                .build();
         
         // Total 15

        AppSettings appSettings = AppSettings.builder()
                .metadataMatchWeights(weights)
                .build();

        when(appSettingsService.getAppSettings()).thenReturn(appSettings);

        BookMetadataEntity metadata = BookMetadataEntity.builder()
                .title("Some Title")
                .seriesNumberLocked(true) // Null but locked
                .build();

        BookEntity book = BookEntity.builder()
                .metadata(metadata)
                .build();

        Float score = metadataMatchService.calculateMatchScore(book);

        assertEquals(100.0f, score, 0.01f);
    }
}
