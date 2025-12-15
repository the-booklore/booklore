package com.adityachandel.booklore.mapper.komga;

import com.adityachandel.booklore.model.dto.komga.*;
import com.adityachandel.booklore.model.entity.*;
import org.springframework.stereotype.Component;

import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Component
public class KomgaMapper {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE;

    public KomgaLibraryDto toKomgaLibraryDto(LibraryEntity library) {
        return KomgaLibraryDto.builder()
                .id(library.getId().toString())
                .name(library.getName())
                .root(library.getLibraryPaths() != null && !library.getLibraryPaths().isEmpty() 
                      ? library.getLibraryPaths().get(0).getPath() 
                      : "")
                .unavailable(false)
                .build();
    }

    public KomgaBookDto toKomgaBookDto(BookEntity book) {
        BookMetadataEntity metadata = book.getMetadata();
        String seriesId = generateSeriesId(book);
        
        return KomgaBookDto.builder()
                .id(book.getId().toString())
                .seriesId(seriesId)
                .seriesTitle(metadata != null && metadata.getSeriesName() != null 
                           ? metadata.getSeriesName() 
                           : "Unknown Series")
                .libraryId(book.getLibrary().getId().toString())
                .name(metadata != null ? metadata.getTitle() : book.getFileName())
                .url("/komga/api/v1/books/" + book.getId())
                .number(metadata != null && metadata.getSeriesNumber() != null 
                       ? metadata.getSeriesNumber().intValue() 
                       : 1)
                .created(book.getAddedOn())
                .lastModified(book.getAddedOn())
                .fileLastModified(book.getAddedOn())
                .sizeBytes(book.getFileSizeKb() != null ? book.getFileSizeKb() * 1024 : 0L)
                .size(formatFileSize(book.getFileSizeKb()))
                .media(toKomgaMediaDto(book, metadata))
                .metadata(toKomgaBookMetadataDto(metadata))
                .deleted(book.getDeleted())
                .fileHash(book.getCurrentHash())
                .oneshot(false)
                .build();
    }

    public KomgaSeriesDto toKomgaSeriesDto(String seriesName, Long libraryId, List<BookEntity> books) {
        if (books == null || books.isEmpty()) {
            return null;
        }
        
        BookEntity firstBook = books.get(0);
        String seriesId = generateSeriesId(firstBook);
        
        // Aggregate metadata from all books
        KomgaSeriesMetadataDto metadata = aggregateSeriesMetadata(seriesName, books);
        KomgaBookMetadataAggregationDto booksMetadata = aggregateBooksMetadata(books);
        
        return KomgaSeriesDto.builder()
                .id(seriesId)
                .libraryId(libraryId.toString())
                .name(seriesName)
                .url("/komga/api/v1/series/" + seriesId)
                .created(firstBook.getAddedOn())
                .lastModified(firstBook.getAddedOn())
                .fileLastModified(firstBook.getAddedOn())
                .booksCount(books.size())
                .booksReadCount(0)
                .booksUnreadCount(books.size())
                .booksInProgressCount(0)
                .metadata(metadata)
                .booksMetadata(booksMetadata)
                .deleted(false)
                .oneshot(books.size() == 1)
                .build();
    }

    private KomgaMediaDto toKomgaMediaDto(BookEntity book, BookMetadataEntity metadata) {
        String mediaType = getMediaType(book.getBookType());
        Integer pageCount = metadata != null ? metadata.getPageCount() : 0;
        return KomgaMediaDto.builder()
                .status("READY")
                .mediaType(mediaType)
                .mediaProfile(getMediaProfile(book.getBookType()))
                .pagesCount(pageCount != null ? pageCount : 0)
                .build();
    }

    private KomgaBookMetadataDto toKomgaBookMetadataDto(BookMetadataEntity metadata) {
        if (metadata == null) {
            return KomgaBookMetadataDto.builder().build();
        }
        
        List<KomgaAuthorDto> authors = new ArrayList<>();
        if (metadata.getAuthors() != null) {
            authors = metadata.getAuthors().stream()
                    .map(author -> KomgaAuthorDto.builder()
                            .name(author.getName())
                            .role("writer")
                            .build())
                    .collect(Collectors.toList());
        }
        
        List<String> tags = new ArrayList<>();
        if (metadata.getTags() != null) {
            tags = metadata.getTags().stream()
                    .map(TagEntity::getName)
                    .collect(Collectors.toList());
        }
        
        return KomgaBookMetadataDto.builder()
                .title(metadata.getTitle())
                .titleLock(metadata.getTitleLocked())
                .summary(metadata.getDescription())
                .summaryLock(metadata.getDescriptionLocked())
                .number(metadata.getSeriesNumber() != null ? metadata.getSeriesNumber().toString() : null)
                .numberLock(metadata.getSeriesNumberLocked())
                .numberSort(metadata.getSeriesNumber())
                .numberSortLock(metadata.getSeriesNumberLocked())
                .releaseDate(metadata.getPublishedDate() != null 
                           ? metadata.getPublishedDate().format(DATE_FORMATTER) 
                           : null)
                .releaseDateLock(metadata.getPublishedDateLocked())
                .authors(authors)
                .authorsLock(metadata.getAuthorsLocked())
                .tags(tags)
                .tagsLock(metadata.getTagsLocked())
                .isbn(metadata.getIsbn13() != null ? metadata.getIsbn13() : metadata.getIsbn10())
                .isbnLock(metadata.getIsbn13Locked())
                .build();
    }

    private KomgaSeriesMetadataDto aggregateSeriesMetadata(String seriesName, List<BookEntity> books) {
        BookEntity firstBook = books.get(0);
        BookMetadataEntity firstMetadata = firstBook.getMetadata();
        
        List<String> genres = new ArrayList<>();
        List<String> tags = new ArrayList<>();
        
        if (firstMetadata != null) {
            if (firstMetadata.getCategories() != null) {
                genres = firstMetadata.getCategories().stream()
                        .map(CategoryEntity::getName)
                        .collect(Collectors.toList());
            }
            if (firstMetadata.getTags() != null) {
                tags = firstMetadata.getTags().stream()
                        .map(TagEntity::getName)
                        .collect(Collectors.toList());
            }
        }
        
        return KomgaSeriesMetadataDto.builder()
                .status("ONGOING")
                .statusLock(false)
                .title(seriesName)
                .titleLock(false)
                .titleSort(seriesName)
                .titleSortLock(false)
                .summary(firstMetadata != null ? firstMetadata.getDescription() : null)
                .summaryLock(false)
                .publisher(firstMetadata != null ? firstMetadata.getPublisher() : null)
                .publisherLock(false)
                .language(firstMetadata != null ? firstMetadata.getLanguage() : "en")
                .languageLock(false)
                .genres(genres)
                .genresLock(false)
                .tags(tags)
                .tagsLock(false)
                .totalBookCount(books.size())
                .totalBookCountLock(false)
                .build();
    }

    private KomgaBookMetadataAggregationDto aggregateBooksMetadata(List<BookEntity> books) {
        Set<String> authorNames = new HashSet<>();
        Set<String> allTags = new HashSet<>();
        String releaseDate = null;
        String summary = null;
        
        for (BookEntity book : books) {
            BookMetadataEntity metadata = book.getMetadata();
            if (metadata != null) {
                if (metadata.getAuthors() != null) {
                    metadata.getAuthors().forEach(author -> authorNames.add(author.getName()));
                }
                
                if (metadata.getTags() != null) {
                    metadata.getTags().forEach(tag -> allTags.add(tag.getName()));
                }
                
                if (releaseDate == null && metadata.getPublishedDate() != null) {
                    releaseDate = metadata.getPublishedDate().format(DATE_FORMATTER);
                }
                
                if (summary == null && metadata.getDescription() != null) {
                    summary = metadata.getDescription();
                }
            }
        }
        
        List<KomgaAuthorDto> authors = authorNames.stream()
                .map(name -> KomgaAuthorDto.builder().name(name).role("writer").build())
                .collect(Collectors.toList());
        
        return KomgaBookMetadataAggregationDto.builder()
                .authors(authors)
                .tags(new ArrayList<>(allTags))
                .releaseDate(releaseDate)
                .summary(summary)
                .summaryLock(false)
                .build();
    }

    private String generateSeriesId(BookEntity book) {
        BookMetadataEntity metadata = book.getMetadata();
        String seriesName = metadata != null && metadata.getSeriesName() != null 
                          ? metadata.getSeriesName() 
                          : "Unknown Series";
        Long libraryId = book.getLibrary().getId();
        
        // Generate a pseudo-ID based on library and series name
        return libraryId + "-" + seriesName.toLowerCase().replaceAll("[^a-z0-9]+", "-");
    }

    private String getMediaType(com.adityachandel.booklore.model.enums.BookFileType bookType) {
        if (bookType == null) {
            return "application/zip";
        }
        
        return switch (bookType) {
            case PDF -> "application/pdf";
            case EPUB -> "application/epub+zip";
            case CBX -> "application/x-cbz"; // CBX is a generic format for comic book archives
            case FB2 -> "application/fictionbook2+zip";
        };
    }

    private String getMediaProfile(com.adityachandel.booklore.model.enums.BookFileType bookType) {
        if (bookType == null) {
            return "UNKNOWN";
        }
        
        return switch (bookType) {
            case PDF -> "PDF";
            case EPUB -> "EPUB";
            case CBX -> "DIVINA"; // DIVINA is for comic books
        };
    }

    private String formatFileSize(Long fileSizeKb) {
        if (fileSizeKb == null || fileSizeKb == 0) {
            return "0 B";
        }
        
        long bytes = fileSizeKb * 1024;
        if (bytes < 1024) {
            return bytes + " B";
        } else if (bytes < 1024 * 1024) {
            return (bytes / 1024) + " KB";
        } else if (bytes < 1024 * 1024 * 1024) {
            return (bytes / (1024 * 1024)) + " MB";
        } else {
            return (bytes / (1024 * 1024 * 1024)) + " GB";
        }
    }

    public KomgaUserDto toKomgaUserDto(OpdsUserV2Entity opdsUser) {
        return KomgaUserDto.builder()
                .id(opdsUser.getId().toString())
                .email(opdsUser.getUsername() + "@booklore.local")
                .roles(List.of("USER"))
                .sharedAllLibraries(true)
                .build();
    }
}
