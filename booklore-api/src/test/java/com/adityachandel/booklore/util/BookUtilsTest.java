package com.adityachandel.booklore.util;

import com.adityachandel.booklore.model.entity.AuthorEntity;
import com.adityachandel.booklore.model.entity.BookMetadataEntity;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class BookUtilsTest {

    @Test
    void testBuildSearchText() {
        BookMetadataEntity metadata = new BookMetadataEntity();
        metadata.setTitle("Harry Potter");
        metadata.setSubtitle("Philosopher's Stone");
        metadata.setSeriesName("Harry Potter Series");
        metadata.setAuthors(Set.of(AuthorEntity.builder().name("J.K. Rowling").build()));

        String searchText = BookUtils.buildSearchText(metadata);
        
        assertNotNull(searchText);
        assertTrue(searchText.contains("harry potter"));
        assertTrue(searchText.contains("philosophers stone"));
        assertTrue(searchText.contains("jk rowling"));
    }

    @Test
    void testCleanSearchTerm_doesNotTruncate() {
        String longText = "A".repeat(100);
        String result = BookUtils.cleanSearchTerm(longText);
        assertEquals(100, result.length());
        assertEquals(longText, result);
    }

    @Test
    void testCleanFileName_nullInput() {
        String result = BookUtils.cleanFileName(null);
        assertNull(result);
    }

    @Test
    void testCleanFileName_simpleName() {
        String result = BookUtils.cleanFileName("Test Book.pdf");
        assertEquals("Test Book", result);
    }

    @Test
    void testCleanFileName_withZLibrary() {
        String result = BookUtils.cleanFileName("Test Book (Z-Library).pdf");
        assertEquals("Test Book", result);
    }

    @Test
    void testCleanFileName_withAuthorInParentheses() {
        String result = BookUtils.cleanFileName("Test Book (John Doe).pdf");
        assertEquals("Test Book", result);
    }

    @Test
    void testCleanFileName_withMultipleExtensions() {
        String result = BookUtils.cleanFileName("Test Book.epub.zip");
        assertEquals("Test Book.epub", result);
    }

    @Test
    void testCleanFileName_noExtension() {
        String result = BookUtils.cleanFileName("Test Book");
        assertEquals("Test Book", result);
    }

    @Test
    void testCleanFileName_onlyExtension() {
        // Hidden files (starting with dot) should keep their name to avoid empty strings
        // which could cause DB constraint or filesystem errors
        String result = BookUtils.cleanFileName(".pdf");
        assertEquals(".pdf", result);
    }

    @Test
    void testCleanFileName_complexCase() {
        String result = BookUtils.cleanFileName("Advanced Calculus (Z-Library) (Michael Spivak).pdf");
        assertEquals("Advanced Calculus", result);
    }

    @Test
    void testCleanAndTruncateSearchTerm_nullInput() {
        String result = BookUtils.cleanAndTruncateSearchTerm(null);
        assertEquals("", result);
    }

    @Test
    void testCleanAndTruncateSearchTerm_emptyString() {
        String result = BookUtils.cleanAndTruncateSearchTerm("");
        assertEquals("", result);
    }

    @Test
    void testCleanAndTruncateSearchTerm_simpleText() {
        String result = BookUtils.cleanAndTruncateSearchTerm("Hello World");
        assertEquals("Hello World", result);
    }

    @Test
    void testCleanAndTruncateSearchTerm_withSpecialChars() {
        String result = BookUtils.cleanAndTruncateSearchTerm("Hello, World! How are you?");
        assertEquals("Hello World How are you", result);
    }

    @Test
    void testCleanAndTruncateSearchTerm_withBrackets() {
        String result = BookUtils.cleanAndTruncateSearchTerm("Test [Book] {Series}");
        assertEquals("Test Book Series", result);
    }

    @Test
    void testCleanAndTruncateSearchTerm_longText() {
        String longText = "This is a very long search term that should be truncated because it exceeds sixty characters in length and needs to be shortened";
        String result = BookUtils.cleanAndTruncateSearchTerm(longText);
        assertTrue(result.length() <= 60);
        assertEquals("This is a very long search term that should be truncated", result);
    }

    @Test
    void testCleanAndTruncateSearchTerm_longTextWithSpecialChars() {
        String longText = "This-is,a@very#long$search%term^with&special*chars(that)should[be]truncated{because}it<exceeds>sixty?characters";
        String result = BookUtils.cleanAndTruncateSearchTerm(longText);
        assertTrue(result.length() <= 60);
        assertEquals("Thisisaverylongsearchtermwithspecialcharsthatshouldbetruncat", result);
    }

    @Test
    void testCleanAndTruncateSearchTerm_exactly60Chars() {
        String text = "A".repeat(60);
        String result = BookUtils.cleanAndTruncateSearchTerm(text);
        assertEquals(text, result);
        assertEquals(60, result.length());
    }

    @Test
    void testCleanAndTruncateSearchTerm_whitespaceHandling() {
        String result = BookUtils.cleanAndTruncateSearchTerm("  Multiple   Spaces   Here  ");
        assertEquals("Multiple Spaces Here", result);
    }

    @Test
    void testCleanAndTruncateSearchTerm_onlySpecialChars() {
        String result = BookUtils.cleanAndTruncateSearchTerm(",.!@#$%^&*()[]{}");
        assertEquals("", result);
    }

    @Test
    void testNormalizeForSearch() {
        assertEquals("nesbo", BookUtils.normalizeForSearch("Nesbø"));
        assertEquals("jo nesbo", BookUtils.normalizeForSearch("Jo Nesbø"));
        assertEquals("aeiou", BookUtils.normalizeForSearch("áéíóú"));
        assertEquals("aeiou", BookUtils.normalizeForSearch("ÀÈÌÒÙ"));
        assertEquals("l", BookUtils.normalizeForSearch("ł"));
        assertEquals("ss", BookUtils.normalizeForSearch("ß"));
        assertEquals("harry potter", BookUtils.normalizeForSearch("Harry Potter"));
        assertEquals("misere", BookUtils.normalizeForSearch("Misère"));
    }
}
