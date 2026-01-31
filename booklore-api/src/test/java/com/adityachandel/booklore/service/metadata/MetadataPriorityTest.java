package com.adityachandel.booklore.service.metadata;

import com.adityachandel.booklore.model.dto.BookMetadata;
import com.adityachandel.booklore.model.dto.request.MetadataRefreshOptions;
import com.adityachandel.booklore.model.enums.MetadataProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class MetadataPriorityTest {

    @InjectMocks
    private MetadataRefreshService metadataRefreshService;

    @Test
    @DisplayName("P1 should have higher priority than P4")
    void buildFetchMetadata_shouldRespectPriority_P1_over_P4() {
        Long bookId = 1L;

        // Setup Options with P1=Google, P4=Amazon
        MetadataRefreshOptions refreshOptions = new MetadataRefreshOptions();
        MetadataRefreshOptions.FieldProvider titleProvider = new MetadataRefreshOptions.FieldProvider();
        titleProvider.setP1(MetadataProvider.Google);
        titleProvider.setP4(MetadataProvider.Amazon);
        
        MetadataRefreshOptions.FieldOptions fieldOptions = new MetadataRefreshOptions.FieldOptions();
        fieldOptions.setTitle(titleProvider);
        refreshOptions.setFieldOptions(fieldOptions);

        BookMetadata googleMetadata = BookMetadata.builder().title("Google Title (P1)").provider(MetadataProvider.Google).build();
        BookMetadata amazonMetadata = BookMetadata.builder().title("Amazon Title (P4)").provider(MetadataProvider.Amazon).build();
        
        Map<MetadataProvider, BookMetadata> metadataMap = Map.of(
                MetadataProvider.Google, googleMetadata,
                MetadataProvider.Amazon, amazonMetadata
        );

        BookMetadata result = metadataRefreshService.buildFetchMetadata(null, bookId, refreshOptions, metadataMap);

        assertThat(result.getTitle())
                .as("Should prefer P1 (Google) over P4 (Amazon)")
                .isEqualTo("Google Title (P1)");
    }
}
