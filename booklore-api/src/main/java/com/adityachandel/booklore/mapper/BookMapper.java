package com.adityachandel.booklore.mapper;

import com.adityachandel.booklore.model.dto.BookFile;
import com.adityachandel.booklore.model.dto.Book;
import com.adityachandel.booklore.model.dto.LibraryPath;
import com.adityachandel.booklore.model.entity.*;
import com.adityachandel.booklore.model.enums.AdditionalFileType;
import org.mapstruct.Context;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;
import org.mapstruct.ReportingPolicy;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Mapper(componentModel = "spring", uses = {BookMetadataMapper.class, ShelfMapper.class, AdditionalFileMapper.class}, unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface BookMapper {

    @Mapping(source = "library.id", target = "libraryId")
    @Mapping(source = "library.name", target = "libraryName")
    @Mapping(source = "libraryPath", target = "libraryPath", qualifiedByName = "mapLibraryPathIdOnly")
    @Mapping(source = "metadata", target = "metadata")
    @Mapping(source = "shelves", target = "shelves")
    @Mapping(source = "bookFiles", target = "alternativeFormats", qualifiedByName = "mapAlternativeFormats")
    @Mapping(source = "bookFiles", target = "supplementaryFiles", qualifiedByName = "mapSupplementaryFiles")
    Book toBook(BookEntity bookEntity);

    @Mapping(source = "library.id", target = "libraryId")
    @Mapping(source = "library.name", target = "libraryName")
    @Mapping(source = "libraryPath", target = "libraryPath", qualifiedByName = "mapLibraryPathIdOnly")
    @Mapping(source = "metadata", target = "metadata")
    @Mapping(source = "shelves", target = "shelves")
    @Mapping(source = "bookFiles", target = "alternativeFormats", qualifiedByName = "mapAlternativeFormats")
    @Mapping(source = "bookFiles", target = "supplementaryFiles", qualifiedByName = "mapSupplementaryFiles")
    Book toBookWithDescription(BookEntity bookEntity, @Context boolean includeDescription);

    default Set<String> mapAuthors(Set<AuthorEntity> authors) {
        if (authors == null) return null;
        return authors.stream()
                .map(AuthorEntity::getName)
                .collect(Collectors.toSet());
    }

    default Set<String> mapCategories(Set<CategoryEntity> categories) {
        if (categories == null) return null;
        return categories.stream()
                .map(CategoryEntity::getName)
                .collect(Collectors.toSet());
    }

    default Set<String> mapMoods(Set<MoodEntity> moods) {
        if (moods == null) return null;
        return moods.stream()
                .map(MoodEntity::getName)
                .collect(Collectors.toSet());
    }

    default Set<String> mapTags(Set<TagEntity> tags) {
        if (tags == null) return null;
        return tags.stream()
                .map(TagEntity::getName)
                .collect(Collectors.toSet());
    }

    @Named("mapLibraryPathIdOnly")
    default LibraryPath mapLibraryPathIdOnly(LibraryPathEntity entity) {
        if (entity == null) return null;
        return LibraryPath.builder()
                .id(entity.getId())
                .build();
    }

    @Named("mapAlternativeFormats")
    default List<BookFile> mapAlternativeFormats(List<BookFileEntity> bookFiles) {
        if (bookFiles == null) return null;
        return bookFiles.stream()
                .filter(af -> AdditionalFileType.ALTERNATIVE_FORMAT.equals(af.getAdditionalFileType()))
                .map(this::toAdditionalFile)
                .toList();
    }

    @Named("mapSupplementaryFiles")
    default List<BookFile> mapSupplementaryFiles(List<BookFileEntity> bookFiles) {
        if (bookFiles == null) return null;
        return bookFiles.stream()
                .filter(af -> AdditionalFileType.SUPPLEMENTARY.equals(af.getAdditionalFileType()))
                .map(this::toAdditionalFile)
                .toList();
    }

    default BookFile toAdditionalFile(BookFileEntity entity) {
        if (entity == null) return null;
        return BookFile.builder()
                .id(entity.getId())
                .bookId(entity.getBook().getId())
                .fileName(entity.getFileName())
                .filePath(entity.getFullFilePath().toString())
                .fileSubPath(entity.getFileSubPath())
                .additionalFileType(entity.getAdditionalFileType())
                .fileSizeKb(entity.getFileSizeKb())
                .description(entity.getDescription())
                .addedOn(entity.getAddedOn())
                .build();
    }
}
