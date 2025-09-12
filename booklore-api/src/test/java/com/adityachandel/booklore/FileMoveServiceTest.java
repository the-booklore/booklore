package com.adityachandel.booklore;

import com.adityachandel.booklore.model.entity.AuthorEntity;
import com.adityachandel.booklore.model.entity.BookEntity;
import com.adityachandel.booklore.model.entity.BookMetadataEntity;
import com.adityachandel.booklore.service.BookQueryService;
import com.adityachandel.booklore.service.file.FileMoveService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class FileMoveServiceTest {

    @Mock
    private BookQueryService bookQueryService;

    @InjectMocks
    private FileMoveService fileMoveService;

    @Test
    void testGeneratePathWithAllFieldsPresent() {
        BookMetadataEntity metadata = BookMetadataEntity.builder()
                .title("Dune")
                .authors(new HashSet<>(List.of(new AuthorEntity(1L, "Frank Herbert", new ArrayList<>())))
                )
                .publishedDate(LocalDate.of(1965, 8, 1))
                .seriesName("Dune Saga")
                .seriesNumber(1.0F)
                .language("English")
                .publisher("Chilton Books")
                .isbn13("9780441172719")
                .build();

        BookEntity book = BookEntity.builder()
                .metadata(metadata)
                .fileName("Dune.epub")
                .build();

        String pattern = "/{series}/{seriesIndex} - {title} by {authors} ({year})";
        String result = fileMoveService.generatePathFromPattern(book, pattern);

        assertThat(result).isEqualTo("/Dune Saga/1 - Dune by Frank Herbert (1965).epub");
    }

    @Test
    void testGeneratePathWithMissingMetadata() {
        BookMetadataEntity metadata = BookMetadataEntity.builder().build();
        BookEntity book = BookEntity.builder()
                .metadata(metadata)
                .fileName("unknown.pdf")
                .build();

        String result = fileMoveService.generatePathFromPattern(
                book,
                "/{title}_{authors}_{year}_{series}_{seriesIndex}_{language}_{publisher}_{isbn}"
        );
        assertThat(result).isEqualTo("/Untitled_______.pdf");
    }

    @Test
    void testGeneratePathWithMultipleAuthors() {
        BookMetadataEntity metadata = BookMetadataEntity.builder()
                .title("Good Omens")
                .authors(new HashSet<>(List.of(
                        new AuthorEntity(1L, "Neil Gaiman", new ArrayList<>()),
                        new AuthorEntity(2L, "Terry Pratchett", new ArrayList<>())
                )))
                .publishedDate(LocalDate.of(1990, 5, 1))
                .build();

        BookEntity book = BookEntity.builder()
                .metadata(metadata)
                .fileName("goodomens.mobi")
                .build();

        String pattern = "/{title} - {authors} ({year})";
        String result = fileMoveService.generatePathFromPattern(book, pattern);

        assertThat(result)
                .isIn(
                        "/Good Omens - Neil Gaiman, Terry Pratchett (1990).mobi",
                        "/Good Omens - Terry Pratchett, Neil Gaiman (1990).mobi"
                );
    }

    @Test
    void testGeneratePathWithNoExtension() {
        BookMetadataEntity metadata = BookMetadataEntity.builder()
                .title("The Nameless Book")
                .build();

        BookEntity book = BookEntity.builder()
                .metadata(metadata)
                .fileName("namelessbook")
                .build();

        String result = fileMoveService.generatePathFromPattern(book, "/{title}");
        assertThat(result).isEqualTo("/The Nameless Book");
    }

    @Test
    void testGeneratePathWithIsbn10Fallback() {
        BookMetadataEntity metadata = BookMetadataEntity.builder()
                .title("Test Book")
                .isbn10("1234567890")
                .build();

        BookEntity book = BookEntity.builder()
                .metadata(metadata)
                .fileName("testbook.pdf")
                .build();

        String result = fileMoveService.generatePathFromPattern(book, "/{isbn}");
        assertThat(result).isEqualTo("/1234567890.pdf");
    }

    @Test
    void testGeneratePathWithEmptyFileName() {
        BookMetadataEntity metadata = BookMetadataEntity.builder()
                .title("No File")
                .build();

        BookEntity book = BookEntity.builder()
                .metadata(metadata)
                .fileName(null)
                .build();

        String result = fileMoveService.generatePathFromPattern(book, "/{title}");
        assertThat(result).isEqualTo("/No File");
    }

    @Test
    void testGeneratePathWithEmptyPattern() {
        BookMetadataEntity metadata = BookMetadataEntity.builder()
                .title("Empty Pattern Book")
                .build();

        BookEntity book = BookEntity.builder()
                .metadata(metadata)
                .fileName("empty.txt")
                .build();

        String result = fileMoveService.generatePathFromPattern(book, "");
        assertThat(result).isEqualTo("empty.txt");
    }

    @Test
    void testGeneratePathWithEmptyFileNameString() {
        BookMetadataEntity metadata = BookMetadataEntity.builder()
                .title("Ghost File")
                .build();

        BookEntity book = BookEntity.builder()
                .metadata(metadata)
                .fileName("")
                .build();

        String result = fileMoveService.generatePathFromPattern(book, "/{title}");
        assertThat(result).isEqualTo("/Ghost File");
    }

    @Test
    void testGeneratePathWithUnknownPlaceholder() {
        BookMetadataEntity metadata = BookMetadataEntity.builder()
                .title("Weird Format")
                .build();

        BookEntity book = BookEntity.builder()
                .metadata(metadata)
                .fileName("weird.pdf")
                .build();

        String pattern = "/{title}/{unknown}";
        String result = fileMoveService.generatePathFromPattern(book, pattern);

        assertThat(result).isEqualTo("/Weird Format/{unknown}.pdf");
    }

    @Test
    void testGeneratePathWithDotOnlyFilename() {
        BookMetadataEntity metadata = BookMetadataEntity.builder()
                .title("Dotfile")
                .build();

        BookEntity book = BookEntity.builder()
                .metadata(metadata)
                .fileName(".gitignore")
                .build();

        String result = fileMoveService.generatePathFromPattern(book, "/{title}");
        assertThat(result).isEqualTo("/Dotfile.gitignore");
    }

    @Test
    void testGeneratePathWithOptionalBlockIncluded() {
        BookMetadataEntity metadata = BookMetadataEntity.builder()
                .title("Book Title")
                .seriesName("Series")
                .seriesNumber(3.5F)
                .build();

        BookEntity book = BookEntity.builder()
                .metadata(metadata)
                .fileName("book.pdf")
                .build();

        String pattern = "<{series}/><{seriesIndex}. >{title}";
        String result = fileMoveService.generatePathFromPattern(book, pattern);

        assertThat(result).isEqualTo("Series/3.5. Book Title.pdf");
    }

    @Test
    void testGeneratePathWithOptionalBlockExcluded() {
        BookMetadataEntity metadata = BookMetadataEntity.builder()
                .title("Book Title")
                .build();

        BookEntity book = BookEntity.builder()
                .metadata(metadata)
                .fileName("book.pdf")
                .build();

        String pattern = "<{series}/><{seriesIndex}. >{title}";
        String result = fileMoveService.generatePathFromPattern(book, pattern);

        assertThat(result).isEqualTo("Book Title.pdf");
    }

    @Test
    void testOptionalBlockWithPartialMissingData() {
        BookMetadataEntity metadata = BookMetadataEntity.builder()
                .title("Solo")
                .seriesName("Lone")
                .build();

        BookEntity book = BookEntity.builder()
                .metadata(metadata)
                .fileName("solo.pdf")
                .build();

        String pattern = "<{series}/><{seriesIndex}. >{title}";
        String result = fileMoveService.generatePathFromPattern(book, pattern);

        assertThat(result).isEqualTo("Lone/Solo.pdf");
    }

    @Test
    void testPatternWithMultipleOptionalBlocks() {
        BookMetadataEntity metadata = BookMetadataEntity.builder()
                .title("Layered")
                .seriesName("Stack")
                .seriesNumber(3F)
                .language("English")
                .build();

        BookEntity book = BookEntity.builder()
                .metadata(metadata)
                .fileName("layered.epub")
                .build();

        String pattern = "<{series}/><{seriesIndex}. ><{language}/>{title}";
        String result = fileMoveService.generatePathFromPattern(book, pattern);

        assertThat(result).isEqualTo("Stack/3. English/Layered.epub");
    }

    @Test
    void testIllegalCharactersAreRemoved() {
        BookMetadataEntity metadata = BookMetadataEntity.builder()
                .title("Inva|id:Name*?")
                .authors(new HashSet<>(List.of(new AuthorEntity(1L, "Au<thor>", new ArrayList<>()))))
                .build();

        BookEntity book = BookEntity.builder()
                .metadata(metadata)
                .fileName("bad?.pdf")
                .build();

        String pattern = "/{title}_{authors}";
        String result = fileMoveService.generatePathFromPattern(book, pattern);

        assertThat(result).isEqualTo("/InvaidName_Author.pdf");
    }

    @Test
    void testControlCharactersAreStripped() {
        String titleWithControlChars = "Bad\u0000Name\u0007Here";

        BookMetadataEntity metadata = BookMetadataEntity.builder()
                .title(titleWithControlChars)
                .build();

        BookEntity book = BookEntity.builder()
                .metadata(metadata)
                .fileName("control.epub")
                .build();

        String result = fileMoveService.generatePathFromPattern(book, "/{title}");
        assertThat(result).isEqualTo("/BadNameHere.epub");
    }

    @Test
    void testWhitespaceIsCollapsed() {
        BookMetadataEntity metadata = BookMetadataEntity.builder()
                .title("   Too   Many    Spaces   ")
                .build();

        BookEntity book = BookEntity.builder()
                .metadata(metadata)
                .fileName("space.mobi")
                .build();

        String result = fileMoveService.generatePathFromPattern(book, "/{title}");
        assertThat(result).isEqualTo("/Too Many Spaces.mobi");
    }

    @Test
    void testUnknownPlaceholdersAreUnchanged() {
        BookMetadataEntity metadata = BookMetadataEntity.builder()
                .title("Valid Book")
                .build();

        BookEntity book = BookEntity.builder()
                .metadata(metadata)
                .fileName("valid.pdf")
                .build();

        String pattern = "/{title}/{foo}/{bar}";
        String result = fileMoveService.generatePathFromPattern(book, pattern);

        assertThat(result).isEqualTo("/Valid Book/{foo}/{bar}.pdf");
    }

    @Test
    void testEmptyOptionalBlockIsIgnored() {
        BookMetadataEntity metadata = BookMetadataEntity.builder()
                .title("Optional")
                .build();

        BookEntity book = BookEntity.builder()
                .metadata(metadata)
                .fileName("opt.epub")
                .build();

        String result = fileMoveService.generatePathFromPattern(book, "<>");
        assertThat(result).isEqualTo("opt.epub");
    }

    @Test
    void testUnicodeCharactersArePreservedAndSanitized() {
        BookMetadataEntity metadata = BookMetadataEntity.builder()
                .title("𝔘𝔫𝔦𝔠𝔬𝔡𝔢 – 漢字 – हिंदी")
                .build();

        BookEntity book = BookEntity.builder()
                .metadata(metadata)
                .fileName("unicode.azw3")
                .build();

        String result = fileMoveService.generatePathFromPattern(book, "/{title}");
        assertThat(result).isEqualTo("/𝔘𝔫𝔦𝔠𝔬𝔡𝔢 – 漢字 – हिंदी.azw3");
    }

    @Test
    void testOptionalBlockDoesNotLeakSurroundingChars() {
        BookMetadataEntity metadata = BookMetadataEntity.builder()
                .title("LeakTest")
                .build();

        BookEntity book = BookEntity.builder()
                .metadata(metadata)
                .fileName("leaky.fb2")
                .build();

        String pattern = "<({series}) >{title}";
        String result = fileMoveService.generatePathFromPattern(book, pattern);

        assertThat(result).isEqualTo("LeakTest.fb2");
    }

    @Test
    void testExtensionWithDotOnly() {
        BookMetadataEntity metadata = BookMetadataEntity.builder()
                .title("DotDot")
                .build();

        BookEntity book = BookEntity.builder()
                .metadata(metadata)
                .fileName(".")
                .build();

        String result = fileMoveService.generatePathFromPattern(book, "/{title}");
        assertThat(result).isEqualTo("/DotDot");
    }
}