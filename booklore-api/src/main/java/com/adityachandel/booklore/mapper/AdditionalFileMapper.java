package com.adityachandel.booklore.mapper;

import com.adityachandel.booklore.model.dto.BookFile;
import com.adityachandel.booklore.model.entity.BookFileEntity;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;

import java.util.List;

@Mapper(componentModel = "spring")
public interface AdditionalFileMapper {

    @Mapping(source = "book.id", target = "bookId")
    @Mapping(source = ".", target = "filePath", qualifiedByName = "mapFilePath")
    @Mapping(source = "bookFormat", target = "isBook")
    BookFile toAdditionalFile(BookFileEntity entity);

    List<BookFile> toAdditionalFiles(List<BookFileEntity> entities);

    @Named("mapFilePath")
    default String mapFilePath(BookFileEntity entity) {
        if (entity == null) return null;
        try {
            return entity.getFullFilePath().toString();
        } catch (Exception e) {
            return null;
        }
    }
}