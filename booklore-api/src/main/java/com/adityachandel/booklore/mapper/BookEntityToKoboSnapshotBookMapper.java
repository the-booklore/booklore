package com.adityachandel.booklore.mapper;

import com.adityachandel.booklore.model.entity.BookEntity;
import com.adityachandel.booklore.model.entity.KoboSnapshotBookEntity;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface BookEntityToKoboSnapshotBookMapper {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "bookId", expression = "java(book.getId())")
    @Mapping(target = "synced", constant = "false")
    KoboSnapshotBookEntity toKoboSnapshotBook(BookEntity book);
}
