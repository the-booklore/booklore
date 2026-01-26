package com.adityachandel.booklore.model.entity;

import com.adityachandel.booklore.convertor.BookRecommendationIdsListConverter;
import com.adityachandel.booklore.model.dto.BookRecommendationLite;
import com.adityachandel.booklore.model.enums.BookFileType;
import com.adityachandel.booklore.util.ArchiveUtils;
import jakarta.persistence.*;
import lombok.*;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Entity
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "book")
public class BookEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "metadata_match_score")
    private Float metadataMatchScore;

    @OneToOne(mappedBy = "book", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private BookMetadataEntity metadata;

    @Column(name = "metadata_updated_at")
    private Instant metadataUpdatedAt;

    @Column(name = "metadata_for_write_updated_at")
    private Instant metadataForWriteUpdatedAt;

    @ManyToOne
    @JoinColumn(name = "library_id", nullable = false)
    private LibraryEntity library;

    @ManyToOne
    @JoinColumn(name = "library_path_id", nullable = false)
    private LibraryPathEntity libraryPath;

    @Column(name = "added_on")
    private Instant addedOn;

    @Column(name = "book_cover_hash", length = 20)
    private String bookCoverHash;

    @Column(name = "deleted")
    @Builder.Default
    private Boolean deleted = Boolean.FALSE;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @ManyToMany
    @JoinTable(
            name = "book_shelf_mapping",
            joinColumns = @JoinColumn(name = "book_id"),
            inverseJoinColumns = @JoinColumn(name = "shelf_id")
    )
    private Set<ShelfEntity> shelves;

    @Convert(converter = BookRecommendationIdsListConverter.class)
    @Column(name = "similar_books_json", columnDefinition = "TEXT")
    private Set<BookRecommendationLite> similarBooksJson;

    @OneToMany(mappedBy = "book", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("id ASC")
    @Builder.Default
    private List<BookFileEntity> bookFiles = new ArrayList<>();

    @OneToMany(mappedBy = "book", fetch = FetchType.LAZY)
    private List<UserBookProgressEntity> userBookProgress;

    public Path getFullFilePath() {
        BookFileEntity primaryBookFile = getPrimaryBookFile();
        if (libraryPath == null || libraryPath.getPath() == null || primaryBookFile.getFileSubPath() == null || primaryBookFile.getFileName() == null) {
            throw new IllegalStateException(
                    "Cannot construct file path: missing library path, file subpath, or file name");
        }

        return Paths.get(libraryPath.getPath(), primaryBookFile.getFileSubPath(), primaryBookFile.getFileName());
    }

    public List<Path> getFullFilePaths() {
        if (libraryPath == null || libraryPath.getPath() == null || bookFiles == null || bookFiles.isEmpty()) {
            throw new IllegalStateException(
                    "Cannot construct file path: missing library path, file subpath, or file name");
        }
        return bookFiles.stream()
                .map(bookFile -> Paths.get(libraryPath.getPath(), bookFile.getFileSubPath(), bookFile.getFileName()))
                .collect(Collectors.toList());
    }

    // TODO: Add support for specifying the preferred format
    public BookFileEntity getPrimaryBookFile() {
        if (bookFiles == null) {
            bookFiles = new ArrayList<>();
        }
        if (bookFiles.isEmpty()) {
            throw new IllegalStateException("Book file not found");
        }
        return bookFiles.getFirst();
    }
}
