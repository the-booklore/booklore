package com.adityachandel.booklore.mapper;

import com.adityachandel.booklore.model.dto.CustomFontDto;
import com.adityachandel.booklore.model.entity.CustomFontEntity;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface CustomFontMapper {

    @Mapping(target = "fontUrl", expression = "java(\"/api/v1/custom-fonts/\" + entity.getId() + \"/file\")")
    CustomFontDto toDto(CustomFontEntity entity);
}
