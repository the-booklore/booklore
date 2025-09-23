package com.adityachandel.booklore;

import com.adityachandel.booklore.model.entity.AuthorEntity;
import com.adityachandel.booklore.model.entity.BookEntity;
import com.adityachandel.booklore.model.entity.BookMetadataEntity;
import com.adityachandel.booklore.util.PathPatternResolver;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PathPatternResolverTest {

    private BookEntity createBook(String title, String subtitle, List<String> authors, LocalDate date,
                                  String series, Float seriesNum, String lang,
                                  String publisher, String isbn13, String isbn10,
                                  String fileName) {
        BookEntity book = mock(BookEntity.class);
        BookMetadataEntity metadata = mock(BookMetadataEntity.class);

        when(book.getMetadata()).thenReturn(metadata);
        when(book.getFileName()).thenReturn(fileName);

        when(metadata.getTitle()).thenReturn(title);
        when(metadata.getSubtitle()).thenReturn(subtitle);

        if (authors == null) {
            when(metadata.getAuthors()).thenReturn(null);
        } else {
            LinkedHashSet<AuthorEntity> authorEntities = authors.stream().map(name -> {
                AuthorEntity a = new AuthorEntity();
                a.setName(name);
                return a;
            }).collect(Collectors.toCollection(LinkedHashSet::new));
            when(metadata.getAuthors()).thenReturn(authorEntities);
        }

        when(metadata.getPublishedDate()).thenReturn(date);
        when(metadata.getSeriesName()).thenReturn(series);
        when(metadata.getSeriesNumber()).thenReturn(seriesNum);
        when(metadata.getLanguage()).thenReturn(lang);
        when(metadata.getPublisher()).thenReturn(publisher);
        when(metadata.getIsbn13()).thenReturn(isbn13);
        when(metadata.getIsbn10()).thenReturn(isbn10);

        return book;
    }

    // Helper method for backward compatibility
    private BookEntity createBook(String title, List<String> authors, LocalDate date,
                                  String series, Float seriesNum, String lang,
                                  String publisher, String isbn13, String isbn10,
                                  String fileName) {
        return createBook(title, null, authors, date, series, seriesNum, lang, publisher, isbn13, isbn10, fileName);
    }

    @Test void emptyPattern_returnsOnlyExtension() {
        var book = createBook("Title", List.of("Author"), LocalDate.now(), null, null, null, null, null, null, "file.pdf");
        assertThat(PathPatternResolver.resolvePattern(book, "")).isEqualTo("file.pdf");
    }

    @Test void optionalBlockMissingPlaceholder_removed() {
        var book = createBook(null, null, null, null, null, null, null, null, null, "f.epub");
        assertThat(PathPatternResolver.resolvePattern(book, "<{series}/>")).isEqualTo("f.epub");
    }

    @Test void multipleOptionalBlocks_partialValues() {
        var book = createBook("Title", List.of("Author"), LocalDate.of(2020, 1, 1),
                "Series", 1f, null, null, null, null, "f.epub");
        String p = "<{series}/><{seriesIndex}. ><{language}/>{title}";
        assertThat(PathPatternResolver.resolvePattern(book, p)).isEqualTo("Series/1. Title.epub");
    }

    @Test void placeholdersOutsideOptional_replacedWithEmpty() {
        var book = createBook("Title", null, LocalDate.of(2020, 1, 1), null, null, null, null, null, null, "file.cbz");
        String p = "{title} - {authors} - {publisher}";
        assertThat(PathPatternResolver.resolvePattern(book, p)).isEqualTo("Title -  - .cbz");
    }

    @Test void seriesNumber_decimalAndInteger() {
        var book1 = createBook("Title", List.of("Author"), LocalDate.now(), "Series", 3.5f, null, null, null, null, "f.epub");
        var book2 = createBook("Title", List.of("Author"), LocalDate.now(), "Series", 3f, null, null, null, null, "f.epub");
        String p = "<{series}/><{seriesIndex}. >{title}";
        assertThat(PathPatternResolver.resolvePattern(book1, p)).isEqualTo("Series/3.5. Title.epub");
        assertThat(PathPatternResolver.resolvePattern(book2, p)).isEqualTo("Series/3. Title.epub");
    }

    @Test void sanitizes_illegalCharsAndWhitespace() {
        var book = createBook(" Ti:tle/<>|*? ", List.of("Au:thor|?*"), LocalDate.of(2000, 1, 1),
                "Se:ries", 1f, "Lang<>", "Pub|?", "123:456", "654|321", "file?.pdf");
        String p = "{title} - {authors} - {series} - {language} - {publisher} - {isbn}";
        String result = PathPatternResolver.resolvePattern(book, p);
        assertThat(result).doesNotContain(":", "/", "*", "?", "<", ">", "|")
                .contains("Title").contains("Author").contains("Series");
    }

    @Test void noExtension_returnsNoDot() {
        var book = createBook("Title", List.of("Author"), LocalDate.now(), null, null, null, null, null, null, "filenameWithoutExt");
        assertThat(PathPatternResolver.resolvePattern(book, "{title}")).isEqualTo("Title");
    }

    @Test void complexPattern_allPlaceholdersPresent() {
        var book = createBook("Complex Title", List.of("Author One"), LocalDate.of(2010, 5, 5),
                "Complex Series", 12.5f, "English", "Publisher", "ISBN13", "ISBN10", "complex.cbz");
        String p = "<{series}/><{seriesIndex}. ><{language}/><{publisher}/>{title} - {authors} - {year} - {isbn}";
        assertThat(PathPatternResolver.resolvePattern(book, p))
                .isEqualTo("Complex Series/12.5. English/Publisher/Complex Title - Author One - 2010 - ISBN13.cbz");
    }

    @Test void missingAllMetadata_returnsExtensionOnly() {
        var b = createBook(null, null, null, null, null, null, null, null, null, "f.pdf");
        assertThat(PathPatternResolver.resolvePattern(b, "")).isEqualTo("f.pdf");
    }

    @Test void optionalBlockWithEmptyValue_removesBlock() {
        var b = createBook(null, null, null, null, null, null, null, null, null, "f.epub");
        assertThat(PathPatternResolver.resolvePattern(b, "<{series}/><{language}/>")).isEqualTo("f.epub");
    }

    @Test void placeholderWithEmptyValue_outsideOptional_replacedByEmpty() {
        var b = createBook("T", null, null, null, null, null, null, null, null, "f.epub");
        String p = "{title} - {authors} - {language}";
        assertThat(PathPatternResolver.resolvePattern(b, p)).isEqualTo("T -  - .epub");
    }

    @Test void complexPattern_someMissingOptionalBlocksSkipped() {
        var b = createBook("T", List.of("A"), null, null, null, "en", null, null, null, "f.epub");
        String p = "<{series}/><{seriesIndex}. ><{language}/>{title} - {authors}";
        assertThat(PathPatternResolver.resolvePattern(b, p)).isEqualTo("en/T - A.epub");
    }

    @Test void seriesNumberDecimals_andIntegersHandledProperly() {
        var b1 = createBook("T", List.of("A"), null, "S", 1f, null, null, null, null, "f.epub");
        var b2 = createBook("T", List.of("A"), null, "S", 1.5f, null, null, null, null, "f.epub");
        String p = "<{series}/><{seriesIndex}. >{title}";
        assertThat(PathPatternResolver.resolvePattern(b1, p)).isEqualTo("S/1. T.epub");
        assertThat(PathPatternResolver.resolvePattern(b2, p)).isEqualTo("S/1.5. T.epub");
    }

    @Test void sanitize_removesIllegalCharacters() {
        var b = createBook("Ti:tle<>", List.of("Au:thor|?*"), null, "Se:ries", 1f,
                "La<>ng", "Pub|?", "123:456", "654|321", "f?.pdf");
        String p = "{title}-{authors}-{series}-{language}-{publisher}-{isbn}";
        String result = PathPatternResolver.resolvePattern(b, p);
        assertThat(result).doesNotContain(":", "/", "*", "?", "<", ">", "|")
                .contains("Title").contains("Author").contains("Series");
    }

    @Test void noFileExtension_returnsNoDot() {
        var b = createBook("Title", List.of("Author"), null, null, null, null, null, null, null, "fileNoExt");
        assertThat(PathPatternResolver.resolvePattern(b, "{title}")).isEqualTo("Title");
    }

    @Test void patternWithOnlyExtension_returnsExtensionOnly() {
        var book = createBook(null, null, null, null, null, null, null, null, null, "sample.pdf");
        assertThat(PathPatternResolver.resolvePattern(book, ".{extension}")).isEqualTo(".pdf");
    }

    @Test void patternWithExtensionAndFilenamePlaceholder_works() {
        var book = createBook("Sample", null, null, null, null, null, null, null, null, "original.epub");
        assertThat(PathPatternResolver.resolvePattern(book, "{currentFilename}")).isEqualTo("original.epub");
    }

    @Test void allPlaceholdersMissing_yieldsJustExtension() {
        var book = createBook(null, null, null, null, null, null, null, null, null, "file.cbz");
        String pattern = "{title}-{authors}-{series}-{year}-{language}-{publisher}-{isbn}";
        assertThat(PathPatternResolver.resolvePattern(book, pattern)).isEqualTo("Untitled------.cbz");
    }

    @Test void patternWithBackslashes_isSanitized() {
        var book = createBook("Ti\\tle", List.of("Au\\thor"), null, null, null, null, null, null, null, "f.pdf");
        assertThat(PathPatternResolver.resolvePattern(book, "{title}/{authors}")).isEqualTo("Title/Author.pdf");
    }

    @Test void optionalBlockWithMixedResolvedAndEmptyValues_skippedEntirely() {
        var book = createBook("Title", null, null, null, null, null, null, null, null, "file.epub");
        String pattern = "<{authors} - {series}/>{title}";
        assertThat(PathPatternResolver.resolvePattern(book, pattern)).isEqualTo("Title.epub");
    }

    @Test void placeholderWithWhitespace_trimmedAndSanitized() {
        var book = createBook("   My  Book  ", List.of("  John   Doe "), null, null, null, null, null, null, null, "book.pdf");
        String pattern = "{title} - {authors}";
        assertThat(PathPatternResolver.resolvePattern(book, pattern)).isEqualTo("My Book - John Doe.pdf");
    }

    @Test void multipleAuthors_concatenatedProperly() {
        var book = createBook("Book", List.of("Alice", "Bob", "Carol"), null, null, null, null, null, null, null, "book.pdf");
        String pattern = "{title} - {authors}";
        assertThat(PathPatternResolver.resolvePattern(book, pattern)).isEqualTo("Book - Alice, Bob, Carol.pdf");
    }

    @Test void patternWithOnlyOptional_allEmpty_resolvesToFilename() {
        var book = createBook(null, null, null, null, null, null, null, null, null, "fallback.epub");
        String pattern = "<{series}/><{language}/>";
        assertThat(PathPatternResolver.resolvePattern(book, pattern)).isEqualTo("fallback.epub");
    }

    @Test void invalidFilename_noExtension_stillResolves() {
        var book = createBook("A", List.of("B"), null, null, null, null, null, null, null, "noExt");
        String pattern = "{title}-{authors}";
        assertThat(PathPatternResolver.resolvePattern(book, pattern)).isEqualTo("A-B");
    }

    @Test void patternEndsWithSlash_slashIsPreserved() {
        var book = createBook("T", List.of("A"), null, "S", 1f, null, null, null, null, "b.pdf");
        String pattern = "<{series}/><{seriesIndex}/>{title}/";
        assertThat(PathPatternResolver.resolvePattern(book, pattern)).isEqualTo("S/1/T/.pdf");
    }

    @Test void extensionDotEscaped_doubleDotsNotAdded() {
        var book = createBook("X", List.of("Y"), null, null, null, null, null, null, null, "book.mobi");
        String pattern = "{title}.{extension}";
        assertThat(PathPatternResolver.resolvePattern(book, pattern)).isEqualTo("X.mobi");
    }

    @Test void subtitleInPattern_replacedCorrectly() {
        var book = createBook("Main Title", "The Subtitle", List.of("Author"), LocalDate.now(), null, null, null, null, null, null, "file.epub");
        assertThat(PathPatternResolver.resolvePattern(book, "{title} - {subtitle}")).isEqualTo("Main Title - The Subtitle.epub");
    }

    @Test void subtitleEmpty_replacedWithEmpty() {
        var book = createBook("Title", "", List.of("Author"), LocalDate.now(), null, null, null, null, null, null, "file.epub");
        assertThat(PathPatternResolver.resolvePattern(book, "{title} - {subtitle}")).isEqualTo("Title - .epub");
    }

    @Test void subtitleNull_replacedWithEmpty() {
        var book = createBook("Title", null, List.of("Author"), LocalDate.now(), null, null, null, null, null, null, "file.epub");
        assertThat(PathPatternResolver.resolvePattern(book, "{title} - {subtitle}")).isEqualTo("Title - .epub");
    }

    @Test void subtitleInOptionalBlock_withValue_blockIncluded() {
        var book = createBook("Title", "Subtitle", List.of("Author"), LocalDate.now(), null, null, null, null, null, null, "file.epub");
        assertThat(PathPatternResolver.resolvePattern(book, "{title}< - {subtitle}>")).isEqualTo("Title - Subtitle.epub");
    }

    @Test void subtitleInOptionalBlock_withoutValue_blockRemoved() {
        var book = createBook("Title", null, List.of("Author"), LocalDate.now(), null, null, null, null, null, null, "file.epub");
        assertThat(PathPatternResolver.resolvePattern(book, "{title}< - {subtitle}>")).isEqualTo("Title.epub");
    }

    @Test void subtitleWithIllegalChars_sanitized() {
        var book = createBook("Title", "Sub:title<>|*?", List.of("Author"), LocalDate.now(), null, null, null, null, null, null, "file.epub");
        String result = PathPatternResolver.resolvePattern(book, "{title} - {subtitle}");
        assertThat(result).doesNotContain(":", "<", ">", "|", "*", "?")
                .contains("Title").contains("Subtitle");
    }

    @Test void subtitleWithWhitespace_trimmedAndSanitized() {
        var book = createBook("Title", "   Sub  title  ", List.of("Author"), LocalDate.now(), null, null, null, null, null, null, "file.epub");
        assertThat(PathPatternResolver.resolvePattern(book, "{title} - {subtitle}")).isEqualTo("Title - Sub title.epub");
    }

    @Test void complexPatternWithSubtitle_allPlaceholdersPresent() {
        var book = createBook("Main Title", "The Great Subtitle", List.of("Author One"), LocalDate.of(2010, 5, 5),
                "Series", 1f, "English", "Publisher", "ISBN13", "ISBN10", "complex.epub");
        String pattern = "<{series}/>{title}< - {subtitle}> - {authors} - {year}";
        assertThat(PathPatternResolver.resolvePattern(book, pattern))
                .isEqualTo("Series/Main Title - The Great Subtitle - Author One - 2010.epub");
    }

    @Test void optionalBlockWithTitleAndSubtitle_partialValues() {
        var book1 = createBook("Title", "Subtitle", List.of("Author"), LocalDate.now(), null, null, null, null, null, null, "file.epub");
        var book2 = createBook("Title", null, List.of("Author"), LocalDate.now(), null, null, null, null, null, null, "file.epub");
        String pattern = "<{title} - {subtitle}>";

        assertThat(PathPatternResolver.resolvePattern(book1, pattern)).isEqualTo("Title - Subtitle.epub");
        assertThat(PathPatternResolver.resolvePattern(book2, pattern)).isEqualTo("file.epub");
    }
}