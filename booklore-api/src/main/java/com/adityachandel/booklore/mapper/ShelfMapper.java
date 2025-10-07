package com.adityachandel.booklore.mapper;

import com.adityachandel.booklore.model.dto.Shelf;
import com.adityachandel.booklore.model.entity.ShelfEntity;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface ShelfMapper {

    @Mapping(source = "user.id", target = "userId")
    Shelf toShelf(ShelfEntity shelfEntity);
}
