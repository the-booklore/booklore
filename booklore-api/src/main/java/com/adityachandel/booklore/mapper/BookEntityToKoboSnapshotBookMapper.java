package com.adityachandel.booklore.mapper;

import com.adityachandel.booklore.model.entity.BookEntity;
import com.adityachandel.booklore.model.entity.KoboSnapshotBookEntity;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface BookEntityToKoboSnapshotBookMapper {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "bookId", expression = "java(book.getId())")
    @Mapping(target = "synced", constant = "false")
    KoboSnapshotBookEntity toKoboSnapshotBook(BookEntity book);
}
