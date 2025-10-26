package com.adityachandel.booklore.mapper;

import com.adityachandel.booklore.model.dto.kobo.KoboReadingState;
import com.adityachandel.booklore.model.entity.KoboReadingStateEntity;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.regex.Pattern;

@Mapper(componentModel = "spring")
public interface KoboReadingStateMapper {

    ObjectMapper objectMapper = new ObjectMapper();
    Pattern PATTERN = Pattern.compile("^\"|\"$");

    @Mapping(target = "currentBookmarkJson", expression = "java(toJson(dto.getCurrentBookmark()))")
    @Mapping(target = "statisticsJson", expression = "java(toJson(dto.getStatistics()))")
    @Mapping(target = "statusInfoJson", expression = "java(toJson(dto.getStatusInfo()))")
    @Mapping(target = "entitlementId", expression = "java(cleanString(dto.getEntitlementId()))")
    @Mapping(target = "created", expression = "java(dto.getCreated())")
    @Mapping(target = "lastModified", expression = "java(dto.getLastModified())")
    @Mapping(target = "priorityTimestamp", expression = "java(dto.getPriorityTimestamp())")
    KoboReadingStateEntity toEntity(KoboReadingState dto);

    @Mapping(target = "currentBookmark", expression = "java(fromJson(entity.getCurrentBookmarkJson(), KoboReadingState.CurrentBookmark.class))")
    @Mapping(target = "statistics", expression = "java(fromJson(entity.getStatisticsJson(), KoboReadingState.Statistics.class))")
    @Mapping(target = "statusInfo", expression = "java(fromJson(entity.getStatusInfoJson(), KoboReadingState.StatusInfo.class))")
    @Mapping(target = "entitlementId", expression = "java(cleanString(entity.getEntitlementId()))")
    @Mapping(target = "created", expression = "java(entity.getCreated())")
    @Mapping(target = "lastModified", expression = "java(entity.getLastModified())")
    @Mapping(target = "priorityTimestamp", expression = "java(entity.getPriorityTimestamp())")
    KoboReadingState toDto(KoboReadingStateEntity entity);

    default String toJson(Object value) {
        try {
            return value == null ? null : objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize JSON", e);
        }
    }

    default <T> T fromJson(String json, Class<T> clazz) {
        try {
            return json == null ? null : objectMapper.readValue(json, clazz);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to deserialize JSON", e);
        }
    }

    default String cleanString(String value) {
        if (value == null) return null;
        return PATTERN.matcher(value).replaceAll("");
    }
}