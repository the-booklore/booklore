package org.booklore.service.metadata;

import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CoverImageGeneratorTest {

    private final CoverImageGenerator generator = new CoverImageGenerator();

    @Test
    void generateCover_ShouldReturnValidImageBytes() throws IOException {
        byte[] bytes = generator.generateCover("Test Title", "Test Author");

        assertNotNull(bytes);
        assertTrue(bytes.length > 0);

        try (ByteArrayInputStream bais = new ByteArrayInputStream(bytes)) {
            BufferedImage image = ImageIO.read(bais);
            assertNotNull(image);
            assertTrue(image.getWidth() > 0);
            assertTrue(image.getHeight() > 0);
        }
    }

    @Test
    void generateCover_ShouldHandleNullInputs() throws IOException {
        byte[] bytes = generator.generateCover(null, null);

        assertNotNull(bytes);
        assertTrue(bytes.length > 0);

        try (ByteArrayInputStream bais = new ByteArrayInputStream(bytes)) {
            BufferedImage image = ImageIO.read(bais);
            assertNotNull(image);
        }
    }

    @Test
    void generateCover_ShouldHandleLongText() throws IOException {
        String longTitle = "This is a very long title that should wrap multiple lines and potentially be truncated if it is way too long for the cover image to handle gracefully";
        String longAuthor = "This is a very long author name that should also wrap or be handled correctly without crashing the generator";
        
        byte[] bytes = generator.generateCover(longTitle, longAuthor);

        assertNotNull(bytes);
        assertTrue(bytes.length > 0);

        try (ByteArrayInputStream bais = new ByteArrayInputStream(bytes)) {
            BufferedImage image = ImageIO.read(bais);
            assertNotNull(image);
        }
    }

    @Test
    void generateCover_ShouldHandleEmptyStrings() throws IOException {
        byte[] bytes = generator.generateCover("", "");
        assertNotNull(bytes);
        assertTrue(bytes.length > 0);

        try (ByteArrayInputStream bais = new ByteArrayInputStream(bytes)) {
            BufferedImage image = ImageIO.read(bais);
            assertNotNull(image);
        }
    }
}
