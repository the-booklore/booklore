package com.adityachandel.booklore.convertor;

import com.adityachandel.booklore.model.enums.BookFileType;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

@Converter
@Slf4j
public class BookFileTypeListConverter implements AttributeConverter<List<BookFileType>, String> {

    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static final TypeReference<List<BookFileType>> LIST_TYPE_REF = new TypeReference<>() {};

    @Override
    public String convertToDatabaseColumn(List<BookFileType> attribute) {
        if (attribute == null || attribute.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(attribute);
        } catch (JsonProcessingException e) {
            log.error("Failed to convert preferred book format order to JSON: {}", attribute, e);
            throw new RuntimeException("Error converting book format order to JSON", e);
        }
    }

    @Override
    public List<BookFileType> convertToEntityAttribute(String dbData) {
        if (dbData == null || dbData.trim().isEmpty()) {
            return null;
        }
        try {
            return objectMapper.readValue(dbData, LIST_TYPE_REF);
        } catch (JsonProcessingException e) {
            log.error("Failed to parse preferred book format order JSON: {}", dbData, e);
            return null;
        }
    }
}
