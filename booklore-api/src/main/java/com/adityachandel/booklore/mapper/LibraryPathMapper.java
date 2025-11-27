package com.adityachandel.booklore.mapper;

import com.adityachandel.booklore.model.dto.LibraryPath;
import com.adityachandel.booklore.model.entity.LibraryPathEntity;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface LibraryPathMapper {

    LibraryPath toLibraryPath(LibraryPathEntity libraryPathEntity);
}
