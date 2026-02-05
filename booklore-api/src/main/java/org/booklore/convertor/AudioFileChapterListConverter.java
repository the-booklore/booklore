package org.booklore.convertor;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import lombok.extern.slf4j.Slf4j;
import org.booklore.model.entity.BookFileEntity.AudioFileChapter;

import java.util.List;

@Converter
@Slf4j
public class AudioFileChapterListConverter implements AttributeConverter<List<AudioFileChapter>, String> {

    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static final TypeReference<List<AudioFileChapter>> LIST_TYPE_REF = new TypeReference<>() {};

    @Override
    public String convertToDatabaseColumn(List<AudioFileChapter> attribute) {
        if (attribute == null || attribute.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(attribute);
        } catch (JsonProcessingException e) {
            log.error("Error converting chapter list to JSON", e);
            return null;
        }
    }

    @Override
    public List<AudioFileChapter> convertToEntityAttribute(String dbData) {
        if (dbData == null || dbData.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.readValue(dbData, LIST_TYPE_REF);
        } catch (JsonProcessingException e) {
            log.error("Error converting JSON to chapter list", e);
            return null;
        }
    }
}
