package com.adityachandel.booklore.service.file;

import com.adityachandel.booklore.model.dto.UploadResponse;
import com.adityachandel.booklore.util.FileService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import javax.imageio.ImageIO;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BackgroundUploadServiceTest {

    @Mock
    private FileService fileService;

    private BackgroundUploadService backgroundUploadService;

    @BeforeEach
    void setup() {
        backgroundUploadService = new BackgroundUploadService(fileService);
    }

    @Nested
    @DisplayName("uploadBackgroundFile")
    class UploadBackgroundFileTests {

        @Test
        @DisplayName("Accepts WEBP file")
        void validWebpFile_succeeds() throws IOException {
            // Create dummy image data
            BufferedImage image = new BufferedImage(100, 100, BufferedImage.TYPE_INT_RGB);
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(image, "JPEG", baos); // Using JPEG bytes is fine as long as ImageIO can read it
            byte[] imageBytes = baos.toByteArray();

            MockMultipartFile file = new MockMultipartFile(
                    "file", "test.webp", "image/webp", imageBytes);

            // FileService.saveBackgroundImage is void, so no return needed

            assertDoesNotThrow(() -> 
                backgroundUploadService.uploadBackgroundFile(file, 1L));
            
            verify(fileService).deleteBackgroundFile("1.webp", 1L);
            // Note: The current implementation preserves extension but might write as JPEG content.
            // We verify it attempts to save as "1.webp".
            verify(fileService).saveBackgroundImage(any(), eq("1.webp"), eq(1L));
        }

        @Test
        @DisplayName("Rejects invalid MIME type")
        void invalidMimeType_throwsException() {
            MockMultipartFile file = new MockMultipartFile(
                    "file", "test.gif", "image/gif", new byte[10]);

            Exception exception = assertThrows(RuntimeException.class, () ->
                    backgroundUploadService.uploadBackgroundFile(file, 1L));
            assertTrue(exception.getMessage().contains("Background image must be JPEG, PNG or WEBP format"));
        }
    }
}
