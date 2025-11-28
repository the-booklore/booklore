package com.adityachandel.booklore.model.dto;

import com.adityachandel.booklore.model.enums.AdditionalFileType;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AdditionalFile {
    private Long id;
    private Long bookId;
    private String fileName;
    private String filePath;
    private String fileSubPath;
    private AdditionalFileType additionalFileType;
    private Long fileSizeKb;
    private String description;
    private Instant addedOn;
}