package org.booklore.mapper;

import org.booklore.model.dto.ComicMetadata;
import org.booklore.model.entity.ComicMetadataEntity;
import org.mapstruct.Mapper;
import org.mapstruct.MappingTarget;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface ComicMetadataMapper {

    ComicMetadata toComicMetadata(ComicMetadataEntity entity);

    ComicMetadataEntity toComicMetadataEntity(ComicMetadata dto);

    void updateEntityFromDto(ComicMetadata dto, @MappingTarget ComicMetadataEntity entity);
}
