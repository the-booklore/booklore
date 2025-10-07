package com.adityachandel.booklore.mapper;

import com.adityachandel.booklore.model.dto.BookMetadata;
import com.adityachandel.booklore.model.entity.BookMetadataEntity;
import org.mapstruct.*;

@Mapper(componentModel = "spring", uses = {AuthorMapper.class, CategoryMapper.class, MoodMapper.class, TagMapper.class})
public interface BookMetadataMapper {

    @AfterMapping
    default void mapWithDescriptionCondition(BookMetadataEntity bookMetadataEntity, @MappingTarget BookMetadata bookMetadata, @Context boolean includeDescription) {
        if (includeDescription) {
            bookMetadata.setDescription(bookMetadataEntity.getDescription());
        } else {
            bookMetadata.setDescription(null);
        }
    }

    @Named("withRelations")
    @Mapping(target = "description", ignore = true)
    BookMetadata toBookMetadata(BookMetadataEntity bookMetadataEntity, @Context boolean includeDescription);

    @Named("withoutRelations")
    @Mapping(target = "description", ignore = true)
    @Mapping(target = "authors", ignore = true)
    @Mapping(target = "categories", ignore = true)
    @Mapping(target = "moods", ignore = true)
    @Mapping(target = "tags", ignore = true)
    BookMetadata toBookMetadataWithoutRelations(BookMetadataEntity bookMetadataEntity, @Context boolean includeDescription);

}