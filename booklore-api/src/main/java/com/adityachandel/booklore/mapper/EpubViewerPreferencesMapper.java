package com.adityachandel.booklore.mapper;

import com.adityachandel.booklore.model.dto.EpubViewerPreferences;
import com.adityachandel.booklore.model.entity.EpubViewerPreferencesEntity;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface EpubViewerPreferencesMapper {

    EpubViewerPreferences toModel(EpubViewerPreferencesEntity entity);

    EpubViewerPreferencesEntity toEntity(EpubViewerPreferences entity);
}
