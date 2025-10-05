package com.adityachandel.booklore.mapper.v2;

import com.adityachandel.booklore.mapper.ShelfMapper;
import com.adityachandel.booklore.model.dto.Book;
import com.adityachandel.booklore.model.dto.BookMetadata;
import com.adityachandel.booklore.model.dto.LibraryPath;
import com.adityachandel.booklore.model.entity.*;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;

import java.util.Set;
import java.util.stream.Collectors;

@Mapper(componentModel = "spring", uses = ShelfMapper.class)
public interface BookMapperV2 {

    @Mapping(source = "library.id", target = "libraryId")
    @Mapping(source = "library.name", target = "libraryName")
    @Mapping(source = "libraryPath", target = "libraryPath", qualifiedByName = "mapLibraryPathIdOnly")
    @Mapping(target = "metadata", qualifiedByName = "mapMetadata")
    Book toDTO(BookEntity bookEntity);

    @Named("mapMetadata")
    @Mapping(target = "authors", source = "authors", qualifiedByName = "mapAuthors")
    @Mapping(target = "categories", source = "categories", qualifiedByName = "mapCategories")
    @Mapping(target = "moods", source = "moods", qualifiedByName = "mapMoods")
    @Mapping(target = "tags", source = "tags", qualifiedByName = "mapTags")
    BookMetadata mapMetadata(BookMetadataEntity metadataEntity);

    @Named("mapAuthors")
    default Set<String> mapAuthors(Set<AuthorEntity> authors) {
        return authors == null ? Set.of() :
                authors.stream().map(AuthorEntity::getName).collect(Collectors.toSet());
    }

    @Named("mapCategories")
    default Set<String> mapCategories(Set<CategoryEntity> categories) {
        return categories == null ? Set.of() :
                categories.stream().map(CategoryEntity::getName).collect(Collectors.toSet());
    }

    @Named("mapMoods")
    default Set<String> mapMoods(Set<MoodEntity> moods) {
        return moods == null ? Set.of() :
                moods.stream().map(MoodEntity::getName).collect(Collectors.toSet());
    }

    @Named("mapTags")
    default Set<String> mapTags(Set<TagEntity> tags) {
        return tags == null ? Set.of() :
                tags.stream().map(TagEntity::getName).collect(Collectors.toSet());
    }

    @Named("mapLibraryPathIdOnly")
    default LibraryPath mapLibraryPathIdOnly(LibraryPathEntity entity) {
        if (entity == null) return null;
        return LibraryPath.builder()
                .id(entity.getId())
                .build();
    }
}