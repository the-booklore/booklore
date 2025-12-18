package com.adityachandel.booklore.service.bookdrop;

import com.adityachandel.booklore.repository.BookdropFileRepository;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.*;

class FilenamePatternExtractorDateFormatTest {

    @Test
    void buildRegexForDateFormat_shouldHandleMixedDateFormatsCorrectly() {

        String regex = invokeBuildRegexForDateFormat("MMdd");
        assertEquals("(\\d{2}\\d{2})", regex);
        
        regex = invokeBuildRegexForDateFormat("ddMMyyyy");
        assertEquals("(\\d{2}\\d{2}\\d{4})", regex);
        
        regex = invokeBuildRegexForDateFormat("M/d/yyyy");
        assertEquals("(\\d{1,2}/\\d{1,2}/\\d{4})", regex);
        
        regex = invokeBuildRegexForDateFormat("yyyyMd");
        assertEquals("(\\d{4}\\d{1,2}\\d{1,2})", regex);
    }

    @Test
    void buildRegexForDateFormat_shouldHandleSingleCharacterFormatsAtDifferentPositions() {

        assertEquals("(\\d{1,2}\\d{2})", invokeBuildRegexForDateFormat("Md"));  // M at position 0, d at position 1
        assertEquals("(\\d{2}\\d{1,2})", invokeBuildRegexForDateFormat("dM"));  // d at position 0, M at position 1
        assertEquals("(\\d{1,2}\\d{4}\\d{1,2})", invokeBuildRegexForDateFormat("MyMd")); // M at pos 0, y at pos 1, M at pos 3, d at pos 4
    }

    @Test
    void buildRegexForDateFormat_shouldHandleEdgeCases() {
        assertEquals("()", invokeBuildRegexForDateFormat(""));
        
        assertEquals("(\\d{1,2})", invokeBuildRegexForDateFormat("M")); // single M
        assertEquals("(\\d{1,2})", invokeBuildRegexForDateFormat("d")); // single d
        assertEquals("(\\d{2})", invokeBuildRegexForDateFormat("MM")); // double M
        assertEquals("(\\d{2})", invokeBuildRegexForDateFormat("dd")); // double d
        assertEquals("(\\d{4})", invokeBuildRegexForDateFormat("yyyy")); // year
        assertEquals("(\\d{2})", invokeBuildRegexForDateFormat("yy")); // 2-digit year
    }

    @Test
    void buildRegexForDateFormat_shouldEscapeNonFormatCharacters() {
        String regex = invokeBuildRegexForDateFormat("M.d");
        assertEquals("(\\d{1,2}\\.\\d{1,2})", regex);
        
        String regex2 = invokeBuildRegexForDateFormat("M/d/yyyy");
        assertEquals("(\\d{1,2}/\\d{1,2}/\\d{4})", regex2);
    }

    private String invokeBuildRegexForDateFormat(String dateFormat) {
        try {
            // Create instance with mocked dependencies - buildRegexForDateFormat doesn't use them anyway
            FilenamePatternExtractor extractor = new FilenamePatternExtractor(
                Mockito.mock(BookdropFileRepository.class),
                Mockito.mock(BookdropMetadataHelper.class)
            );

            java.lang.reflect.Method method = FilenamePatternExtractor.class
                    .getDeclaredMethod("buildRegexForDateFormat", String.class);
            method.setAccessible(true);
            return (String) method.invoke(extractor, dateFormat);
        } catch (Exception e) {
            throw new RuntimeException("Failed to invoke buildRegexForDateFormat", e);
        }
    }
}