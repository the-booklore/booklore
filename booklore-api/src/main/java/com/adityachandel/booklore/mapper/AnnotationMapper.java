package com.adityachandel.booklore.mapper;

import com.adityachandel.booklore.model.dto.Annotation;
import com.adityachandel.booklore.model.entity.AnnotationEntity;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface AnnotationMapper {

    @Mapping(source = "book.id", target = "bookId")
    @Mapping(source = "user.id", target = "userId")
    Annotation toDto(AnnotationEntity entity);
}
