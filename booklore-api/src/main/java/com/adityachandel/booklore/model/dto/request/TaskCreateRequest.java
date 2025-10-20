package com.adityachandel.booklore.model.dto.request;

import com.adityachandel.booklore.model.enums.TaskType;
import com.adityachandel.booklore.task.options.LibraryRescanOptions;
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
            @JsonSubTypes.Type(value = LibraryRescanOptions.class, name = "REFRESH_LIBRARY_METADATA"),
            @JsonSubTypes.Type(value = MetadataRefreshRequest.class, name = "REFRESH_METADATA_MANUAL"),
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
