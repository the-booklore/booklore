package com.adityachandel.booklore.model.dto.request;

import com.adityachandel.booklore.model.enums.TaskType;
import com.adityachandel.booklore.task.tasks.options.ClearCbxCacheOptions;
import com.adityachandel.booklore.task.tasks.options.ClearPdfCacheOptions;
import com.adityachandel.booklore.task.tasks.options.LibraryRescanOptions;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TaskCreateRequest {
    private String taskId;
    private TaskType taskType;

    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "taskType", include = JsonTypeInfo.As.EXTERNAL_PROPERTY)
    @JsonSubTypes({
            @JsonSubTypes.Type(value = ClearCbxCacheOptions.class, name = "CLEAR_CBX_CACHE"),
            @JsonSubTypes.Type(value = ClearPdfCacheOptions.class, name = "CLEAR_PDF_CACHE"),
            @JsonSubTypes.Type(value = LibraryRescanOptions.class, name = "RE_SCAN_LIBRARY")
    })
    private Object options;

    public <T> T getOptions(Class<T> optionsClass) {
        if (options == null) {
            return null;
        }
        if (optionsClass.isInstance(options)) {
            return optionsClass.cast(options);
        }
        ObjectMapper mapper = new ObjectMapper();
        return mapper.convertValue(options, optionsClass);
    }
}
