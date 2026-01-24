package com.adityachandel.booklore.util;

import com.adityachandel.booklore.model.dto.settings.LibraryFile;
import com.adityachandel.booklore.model.entity.LibraryPathEntity;
import com.adityachandel.booklore.model.enums.BookFileType;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class BookFileGroupingUtilsTest {

    @Test
    void extractGroupingKey_shouldStripExtension() {
        assertThat(BookFileGroupingUtils.extractGroupingKey("The Hobbit.pdf")).isEqualTo("the hobbit");
        assertThat(BookFileGroupingUtils.extractGroupingKey("The Hobbit.epub")).isEqualTo("the hobbit");
    }

    @Test
    void extractGroupingKey_shouldStripFormatIndicators() {
        assertThat(BookFileGroupingUtils.extractGroupingKey("The Hobbit (PDF).pdf")).isEqualTo("the hobbit");
        assertThat(BookFileGroupingUtils.extractGroupingKey("The Hobbit (EPUB).epub")).isEqualTo("the hobbit");
        assertThat(BookFileGroupingUtils.extractGroupingKey("The Hobbit [pdf].pdf")).isEqualTo("the hobbit");
        assertThat(BookFileGroupingUtils.extractGroupingKey("The Hobbit [EPUB].epub")).isEqualTo("the hobbit");
    }

    @Test
    void extractGroupingKey_shouldBeCaseInsensitive() {
        assertThat(BookFileGroupingUtils.extractGroupingKey("THE HOBBIT.pdf"))
                .isEqualTo(BookFileGroupingUtils.extractGroupingKey("the hobbit.epub"));
    }

    @Test
    void extractGroupingKey_shouldNormalizeWhitespace() {
        assertThat(BookFileGroupingUtils.extractGroupingKey("The   Hobbit.pdf")).isEqualTo("the hobbit");
        assertThat(BookFileGroupingUtils.extractGroupingKey("  The Hobbit  .pdf")).isEqualTo("the hobbit");
    }

    @Test
    void extractGroupingKey_shouldHandleNullAndEmpty() {
        assertThat(BookFileGroupingUtils.extractGroupingKey(null)).isEqualTo("");
        assertThat(BookFileGroupingUtils.extractGroupingKey("")).isEqualTo("");
    }

    @Test
    void extractGroupingKey_shouldHandleFileWithNoExtension() {
        assertThat(BookFileGroupingUtils.extractGroupingKey("noextension")).isEqualTo("noextension");
    }

    @Test
    void extractGroupingKey_shouldHandleMultipleFormatIndicators() {
        assertThat(BookFileGroupingUtils.extractGroupingKey("Book (PDF) (EPUB).pdf")).isEqualTo("book");
    }

    @Test
    void extractGroupingKey_shouldNotStripNonFormatParentheses() {
        assertThat(BookFileGroupingUtils.extractGroupingKey("Book (Extended Edition).pdf")).isEqualTo("book (extended edition)");
    }

    @Test
    void generateDirectoryGroupKey_shouldCombineSubPathAndGroupingKey() {
        String key = BookFileGroupingUtils.generateDirectoryGroupKey("fantasy/tolkien", "The Hobbit.pdf");
        assertThat(key).isEqualTo("fantasy/tolkien:the hobbit");
    }

    @Test
    void generateDirectoryGroupKey_shouldHandleNullSubPath() {
        String key = BookFileGroupingUtils.generateDirectoryGroupKey(null, "The Hobbit.pdf");
        assertThat(key).isEqualTo(":the hobbit");
    }

    @Test
    void groupByBaseName_shouldGroupFilesWithSameBaseNameInSameDirectory() {
        LibraryPathEntity pathEntity = new LibraryPathEntity();
        pathEntity.setId(1L);
        pathEntity.setPath("/library");

        LibraryFile epub = LibraryFile.builder()
                .libraryPathEntity(pathEntity)
                .fileSubPath("books")
                .fileName("The Hobbit.epub")
                .bookFileType(BookFileType.EPUB)
                .build();

        LibraryFile pdf = LibraryFile.builder()
                .libraryPathEntity(pathEntity)
                .fileSubPath("books")
                .fileName("The Hobbit.pdf")
                .bookFileType(BookFileType.PDF)
                .build();

        Map<String, List<LibraryFile>> groups = BookFileGroupingUtils.groupByBaseName(List.of(epub, pdf));

        assertThat(groups).hasSize(1);
        assertThat(groups.values().iterator().next()).containsExactlyInAnyOrder(epub, pdf);
    }

    @Test
    void groupByBaseName_shouldSeparateFilesInDifferentDirectories() {
        LibraryPathEntity pathEntity = new LibraryPathEntity();
        pathEntity.setId(1L);
        pathEntity.setPath("/library");

        LibraryFile file1 = LibraryFile.builder()
                .libraryPathEntity(pathEntity)
                .fileSubPath("dir1")
                .fileName("book.epub")
                .bookFileType(BookFileType.EPUB)
                .build();

        LibraryFile file2 = LibraryFile.builder()
                .libraryPathEntity(pathEntity)
                .fileSubPath("dir2")
                .fileName("book.epub")
                .bookFileType(BookFileType.EPUB)
                .build();

        Map<String, List<LibraryFile>> groups = BookFileGroupingUtils.groupByBaseName(List.of(file1, file2));

        assertThat(groups).hasSize(2);
    }

    @Test
    void groupByBaseName_shouldSeparateFilesFromDifferentLibraryPaths() {
        LibraryPathEntity path1 = new LibraryPathEntity();
        path1.setId(1L);
        path1.setPath("/library1");

        LibraryPathEntity path2 = new LibraryPathEntity();
        path2.setId(2L);
        path2.setPath("/library2");

        LibraryFile file1 = LibraryFile.builder()
                .libraryPathEntity(path1)
                .fileSubPath("books")
                .fileName("book.epub")
                .bookFileType(BookFileType.EPUB)
                .build();

        LibraryFile file2 = LibraryFile.builder()
                .libraryPathEntity(path2)
                .fileSubPath("books")
                .fileName("book.epub")
                .bookFileType(BookFileType.EPUB)
                .build();

        Map<String, List<LibraryFile>> groups = BookFileGroupingUtils.groupByBaseName(List.of(file1, file2));

        assertThat(groups).hasSize(2);
    }

    @Test
    void groupByBaseName_shouldGroupFormatIndicatorVariants() {
        LibraryPathEntity pathEntity = new LibraryPathEntity();
        pathEntity.setId(1L);
        pathEntity.setPath("/library");

        LibraryFile epub = LibraryFile.builder()
                .libraryPathEntity(pathEntity)
                .fileSubPath("books")
                .fileName("The Hobbit.epub")
                .bookFileType(BookFileType.EPUB)
                .build();

        LibraryFile pdf = LibraryFile.builder()
                .libraryPathEntity(pathEntity)
                .fileSubPath("books")
                .fileName("The Hobbit (PDF).pdf")
                .bookFileType(BookFileType.PDF)
                .build();

        Map<String, List<LibraryFile>> groups = BookFileGroupingUtils.groupByBaseName(List.of(epub, pdf));

        assertThat(groups).hasSize(1);
        assertThat(groups.values().iterator().next()).containsExactlyInAnyOrder(epub, pdf);
    }
}
