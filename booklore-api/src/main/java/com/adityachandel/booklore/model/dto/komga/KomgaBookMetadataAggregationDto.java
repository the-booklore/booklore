package com.adityachandel.booklore.model.dto.komga;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class KomgaBookMetadataAggregationDto {
    @Builder.Default
    private List<KomgaAuthorDto> authors = new ArrayList<>();
    
    @Builder.Default
    private List<String> tags = new ArrayList<>();
    
    private String releaseDate;
    
    private String summary;
    
    private Boolean summaryLock;
}
