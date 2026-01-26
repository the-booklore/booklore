package com.adityachandel.booklore.model.dto;

import com.adityachandel.booklore.model.enums.BookFileType;
import com.adityachandel.booklore.util.ArchiveUtils;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;

@Builder
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class BookFile {
    private Long id;
    private Long bookId;
    private String fileName;
    private String filePath;
    private String fileSubPath;
    private boolean isBook;
    private boolean folderBased;
    private BookFileType bookType;
    private ArchiveUtils.ArchiveType archiveType;
    private Long fileSizeKb;
    private String extension;
    private String description;
    private Instant addedOn;
}