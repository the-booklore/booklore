package com.adityachandel.booklore.model.dto.request;

import com.adityachandel.booklore.model.enums.CustomFieldType;
import lombok.*;

@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class CreateLibraryCustomFieldRequest {
    private String name;
    private CustomFieldType fieldType;
    private String defaultValue;
}
