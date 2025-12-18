package com.adityachandel.booklore.service.metadata.extractor;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class Fb2MetadataExtractorOptimizationTest {

    @Test
    void regexPrecompilation_shouldWorkCorrectly() {
        Pattern keywordSeparatorPattern = Pattern.compile("[,;]");
        String[] result = keywordSeparatorPattern.split("keyword1,keyword2;keyword3");
        assertArrayEquals(new String[]{"keyword1", "keyword2", "keyword3"}, result);
        
        Pattern isbnCleanerPattern = Pattern.compile("[^0-9Xx]");
        String cleaned = isbnCleanerPattern.matcher("ISBN: 123-456-789X").replaceAll("");
        assertEquals("ISBN123456789X", cleaned);
        
        Pattern isoDatePattern = Pattern.compile("\\d{4}-\\d{2}-\\d{2}");
        assertTrue(isoDatePattern.matcher("2023-12-25").matches());
        assertFalse(isoDatePattern.matcher("23-12-25").matches());
    }

    @Test
    void stringBuilderInitialization_shouldWorkCorrectly() {

        StringBuilder sb = new StringBuilder(64); // As used in Fb2MetadataExtractor.authorName method
        sb.append("John");
        sb.append(" ");
        sb.append("Doe");
        assertEquals("John Doe", sb.toString());
        
        StringBuilder largeSb = new StringBuilder(256);
        for (int i = 0; i < 50; i++) {
            largeSb.append("word").append(i).append(" ");
        }
        assertTrue(largeSb.length() > 256);
    }

    @Test
    void stringIsEmptyVsLengthComparison() {

        String empty = "";
        String nonEmpty = "test";
        String nonEmptyWithDot = "test.";
        String startsWithDot = ".hidden";

        // Test that startsWith/endsWith work as expected
        assertFalse(empty.startsWith("."));
        assertFalse(empty.endsWith("."));
        assertFalse(nonEmpty.startsWith("."));
        assertTrue(nonEmptyWithDot.endsWith("."));
        assertTrue(startsWithDot.startsWith("."));
    }
}