package org.booklore.mapper;

import org.booklore.model.dto.Shelf;
import org.booklore.model.entity.ShelfEntity;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface ShelfMapper {

    @Mapping(source = "user.id", target = "userId")
    @Mapping(source = "public", target = "publicShelf")
    @Mapping(target = "bookCount", expression = "java(shelfEntity.getBookEntities() != null ? shelfEntity.getBookEntities().size() : 0)")
    Shelf toShelf(ShelfEntity shelfEntity);
}
