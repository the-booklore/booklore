package com.adityachandel.booklore.service.metadata.extractor;

import com.adityachandel.booklore.model.dto.BookMetadata;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import javax.imageio.ImageIO;
import java.awt.Color;

import static org.junit.jupiter.api.Assertions.*;

class ComicInfoParsingIssuesTest {

    private CbxMetadataExtractor extractor;
    private Path tempDir;

    @BeforeEach
    void setUp() throws IOException {
        extractor = new CbxMetadataExtractor();
        tempDir = Files.createTempDirectory("comicinfo_test_");
    }

    @AfterEach
    void tearDown() throws IOException {
        if (tempDir != null) {
            Files.walk(tempDir)
                    .sorted(Comparator.reverseOrder())
                    .forEach(p -> { try { Files.deleteIfExists(p); } catch (Exception ignore) {} });
        }
    }

    @Test
    void testComicInfoExtractionFromEmbeddedXml() throws Exception {
        String xml = "<ComicInfo>" +
                "  <Title>Daredevil #1</Title>" +
                "  <Series>Daredevil</Series>" +
                "  <Number>1</Number>" +
                "  <Year>1964</Year>" +
                "  <Publisher>Marvel</Publisher>" +
                "  <Writer>Stan Lee</Writer>" +
                "  <Penciller>Joe Orlando</Penciller>" +
                "</ComicInfo>";

        File cbz = createCbz("daredevil_1964.cbz", new LinkedHashMap<>() {{
            put("ComicInfo.xml", xml.getBytes(StandardCharsets.UTF_8));
            put("page1.jpg", createTestImage(Color.RED));
        }});

        BookMetadata metadata = extractor.extractMetadata(cbz);
        
        // Verify that the metadata was properly extracted from ComicInfo.xml
        assertEquals("Daredevil #1", metadata.getTitle());
        assertEquals("Daredevil", metadata.getSeriesName());
        assertEquals(1.0f, metadata.getSeriesNumber());
        assertEquals(Integer.valueOf(1964), metadata.getPublishedDate().getYear());
        assertEquals("Marvel", metadata.getPublisher());
        assertTrue(metadata.getAuthors().contains("Stan Lee"));
        assertTrue(metadata.getAuthors().contains("Joe Orlando"));
    }

    @Test
    void testComicInfoExtractionWithDifferentCase() throws Exception {
        String xml = "<ComicInfo>" +
                "  <Title>Daredevil #12</Title>" +
                "  <Series>Daredevil</Series>" +
                "  <Number>12</Number>" +
                "  <Year>1966</Year>" +
                "</ComicInfo>";

        File cbz = createCbz("daredevil_1966.cbz", new LinkedHashMap<>() {{
            put("comicinfo.xml", xml.getBytes(StandardCharsets.UTF_8)); // lowercase
            put("page1.jpg", createTestImage(Color.BLUE));
        }});

        BookMetadata metadata = extractor.extractMetadata(cbz);
        
        assertEquals("Daredevil #12", metadata.getTitle());
        assertEquals("Daredevil", metadata.getSeriesName());
        assertEquals(12.0f, metadata.getSeriesNumber());
    }

    @Test
    void testComicInfoExtractionWithPathInName() throws Exception {
        String xml = "<ComicInfo>" +
                "  <Title>Daredevil #200</Title>" +
                "  <Series>Daredevil</Series>" +
                "  <Number>200</Number>" +
                "  <Year>1985</Year>" +
                "</ComicInfo>";

        File cbz = createCbz("daredevil_1985.cbz", new LinkedHashMap<>() {{
            put("metadata/ComicInfo.xml", xml.getBytes(StandardCharsets.UTF_8)); // in subdirectory
            put("page1.jpg", createTestImage(Color.GREEN));
        }});

        BookMetadata metadata = extractor.extractMetadata(cbz);
        
        assertEquals("Daredevil #200", metadata.getTitle());
        assertEquals("Daredevil", metadata.getSeriesName());
        assertEquals(200.0f, metadata.getSeriesNumber());
    }

    @Test
    void testComicInfoWithExtendedFields() throws Exception {
        String xml = "<ComicInfo>" +
                "  <Title>Daredevil #500</Title>" +
                "  <Series>Daredevil</Series>" +
                "  <Number>500</Number>" +
                "  <Count>800</Count>" +
                "  <Year>2004</Year>" +
                "  <Month>10</Month>" +
                "  <Day>15</Day>" +
                "  <Publisher>Marvel Comics</Publisher>" +
                "  <Genre>Superhero</Genre>" +
                "  <Tags>Marvel;Daredevil;Superhero;Frank Miller</Tags>" +
                "  <Summary>Special anniversary issue</Summary>" +
                "  <PageCount>32</PageCount>" +
                "  <LanguageISO>en</LanguageISO>" +
                "  <Writer>Frank Miller</Writer>" +
                "  <Penciller>John Romita Jr.</Penciller>" +
                "  <Inker>Scott Hanna</Inker>" +
                "  <Colorist>Steve Oliff</Colorist>" +
                "  <Letterer>Joe Rosen</Letterer>" +
                "  <CoverArtist>Frank Miller</CoverArtist>" +
                "</ComicInfo>";

        File cbz = createCbz("daredevil_anniversary.cbz", new LinkedHashMap<>() {{
            put("ComicInfo.xml", xml.getBytes(StandardCharsets.UTF_8));
            put("page1.jpg", createTestImage(Color.MAGENTA));
        }});

        BookMetadata metadata = extractor.extractMetadata(cbz);
        
        assertEquals("Daredevil #500", metadata.getTitle());
        assertEquals("Daredevil", metadata.getSeriesName());
        assertEquals(500.0f, metadata.getSeriesNumber());
        assertEquals(Integer.valueOf(800), metadata.getSeriesTotal());
        assertEquals(LocalDate.of(2004, 10, 15), metadata.getPublishedDate());
        assertEquals("Marvel Comics", metadata.getPublisher());
        assertEquals("en", metadata.getLanguage());
        assertEquals(Integer.valueOf(32), metadata.getPageCount());
        assertEquals("Special anniversary issue", metadata.getDescription());
        
        assertTrue(metadata.getAuthors().contains("Frank Miller"));
        assertTrue(metadata.getAuthors().contains("John Romita Jr."));
        assertTrue(metadata.getAuthors().contains("Scott Hanna"));
        assertTrue(metadata.getAuthors().contains("Steve Oliff"));
        assertTrue(metadata.getAuthors().contains("Joe Rosen"));
        
        assertTrue(metadata.getCategories().contains("Marvel"));
        assertTrue(metadata.getCategories().contains("Daredevil"));
        assertTrue(metadata.getCategories().contains("Superhero"));
        assertTrue(metadata.getCategories().contains("Frank Miller"));
    }

    @Test
    void testComicInfoWithSpecialCharacters() throws Exception {
        String xml = "<ComicInfo>" +
                "  <Title>Daredevil: The Man Without Fear #1</Title>" +
                "  <Series>Daredevil: The Man Without Fear</Series>" +
                "  <Number>1</Number>" +
                "  <Year>1993</Year>" +
                "  <Summary>Daredevil's origin story reimagined</Summary>" +
                "</ComicInfo>";

        File cbz = createCbz("daredevil_origin.cbz", new LinkedHashMap<>() {{
            put("ComicInfo.xml", xml.getBytes(StandardCharsets.UTF_8));
            put("page1.jpg", createTestImage(Color.ORANGE));
        }});

        BookMetadata metadata = extractor.extractMetadata(cbz);
        
        assertEquals("Daredevil: The Man Without Fear #1", metadata.getTitle());
        assertEquals("Daredevil: The Man Without Fear", metadata.getSeriesName());
        assertEquals(1.0f, metadata.getSeriesNumber());
        assertEquals("Daredevil's origin story reimagined", metadata.getDescription());
    }


    private File createCbz(String name, Map<String, byte[]> entries) throws IOException {
        Path out = tempDir.resolve(name);
        try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(out.toFile()))) {
            for (Map.Entry<String, byte[]> e : entries.entrySet()) {
                String entryName = e.getKey();
                byte[] data = e.getValue();
                ZipEntry ze = new ZipEntry(entryName);
                ze.setTime(0L);
                zos.putNextEntry(ze);
                try (InputStream is = new ByteArrayInputStream(data)) {
                    is.transferTo(zos);
                }
                zos.closeEntry();
            }
        }
        return out.toFile();
    }

    private byte[] createTestImage(Color color) throws IOException {
        BufferedImage image = new BufferedImage(10, 10, BufferedImage.TYPE_INT_RGB);
        for (int x = 0; x < 10; x++) {
            for (int y = 0; y < 10; y++) {
                image.setRGB(x, y, color.getRGB());
            }
        }
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            ImageIO.write(image, "jpg", baos);
            return baos.toByteArray();
        }
    }
}