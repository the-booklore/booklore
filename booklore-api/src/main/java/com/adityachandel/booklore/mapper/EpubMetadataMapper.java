package com.adityachandel.booklore.mapper;

import com.adityachandel.booklore.model.dto.BookMetadata;
import com.adityachandel.booklore.model.dto.EpubMetadata;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;
import org.mapstruct.factory.Mappers;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface EpubMetadataMapper {

    EpubMetadataMapper INSTANCE = Mappers.getMapper(EpubMetadataMapper.class);

    EpubMetadata toEpubMetadata(BookMetadata bookMetadata);

    BookMetadata toBookMetadata(EpubMetadata epubMetadata);
}
