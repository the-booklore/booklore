package com.adityachandel.booklore.service.metadata.parser;

import com.adityachandel.booklore.service.appsettings.AppSettingService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class GoogleParserTest {

    @Mock
    private AppSettingService appSettingService;

    @Mock
    private ObjectMapper objectMapper;

    private GoogleParser googleParser;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        googleParser = new GoogleParser(objectMapper, appSettingService);
    }

    @Test
    void testCleanDescription_null() {
        assertNull(googleParser.cleanDescription(null));
    }

    @Test
    void testCleanDescription_empty() {
        assertNull(googleParser.cleanDescription(""));
        assertNull(googleParser.cleanDescription("   "));
    }

    @Test
    void testCleanDescription_withHtmlTags() {
        String input = "This is <i>italic</i> and <b>bold</b>. <br> New line.";
        String expected = "This is italic and bold. New line.";
        assertEquals(expected, googleParser.cleanDescription(input));
    }

    @Test
    void testCleanDescription_withEntities() {
        String input = "Tom &amp; Jerry &lt; Disney &gt; Pixar &quot;Toy Story&quot; &#39;test&#39;";
        String expected = "Tom & Jerry < Disney > Pixar \"Toy Story\" 'test'";
        assertEquals(expected, googleParser.cleanDescription(input));
    }

    @Test
    void testCleanDescription_withNestedTagsAndWhitespace() {
        String input = """
            <div>  <p>Some text   </p>
             <span>More text</span> </div>""";
        String expected = "Some text More text";
        assertEquals(expected, googleParser.cleanDescription(input));
    }

    @Test
    void testCleanDescription_withUnclosedTags() {
        String input = "This is <i>italic and <b>bold";
        String expected = "This is italic and bold";
        assertEquals(expected, googleParser.cleanDescription(input));
    }
}
