package org.booklore.service.metadata.writer;

import org.apache.xmpbox.XMPMetadata;
import org.apache.xmpbox.type.TextType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class BookLoreSchemaTest {

    private XMPMetadata xmpMetadata;
    private BookLoreSchema schema;

    @BeforeEach
    void setUp() {
        xmpMetadata = XMPMetadata.createXMPMetadata();
        schema = new BookLoreSchema(xmpMetadata);
    }

    @Nested
    class ScalarTextPropertiesTests {
        @Test
        void setSubtitle_addsPropertyToSchema() {
            schema.setSubtitle("A Novel");

            TextType property = (TextType) schema.getProperty(BookLoreSchema.SUBTITLE);
            assertNotNull(property);
            assertEquals("A Novel", property.getStringValue());
        }

        @Test
        void setSubtitle_whenBlank_doesNotAddProperty() {
            schema.setSubtitle("   ");

            assertNull(schema.getProperty(BookLoreSchema.SUBTITLE));
        }

        @Test
        void setIsbn10_addsPropertyToSchema() {
            schema.setIsbn10("0123456789");

            TextType property = (TextType) schema.getProperty(BookLoreSchema.ISBN_10);
            assertNotNull(property);
            assertEquals("0123456789", property.getStringValue());
        }

        @Test
        void setLubimyczytacId_addsPropertyToSchema() {
            schema.setLubimyczytacId("12345");

            TextType property = (TextType) schema.getProperty(BookLoreSchema.LUBIMYCZYTAC_ID);
            assertNotNull(property);
            assertEquals("12345", property.getStringValue());
        }

        @Test
        void setHardcoverBookId_addsPropertyToSchema() {
            schema.setHardcoverBookId(98765);

            TextType property = (TextType) schema.getProperty(BookLoreSchema.HARDCOVER_BOOK_ID);
            assertNotNull(property);
            assertEquals("98765", property.getStringValue());
        }
    }

    @Nested
    class RatingPropertiesTests {
        @Test
        void setRating_formatsWithLocaleUS() {
            schema.setRating(8.5);

            TextType property = (TextType) schema.getProperty(BookLoreSchema.RATING);
            assertNotNull(property);
            assertEquals("8.50", property.getStringValue());
            assertFalse(property.getStringValue().contains(","));
        }

        @Test
        void setAmazonRating_formatsCorrectly() {
            schema.setAmazonRating(4.25);

            TextType property = (TextType) schema.getProperty(BookLoreSchema.AMAZON_RATING);
            assertNotNull(property);
            assertEquals("4.25", property.getStringValue());
        }

        @Test
        void setGoodreadsRating_formatsCorrectly() {
            schema.setGoodreadsRating(3.99);

            TextType property = (TextType) schema.getProperty(BookLoreSchema.GOODREADS_RATING);
            assertNotNull(property);
            assertEquals("3.99", property.getStringValue());
        }

        @Test
        void setRating_whenNull_doesNotAddProperty() {
            schema.setRating(null);

            assertNull(schema.getProperty(BookLoreSchema.RATING));
        }
    }

    @Nested
    class TextCollectionPropertiesTests {
        @Test
        void setMoods_createsDelimitedText() {
            schema.setMoods(Set.of("Dark", "Atmospheric", "Suspenseful"));

            TextType property = (TextType) schema.getProperty(BookLoreSchema.MOODS);
            assertNotNull(property);
            String value = property.getStringValue();
            assertTrue(value.contains("Dark"));
            assertTrue(value.contains("Atmospheric"));
            assertTrue(value.contains("Suspenseful"));
            assertTrue(value.contains("; "));
        }

        @Test
        void setMoods_whenEmpty_doesNotAddProperty() {
            schema.setMoods(Set.of());

            assertNull(schema.getProperty(BookLoreSchema.MOODS));
        }

        @Test
        void setMoods_whenNull_doesNotAddProperty() {
            schema.setMoods(null);

            assertNull(schema.getProperty(BookLoreSchema.MOODS));
        }

        @Test
        void setTags_createsDelimitedText() {
            schema.setTags(Set.of("Fantasy", "Epic", "Magic"));

            TextType property = (TextType) schema.getProperty(BookLoreSchema.TAGS);
            assertNotNull(property);
            String value = property.getStringValue();
            assertTrue(value.contains("Fantasy"));
            assertTrue(value.contains("Epic"));
            assertTrue(value.contains("Magic"));
        }

        @Test
        void setTags_filtersBlankValues() {
            schema.setTags(Set.of("Fantasy", "   ", "Epic"));

            TextType property = (TextType) schema.getProperty(BookLoreSchema.TAGS);
            assertNotNull(property);
            assertFalse(property.getStringValue().contains("   "));
        }
    }

    @Nested
    class NamespaceTests {
        @Test
        void namespace_isCorrect() {
            assertEquals("http://booklore.org/metadata/1.0/", BookLoreSchema.NAMESPACE);
        }

        @Test
        void prefix_isCorrect() {
            assertEquals("booklore", BookLoreSchema.PREFIX);
        }
    }
}
