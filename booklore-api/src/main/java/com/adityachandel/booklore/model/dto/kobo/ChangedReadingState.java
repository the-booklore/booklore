package com.adityachandel.booklore.model.dto.kobo;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonNaming(PropertyNamingStrategies.UpperCamelCaseStrategy.class)
public class ChangedReadingState implements Entitlement {

    private WrappedReadingState changedReadingState;
    
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @JsonNaming(PropertyNamingStrategies.UpperCamelCaseStrategy.class)
    public static class WrappedReadingState {
        private KoboReadingState readingState;
    }
}
