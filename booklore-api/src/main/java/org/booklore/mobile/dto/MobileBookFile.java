package org.booklore.mobile.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;

@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class MobileBookFile {
    private Long id;
    private Long bookId;
    private String fileName;
    private boolean isBook;
    private boolean folderBased;
    private String bookType;
    private String archiveType;
    private Long fileSizeKb;
    private String extension;
    private Instant addedOn;
    private boolean isPrimary;
}
