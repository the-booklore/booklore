package com.adityachandel.booklore.mapper;

import com.adityachandel.booklore.model.dto.BookViewerSetting;
import com.adityachandel.booklore.model.entity.PdfViewerPreferencesEntity;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface BookViewerSettingMapper {

    BookViewerSetting toBookViewerSetting(PdfViewerPreferencesEntity entity);

}
