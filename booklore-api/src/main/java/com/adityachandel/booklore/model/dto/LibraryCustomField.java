package com.adityachandel.booklore.model.dto;

import com.adityachandel.booklore.model.enums.CustomFieldType;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;

@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class LibraryCustomField {
    private Long id;
    private Long libraryId;
    private String name;
    private CustomFieldType fieldType;
    private String defaultValue;
}
