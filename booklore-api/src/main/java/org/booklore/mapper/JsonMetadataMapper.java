package org.booklore.mapper;

import org.booklore.model.dto.BookMetadata;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

public class JsonMetadataMapper {

    private static final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    public static BookMetadata parse(String json) {
        try {
            return objectMapper.readValue(json, BookMetadata.class);
        } catch (JsonProcessingException e) {
            return null;
        }
    }

    public static String toJson(BookMetadata metadata) {
        try {
            return objectMapper.writeValueAsString(metadata);
        } catch (JsonProcessingException e) {
            return null;
        }
    }
}
