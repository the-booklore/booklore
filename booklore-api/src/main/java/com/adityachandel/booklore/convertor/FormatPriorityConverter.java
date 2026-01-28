package com.adityachandel.booklore.convertor;

import com.adityachandel.booklore.model.enums.BookFileType;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import lombok.extern.slf4j.Slf4j;

import java.util.Collections;
import java.util.List;

@Converter
@Slf4j
public class FormatPriorityConverter implements AttributeConverter<List<BookFileType>, String> {

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
            log.error("Error converting format priority list to JSON", e);
            return null;
        }
    }

    @Override
    public List<BookFileType> convertToEntityAttribute(String dbData) {
        if (dbData == null || dbData.isEmpty()) {
            return Collections.emptyList();
        }
        try {
            return objectMapper.readValue(dbData, LIST_TYPE_REF);
        } catch (JsonProcessingException e) {
            log.error("Error converting JSON to format priority list", e);
            return Collections.emptyList();
        }
    }
}
