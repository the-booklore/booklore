package com.adityachandel.booklore.service.metadata;

import com.adityachandel.booklore.model.dto.BookMetadata;
import com.adityachandel.booklore.model.dto.request.MetadataRefreshOptions;
import com.adityachandel.booklore.model.enums.MetadataProvider;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.Map;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

@ExtendWith(MockitoExtension.class)
class MetadataRefreshServiceTest {

    @InjectMocks
    private MetadataRefreshService metadataRefreshService;

    @ParameterizedTest
    @MethodSource("provideNullCombinations")
    void buildFetchMetadata_shouldHandleNullOptions(
            MetadataRefreshOptions.FieldOptions fieldOptions,
            MetadataRefreshOptions.EnabledFields enabledFields
    ) {
        Long bookId = 1L;
        MetadataRefreshOptions refreshOptions = new MetadataRefreshOptions();
        refreshOptions.setFieldOptions(fieldOptions);
        refreshOptions.setEnabledFields(enabledFields);
        
        Map<MetadataProvider, BookMetadata> metadataMap = Collections.emptyMap();

        BookMetadata result = assertDoesNotThrow(() ->
                metadataRefreshService.buildFetchMetadata(bookId, refreshOptions, metadataMap)
        );

        assertThat(result).isNotNull();
        assertThat(result.getBookId()).isEqualTo(bookId);
    }

    private static Stream<Arguments> provideNullCombinations() {
        return Stream.of(
                Arguments.of(null, null),
                Arguments.of(new MetadataRefreshOptions.FieldOptions(), null),
                Arguments.of(null, new MetadataRefreshOptions.EnabledFields()),
                Arguments.of(new MetadataRefreshOptions.FieldOptions(), new MetadataRefreshOptions.EnabledFields())
        );
    }
}