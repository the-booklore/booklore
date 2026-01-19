package com.adityachandel.booklore.mapper.v2;

import com.adityachandel.booklore.mapper.ShelfMapper;
import com.adityachandel.booklore.model.dto.Book;
import com.adityachandel.booklore.model.dto.BookFile;
import com.adityachandel.booklore.model.dto.BookMetadata;
import com.adityachandel.booklore.model.dto.LibraryPath;
import com.adityachandel.booklore.model.entity.*;
import com.adityachandel.booklore.model.enums.BookFileType;
import com.adityachandel.booklore.service.book.PreferredBookFileResolver;
import org.mapstruct.AfterMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.Named;
import org.mapstruct.ReportingPolicy;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Mapper(componentModel = "spring", uses = ShelfMapper.class, unmappedTargetPolicy = ReportingPolicy.IGNORE)
public abstract class BookMapperV2 {

    @Autowired
    protected PreferredBookFileResolver preferredBookFileResolver;

    @Mapping(source = "library.id", target = "libraryId")
    @Mapping(source = "library.name", target = "libraryName")
    @Mapping(source = "libraryPath", target = "libraryPath", qualifiedByName = "mapLibraryPathIdOnly")
    @Mapping(source = "bookFiles", target = "bookType", qualifiedByName = "mapPrimaryBookType")
    @Mapping(target = "metadata", qualifiedByName = "mapMetadata")
    public abstract Book toDTO(BookEntity bookEntity);

    @Named("mapMetadata")
    @Mapping(target = "authors", source = "authors", qualifiedByName = "mapAuthors")
    @Mapping(target = "categories", source = "categories", qualifiedByName = "mapCategories")
    @Mapping(target = "moods", source = "moods", qualifiedByName = "mapMoods")
    @Mapping(target = "tags", source = "tags", qualifiedByName = "mapTags")
    abstract BookMetadata mapMetadata(BookMetadataEntity metadataEntity);

    @Named("mapAuthors")
    Set<String> mapAuthors(Set<AuthorEntity> authors) {
        return authors == null ? Set.of() :
                authors.stream().map(AuthorEntity::getName).collect(Collectors.toSet());
    }

    @Named("mapCategories")
    Set<String> mapCategories(Set<CategoryEntity> categories) {
        return categories == null ? Set.of() :
                categories.stream().map(CategoryEntity::getName).collect(Collectors.toSet());
    }

    @Named("mapMoods")
    Set<String> mapMoods(Set<MoodEntity> moods) {
        return moods == null ? Set.of() :
                moods.stream().map(MoodEntity::getName).collect(Collectors.toSet());
    }

    @Named("mapTags")
    Set<String> mapTags(Set<TagEntity> tags) {
        return tags == null ? Set.of() :
                tags.stream().map(TagEntity::getName).collect(Collectors.toSet());
    }

    @Named("mapPrimaryBookType")
    BookFileType mapPrimaryBookType(List<BookFileEntity> bookFiles) {
        if (bookFiles == null || bookFiles.isEmpty()) return null;
        return bookFiles.stream()
                .filter(BookFileEntity::isBook)
                .map(BookFileEntity::getBookType)
                .findFirst()
                .orElse(null);
    }

    @Named("mapLibraryPathIdOnly")
    LibraryPath mapLibraryPathIdOnly(LibraryPathEntity entity) {
        if (entity == null) return null;
        return LibraryPath.builder()
                .id(entity.getId())
                .build();
    }

    @AfterMapping
    void enrichWithPrimaryFileInfo(BookEntity bookEntity, @MappingTarget Book book) {
        if (bookEntity == null) return;
        try {
            BookFileEntity primaryFile = preferredBookFileResolver.resolvePrimaryBookFile(bookEntity);
            if (primaryFile != null) {
                book.setBookType(primaryFile.getBookType());
                book.setFileName(primaryFile.getFileName());
                book.setFileSubPath(primaryFile.getFileSubPath());
                book.setFileSizeKb(primaryFile.getFileSizeKb());

                List<BookFileEntity> bookFiles = bookEntity.getBookFiles();
                if (bookFiles != null) {
                    List<BookFile> alternativeFormats = bookFiles.stream()
                            .filter(BookFileEntity::isBook)
                            .filter(bf -> !bf.equals(primaryFile))
                            .map(this::toBookFile)
                            .toList();
                    book.setAlternativeFormats(alternativeFormats);
                }
            }
        } catch (IllegalStateException e) {
            // No book files available, leave defaults
        }
    }

    BookFile toBookFile(BookFileEntity entity) {
        if (entity == null) return null;
        return BookFile.builder()
                .id(entity.getId())
                .bookId(entity.getBook().getId())
                .fileName(entity.getFileName())
                .filePath(entity.getFullFilePath().toString())
                .fileSubPath(entity.getFileSubPath())
                .isBook(entity.isBook())
                .bookType(entity.getBookType())
                .fileSizeKb(entity.getFileSizeKb())
                .build();
    }
}