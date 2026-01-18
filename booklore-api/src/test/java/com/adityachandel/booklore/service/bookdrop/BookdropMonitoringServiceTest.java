package com.adityachandel.booklore.service.bookdrop;

import com.adityachandel.booklore.config.AppProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardWatchEventKinds;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.mockito.Mockito.*;

class BookdropMonitoringServiceTest {

    private AppProperties appProperties;
    private BookdropEventHandlerService eventHandler;
    private BookdropMonitoringService monitoringService;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        appProperties = mock(AppProperties.class);
        eventHandler = mock(BookdropEventHandlerService.class);
        
        when(appProperties.getBookdropFolder()).thenReturn(tempDir.toString());
        monitoringService = new BookdropMonitoringService(appProperties, eventHandler);
    }

    @Test
    void scanExistingBookdropFiles_ShouldIgnoreDotUnderscoreFiles() throws IOException {
        Path validFile = tempDir.resolve("book.epub");
        createValidEpub(validFile);

        Path invalidFile = tempDir.resolve("._book.epub");
        createValidEpub(invalidFile);
        
        Path hiddenFile = tempDir.resolve(".hidden.epub");
        createValidEpub(hiddenFile);

        Path subDir = tempDir.resolve("subdir");
        Files.createDirectories(subDir);
        Path validFileInSubdir = subDir.resolve("another.epub");
        createValidEpub(validFileInSubdir);

        Path invalidFileInSubdir = subDir.resolve("._another.epub");
        createValidEpub(invalidFileInSubdir);

        monitoringService.start();
        
        monitoringService.stop();

        verify(eventHandler).enqueueFile(eq(validFile), eq(StandardWatchEventKinds.ENTRY_CREATE));
        verify(eventHandler).enqueueFile(eq(validFileInSubdir), eq(StandardWatchEventKinds.ENTRY_CREATE));

        verify(eventHandler, never()).enqueueFile(eq(invalidFile), any());
        verify(eventHandler, never()).enqueueFile(eq(hiddenFile), any());
        verify(eventHandler, never()).enqueueFile(eq(invalidFileInSubdir), any());
    }

    private void createValidEpub(Path path) throws IOException {
        try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(path.toFile()))) {
            byte[] mimetypeContent = "application/epub+zip".getBytes(StandardCharsets.US_ASCII);
            ZipEntry mimetypeEntry = new ZipEntry("mimetype");
            mimetypeEntry.setMethod(ZipEntry.STORED);
            mimetypeEntry.setSize(mimetypeContent.length);
            mimetypeEntry.setCompressedSize(mimetypeContent.length);
            CRC32 crc = new CRC32();
            crc.update(mimetypeContent);
            mimetypeEntry.setCrc(crc.getValue());
            zos.putNextEntry(mimetypeEntry);
            zos.write(mimetypeContent);
            zos.closeEntry();
        }
    }
}
