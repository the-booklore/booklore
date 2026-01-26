package com.adityachandel.booklore.mapper;

import com.adityachandel.booklore.model.dto.BookFile;
import com.adityachandel.booklore.model.dto.Book;
import com.adityachandel.booklore.model.dto.LibraryPath;
import com.adityachandel.booklore.model.enums.BookFileType;
import com.adityachandel.booklore.model.entity.*;
import com.adityachandel.booklore.service.book.PreferredBookFileResolver;
import org.mapstruct.AfterMapping;
import org.mapstruct.Context;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.Named;
import org.mapstruct.ReportingPolicy;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Mapper(componentModel = "spring", uses = {BookMetadataMapper.class, ShelfMapper.class, AdditionalFileMapper.class}, unmappedTargetPolicy = ReportingPolicy.IGNORE)
public abstract class BookMapper {

    @Autowired
    protected PreferredBookFileResolver preferredBookFileResolver;

    @Mapping(source = "library.id", target = "libraryId")
    @Mapping(source = "library.name", target = "libraryName")
    @Mapping(source = "libraryPath", target = "libraryPath", qualifiedByName = "mapLibraryPathIdOnly")
    @Mapping(source = "metadata", target = "metadata")
    @Mapping(source = "shelves", target = "shelves")
    @Mapping(source = "bookFiles", target = "bookType", qualifiedByName = "mapPrimaryBookType")
    @Mapping(source = "bookFiles", target = "fileName", qualifiedByName = "mapPrimaryFileName")
    @Mapping(source = "bookFiles", target = "filePath", qualifiedByName = "mapPrimaryFilePath")
    @Mapping(source = "bookFiles", target = "fileSubPath", qualifiedByName = "mapPrimaryFileSubPath")
    @Mapping(source = "bookFiles", target = "fileSizeKb", qualifiedByName = "mapPrimaryFileSizeKb")
    @Mapping(source = "bookFiles", target = "alternativeFormats", qualifiedByName = "mapAlternativeFormats")
    @Mapping(source = "bookFiles", target = "supplementaryFiles", qualifiedByName = "mapSupplementaryFiles")
    public abstract Book toBook(BookEntity bookEntity);

    @Mapping(source = "library.id", target = "libraryId")
    @Mapping(source = "library.name", target = "libraryName")
    @Mapping(source = "libraryPath", target = "libraryPath", qualifiedByName = "mapLibraryPathIdOnly")
    @Mapping(source = "metadata", target = "metadata")
    @Mapping(source = "shelves", target = "shelves")
    @Mapping(source = "bookFiles", target = "bookType", qualifiedByName = "mapPrimaryBookType")
    @Mapping(source = "bookFiles", target = "fileName", qualifiedByName = "mapPrimaryFileName")
    @Mapping(source = "bookFiles", target = "filePath", qualifiedByName = "mapPrimaryFilePath")
    @Mapping(source = "bookFiles", target = "fileSubPath", qualifiedByName = "mapPrimaryFileSubPath")
    @Mapping(source = "bookFiles", target = "fileSizeKb", qualifiedByName = "mapPrimaryFileSizeKb")
    @Mapping(source = "bookFiles", target = "alternativeFormats", qualifiedByName = "mapAlternativeFormats")
    @Mapping(source = "bookFiles", target = "supplementaryFiles", qualifiedByName = "mapSupplementaryFiles")
    public abstract Book toBookWithDescription(BookEntity bookEntity, @Context boolean includeDescription);

    Set<String> mapAuthors(Set<AuthorEntity> authors) {
        if (authors == null) return null;
        return authors.stream()
                .map(AuthorEntity::getName)
                .collect(Collectors.toSet());
    }

    Set<String> mapCategories(Set<CategoryEntity> categories) {
        if (categories == null) return null;
        return categories.stream()
                .map(CategoryEntity::getName)
                .collect(Collectors.toSet());
    }

    Set<String> mapMoods(Set<MoodEntity> moods) {
        if (moods == null) return null;
        return moods.stream()
                .map(MoodEntity::getName)
                .collect(Collectors.toSet());
    }

    Set<String> mapTags(Set<TagEntity> tags) {
        if (tags == null) return null;
        return tags.stream()
                .map(TagEntity::getName)
                .collect(Collectors.toSet());
    }

    @Named("mapLibraryPathIdOnly")
    LibraryPath mapLibraryPathIdOnly(LibraryPathEntity entity) {
        if (entity == null) return null;
        return LibraryPath.builder()
                .id(entity.getId())
                .build();
    }

    @Named("mapPrimaryBookType")
    BookFileType mapPrimaryBookType(List<BookFileEntity> bookFiles) {
        BookFileEntity primary = getPrimaryBookFileFromList(bookFiles);
        return primary == null ? null : primary.getBookType();
    }

    @Named("mapPrimaryFileName")
    String mapPrimaryFileName(List<BookFileEntity> bookFiles) {
        BookFileEntity primary = getPrimaryBookFileFromList(bookFiles);
        return primary == null ? null : primary.getFileName();
    }

    @Named("mapPrimaryFilePath")
    String mapPrimaryFilePath(List<BookFileEntity> bookFiles) {
        BookFileEntity primary = getPrimaryBookFileFromList(bookFiles);
        return primary == null ? null : primary.getFullFilePath().toString();
    }

    @Named("mapPrimaryFileSubPath")
    String mapPrimaryFileSubPath(List<BookFileEntity> bookFiles) {
        BookFileEntity primary = getPrimaryBookFileFromList(bookFiles);
        return primary == null ? null : primary.getFileSubPath();
    }

    @Named("mapPrimaryFileSizeKb")
    Long mapPrimaryFileSizeKb(List<BookFileEntity> bookFiles) {
        BookFileEntity primary = getPrimaryBookFileFromList(bookFiles);
        return primary == null ? null : primary.getFileSizeKb();
    }

    @Named("mapAlternativeFormats")
    List<BookFile> mapAlternativeFormats(List<BookFileEntity> bookFiles) {
        if (bookFiles == null) return null;
        return bookFiles.stream()
                .filter(bf -> bf.isBook())
                .filter(bf -> !bf.equals(getPrimaryBookFileFromList(bookFiles)))
                .map(this::toBookFile)
                .toList();
    }

    @Named("mapSupplementaryFiles")
    List<BookFile> mapSupplementaryFiles(List<BookFileEntity> bookFiles) {
        if (bookFiles == null)
            return null;
        return bookFiles.stream()
                .filter(bf -> !bf.isBook())
                .map(this::toBookFile)
                .toList();
    }

    BookFileEntity getPrimaryBookFile(BookEntity bookEntity) {
        if (bookEntity == null) return null;
        try {
            return preferredBookFileResolver.resolvePrimaryBookFile(bookEntity);
        } catch (IllegalStateException e) {
            return null;
        }
    }

    BookFileEntity getPrimaryBookFileFromList(List<BookFileEntity> bookFiles) {
        if (bookFiles == null || bookFiles.isEmpty()) return null;
        return bookFiles.stream()
                .filter(bf -> bf.isBook())
                .findFirst()
                .orElse(null);
    }

    @AfterMapping
    void enrichWithPrimaryFileInfo(BookEntity bookEntity, @MappingTarget Book book) {
        BookFileEntity primaryFile = getPrimaryBookFile(bookEntity);
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
                .description(entity.getDescription())
                .addedOn(entity.getAddedOn())
                .build();
    }
}
