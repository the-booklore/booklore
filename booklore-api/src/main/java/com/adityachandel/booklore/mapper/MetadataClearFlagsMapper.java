package com.adityachandel.booklore.mapper;

import com.adityachandel.booklore.model.MetadataClearFlags;
import com.adityachandel.booklore.model.dto.request.BulkMetadataUpdateRequest;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface MetadataClearFlagsMapper {

    @Mapping(target = "publisher", source = "clearPublisher")
    @Mapping(target = "publishedDate", source = "clearPublishedDate")
    @Mapping(target = "seriesName", source = "clearSeriesName")
    @Mapping(target = "seriesTotal", source = "clearSeriesTotal")
    @Mapping(target = "language", source = "clearLanguage")
    @Mapping(target = "authors", source = "clearAuthors")
    @Mapping(target = "categories", source = "clearGenres")
    @Mapping(target = "moods", source = "clearMoods")
    @Mapping(target = "tags", source = "clearTags")
    MetadataClearFlags toClearFlags(BulkMetadataUpdateRequest request);
}
