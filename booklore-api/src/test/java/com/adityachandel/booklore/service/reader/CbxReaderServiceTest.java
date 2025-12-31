package com.adityachandel.booklore.service.reader;

import com.adityachandel.booklore.model.dto.settings.AppSettings;
import com.adityachandel.booklore.model.entity.BookEntity;
import com.adityachandel.booklore.model.entity.LibraryPathEntity;
import com.adityachandel.booklore.repository.BookRepository;
import com.adityachandel.booklore.service.appsettings.AppSettingService;
import com.adityachandel.booklore.util.FileService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import javax.imageio.ImageIO;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CbxReaderServiceTest {

    @TempDir
    Path tempDir;

    @Mock
    private BookRepository bookRepository;

    @Mock
    private AppSettingService appSettingService;

    @Mock
    private FileService fileService;

    private CbxReaderService service;
    private Path cbzFile;
    private Path cacheDir;
    private BookEntity testBook;
    private Long bookId = 113L;

    @BeforeEach
    void setUp() throws IOException {
        service = new CbxReaderService(bookRepository, appSettingService, fileService);
        
        cacheDir = tempDir.resolve("cbx_cache").resolve(String.valueOf(bookId));
        Files.createDirectories(cacheDir);
        
        cbzFile = tempDir.resolve("doctorwho_fourdoctors.cbz");
        createTestCbzWithMacOsFiles(cbzFile.toFile());
        
        LibraryPathEntity libraryPath = new LibraryPathEntity();
        libraryPath.setPath(cbzFile.getParent().toString());
        
        testBook = new BookEntity();
        testBook.setId(bookId);
        testBook.setLibraryPath(libraryPath);
        testBook.setFileSubPath("");
        testBook.setFileName(cbzFile.getFileName().toString());
        
        when(bookRepository.findById(bookId)).thenReturn(Optional.of(testBook));
        when(fileService.getCbxCachePath()).thenReturn(cacheDir.getParent().toString());
        
        AppSettings appSettings = new AppSettings();
        appSettings.setCbxCacheSizeInMb(1000);
        when(appSettingService.getAppSettings()).thenReturn(appSettings);
    }

    @AfterEach
    void tearDown() throws IOException {
        if (Files.exists(cacheDir)) {
            Files.walk(cacheDir)
                    .sorted((a, b) -> -a.compareTo(b))
                    .forEach(path -> {
                        try {
                            Files.deleteIfExists(path);
                        } catch (IOException ignored) {
                        }
                    });
        }
    }

    @Test
    void getAvailablePages_filtersOutMacOsFiles_shouldReturnCorrectPageCount() throws IOException {
        List<Integer> pages = service.getAvailablePages(bookId);
        
        assertEquals(130, pages.size(), 
            "Page count should be 130 (actual comic pages), not 260 (including __MACOSX files)");
        assertEquals(1, pages.get(0));
        assertEquals(130, pages.get(pages.size() - 1));
        
        List<Path> cachedFiles = Files.list(cacheDir)
                .filter(Files::isRegularFile)
                .filter(p -> !p.getFileName().toString().equals(".cache-info"))
                .toList();
        
        assertEquals(130, cachedFiles.size(), 
            "Cache should contain exactly 130 image files, not 260. Actual files: " + 
            cachedFiles.stream().map(p -> p.getFileName().toString()).sorted().toList());
        
        boolean hasMacOsFiles = cachedFiles.stream()
                .anyMatch(p -> p.getFileName().toString().startsWith("._") || 
                              p.getFileName().toString().contains("__MACOSX"));
        assertFalse(hasMacOsFiles, "Cache should not contain any __MACOSX or ._ files. Found: " +
            cachedFiles.stream()
                .map(p -> p.getFileName().toString())
                .filter(name -> name.startsWith("._") || name.contains("__MACOSX"))
                .toList());
        
        boolean allAreComicPages = cachedFiles.stream()
                .allMatch(p -> p.getFileName().toString().matches("DW_4D_\\d{3}\\.jpg"));
        assertTrue(allAreComicPages, "All cached files should be actual comic pages (DW_4D_*.jpg)");
    }

    @Test
    void streamPageImage_returnsActualComicPages_notMacOsFiles() throws IOException {
        service.getAvailablePages(bookId);
        
        ByteArrayOutputStream page1Output = new ByteArrayOutputStream();
        service.streamPageImage(bookId, 1, page1Output);
        
        byte[] page1Data = page1Output.toByteArray();
        assertTrue(page1Data.length > 0, "Page 1 should have content");
        assertEquals(0xFF, page1Data[0] & 0xFF);
        assertEquals(0xD8, page1Data[1] & 0xFF);
        
        ByteArrayOutputStream page130Output = new ByteArrayOutputStream();
        service.streamPageImage(bookId, 130, page130Output);
        
        byte[] page130Data = page130Output.toByteArray();
        assertTrue(page130Data.length > 0, "Page 130 should have content");
        assertEquals(0xFF, page130Data[0] & 0xFF);
        assertEquals(0xD8, page130Data[1] & 0xFF);
        
        List<Path> cachedFiles = Files.list(cacheDir)
                .filter(Files::isRegularFile)
                .filter(p -> !p.getFileName().toString().equals(".cache-info"))
                .sorted()
                .toList();
        
        assertEquals("DW_4D_001.jpg", cachedFiles.get(0).getFileName().toString());
        assertEquals("DW_4D_130.jpg", cachedFiles.get(129).getFileName().toString());
    }

    @Test
    void getAvailablePages_withMacOsFiles_shouldNotDoubleCountPages() throws IOException {
        List<Integer> pages = service.getAvailablePages(bookId);
        
        assertNotEquals(260, pages.size(), 
            "Page count should NOT be 260 (this was the bug - double counting __MACOSX files)");
        assertEquals(130, pages.size(), 
            "Page count should be exactly 130 (actual comic pages only)");
    }

    private void createTestCbzWithMacOsFiles(File cbzFile) throws IOException {
        try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(cbzFile))) {
            for (int i = 1; i <= 130; i++) {
                String pageNumber = String.format("%03d", i);
                
                String comicPageName = "DW_4D_" + pageNumber + ".jpg";
                ZipEntry comicEntry = new ZipEntry(comicPageName);
                comicEntry.setTime(0L);
                zos.putNextEntry(comicEntry);
                byte[] comicImage = createTestImage(Color.RED, "Page " + i);
                zos.write(comicImage);
                zos.closeEntry();
                
                String macOsFileName = "__MACOSX/._DW_4D_" + pageNumber + ".jpg";
                ZipEntry macOsEntry = new ZipEntry(macOsFileName);
                macOsEntry.setTime(0L);
                zos.putNextEntry(macOsEntry);
                byte[] macOsData = "MacOS metadata".getBytes();
                zos.write(macOsData);
                zos.closeEntry();
            }
        }
    }

    private byte[] createTestImage(Color color, String label) throws IOException {
        BufferedImage image = new BufferedImage(400, 600, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2d = image.createGraphics();
        
        g2d.setColor(color);
        g2d.fillRect(0, 0, 400, 600);
        
        g2d.setColor(Color.BLACK);
        g2d.setFont(g2d.getFont().deriveFont(24f));
        g2d.drawString(label, 50, 300);
        
        g2d.dispose();
        
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(image, "jpg", baos);
        return baos.toByteArray();
    }
}
