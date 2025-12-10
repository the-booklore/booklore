package com.adityachandel.booklore.model.dto.kobo;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonNaming(PropertyNamingStrategies.UpperCamelCaseStrategy.class)
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class BookEntitlement {
    private ActivePeriod activePeriod;

    @JsonProperty("IsRemoved")
    private boolean isRemoved;

    private String status;

    @Builder.Default
    private String accessibility = "Full";

    private String crossRevisionId;
    private String revisionId;

    @JsonProperty("IsHiddenFromArchive")
    @Builder.Default
    private boolean isHiddenFromArchive = false;

    private String id;
    private String created;
    private String lastModified;

    @JsonProperty("IsLocked")
    @Builder.Default
    private boolean isLocked = false;

    @Builder.Default
    private String originCategory = "Imported";

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonNaming(PropertyNamingStrategies.UpperCamelCaseStrategy.class)
    @Builder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class ActivePeriod {
        private String from;
        private String to;
    }
}