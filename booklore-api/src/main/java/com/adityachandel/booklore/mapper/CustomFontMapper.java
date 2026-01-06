package com.adityachandel.booklore.mapper;

import com.adityachandel.booklore.model.dto.CustomFontDto;
import com.adityachandel.booklore.model.entity.CustomFontEntity;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface CustomFontMapper {

    CustomFontDto toDto(CustomFontEntity entity);
}
