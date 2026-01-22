package com.adityachandel.booklore.service.metadata.extractor;

import com.adityachandel.booklore.model.dto.BookMetadata;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.*;

class CbxMetadataExtractorTest {

    private CbxMetadataExtractor extractor;
    private Path tempDir;

    @BeforeEach
    void setUp() throws IOException {
        extractor = new CbxMetadataExtractor();
        tempDir = Files.createTempDirectory("cbx_test_");
    }

    @AfterEach
    void tearDown() throws IOException {
        if (tempDir != null) {
            // best-effort cleanup
            Files.walk(tempDir)
                    .sorted(Comparator.reverseOrder())
                    .forEach(p -> { try { Files.deleteIfExists(p); } catch (Exception ignore) {} });
        }
    }

    @Test
    void extractMetadata_fromCbz_withComicInfo_populatesFields_withoutVolume() throws Exception {
        String xml = "<ComicInfo>" +
                "  <Title>My Comic</Title>" +
                "  <Summary>A short summary</Summary>" +
                "  <Publisher>Indie</Publisher>" +
                "  <Series>Series X</Series>" +
                "  <Number>2.5</Number>" +
                "  <Count>12</Count>" +
                "  <Year>2020</Year><Month>7</Month><Day>14</Day>" +
                "  <PageCount>42</PageCount>" +
                "  <LanguageISO>en</LanguageISO>" +
                "  <Writer>Alice</Writer>" +
                "  <Penciller>Bob</Penciller>" +
                "  <Tags>action;adventure</Tags>" +
                "</ComicInfo>";

        File cbz = createCbz("with_meta.cbz", new LinkedHashMap<>() {{
            put("ComicInfo.xml", xml.getBytes(StandardCharsets.UTF_8));
            put("page1.jpg", new byte[]{1,2,3});
        }});

        BookMetadata md = extractor.extractMetadata(cbz);
        assertEquals("My Comic", md.getTitle());
        assertEquals("A short summary", md.getDescription());
        assertEquals("Indie", md.getPublisher());
        assertEquals("Series X", md.getSeriesName());
        assertEquals(2.5f, md.getSeriesNumber());
        assertEquals(Integer.valueOf(12), md.getSeriesTotal());
        assertEquals(LocalDate.of(2020,7,14), md.getPublishedDate());
        assertEquals(Integer.valueOf(42), md.getPageCount());
        assertEquals("en", md.getLanguage());
        assertTrue(md.getAuthors().contains("Alice"));
        assertTrue(md.getCategories().contains("action"));
        assertTrue(md.getCategories().contains("adventure"));
    }

    @Test
    void extractMetadata_fromCbz_withComicInfo_populatesFields_withVolume() throws Exception {
        String xml = "<ComicInfo>" +
                "  <Title>My Comic</Title>" +
                "  <Summary>A short summary</Summary>" +
                "  <Publisher>Indie</Publisher>" +
                "  <Series>Series X</Series>" +
                "  <Volume>1</Volume>" +
                "  <Number>2.5</Number>" +
                "  <Count>12</Count>" +
                "  <Year>2020</Year><Month>7</Month><Day>14</Day>" +
                "  <PageCount>42</PageCount>" +
                "  <LanguageISO>en</LanguageISO>" +
                "  <Writer>Alice</Writer>" +
                "  <Penciller>Bob</Penciller>" +
                "  <Tags>action;adventure</Tags>" +
                "</ComicInfo>";

        File cbz = createCbz("with_meta.cbz", new LinkedHashMap<>() {{
            put("ComicInfo.xml", xml.getBytes(StandardCharsets.UTF_8));
            put("page1.jpg", new byte[]{1,2,3});
        }});

        BookMetadata md = extractor.extractMetadata(cbz);
        assertEquals("My Comic", md.getTitle());
        assertEquals("A short summary", md.getDescription());
        assertEquals("Indie", md.getPublisher());
        assertEquals("Series X", md.getSeriesName());
        assertEquals(2.5f, md.getSeriesNumber());
        assertEquals(Integer.valueOf(12), md.getSeriesTotal());
        assertEquals(LocalDate.of(2020,7,14), md.getPublishedDate());
        assertEquals(Integer.valueOf(42), md.getPageCount());
        assertEquals("en", md.getLanguage());
        assertTrue(md.getAuthors().contains("Alice"));
        assertTrue(md.getCategories().contains("action"));
        assertTrue(md.getCategories().contains("adventure"));
    }

    @Test
    void extractCover_fromCbz_usesComicInfoImageFile() throws Exception {
        String xml = "<ComicInfo>" +
                "  <Pages>" +
                "    <Page Type=\"FrontCover\" ImageFile=\"images/002.jpg\"/>" +
                "  </Pages>" +
                "</ComicInfo>";

        byte[] img1 = createTestImage(Color.RED);
        byte[] img2 = createTestImage(Color.GREEN); // expect this one
        byte[] img3 = createTestImage(Color.BLUE);

        File cbz = createCbz("with_cover.cbz", new LinkedHashMap<>() {{
            put("ComicInfo.xml", xml.getBytes(StandardCharsets.UTF_8));
            put("images/001.jpg", img1);
            put("images/002.jpg", img2);
            put("images/003.jpg", img3);
        }});

        byte[] cover = extractor.extractCover(cbz);
        assertArrayEquals(img2, cover);
    }

    @Test
    void extractCover_fromCbz_fallbackAlphabeticalFirst() throws Exception {
        // No ComicInfo.xml, images intentionally added in unsorted order
        byte[] aPng = createTestImage(Color.YELLOW); // alphabetically first (A.png)
        byte[] bJpg = createTestImage(Color.MAGENTA);
        byte[] cJpeg = createTestImage(Color.CYAN);

        File cbz = createCbz("fallback.cbz", new LinkedHashMap<>() {{
            put("z/pageC.jpeg", cJpeg);
            put("A.png", aPng);           // should be chosen
            put("b.jpg", bJpg);
        }});

        byte[] cover = extractor.extractCover(cbz);
        assertArrayEquals(aPng, cover);
    }

    @Test
    void extractMetadata_nonArchive_fallbackTitle() throws Exception {
        Path txt = tempDir.resolve("Some Book Title.txt");
        Files.writeString(txt, "hello");
        BookMetadata md = extractor.extractMetadata(txt.toFile());
        assertEquals("Some Book Title", md.getTitle());
    }

    @Test
    void extractMetadata_fromCbz_withCbrExtension_shouldWork() throws Exception {
        String xml = "<ComicInfo><Title>Mismatched Extension</Title></ComicInfo>";

        File cbzAsCbr = createCbz("mismatched.cbr", new LinkedHashMap<>() {{
            put("ComicInfo.xml", xml.getBytes(StandardCharsets.UTF_8));
        }});

        BookMetadata md = extractor.extractMetadata(cbzAsCbr);
        assertEquals("Mismatched Extension", md.getTitle());
    }

    @Test
    void extractCover_fromCbz_withCbrExtension_shouldWork() throws Exception {
        byte[] img = createTestImage(Color.RED);

        File cbzAsCbr = createCbz("mismatched_cover.cbr", new LinkedHashMap<>() {{
            put("cover.jpg", img);
        }});

        byte[] cover = extractor.extractCover(cbzAsCbr);
        assertArrayEquals(img, cover);
    }

    @Test
    void extractMetadata_fromCbz_withNamespacedXml_shouldParseCorrectly() throws Exception {
        String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>" +
                "<ComicInfo xmlns:xsd=\"http://www.w3.org/2001/XMLSchema\" " +
                "xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" " +
                "xsi:schemaLocation=\"https://anansi-project.github.io/docs/comicinfo/schemas/v2.1\">" +
                "<Title>Vol. 1</Title>" +
                "<Series>Helck</Series>" +
                "<Number>1</Number>" +
                "<Count>12</Count>" +
                "<Volume>1</Volume>" +
                "<Year>2023</Year><Month>1</Month><Day>10</Day>" +
                "<Writer>Nanaki Nanao</Writer>" +
                "<Publisher>Viz</Publisher>" +
                "</ComicInfo>";

        File cbz = createCbz("helck_v1.cbz", new LinkedHashMap<>() {{
            put("ComicInfo.xml", xml.getBytes(StandardCharsets.UTF_8));
            put("page1.jpg", new byte[]{1, 2, 3});
        }});

        BookMetadata md = extractor.extractMetadata(cbz);
        assertEquals("Vol. 1", md.getTitle());
        assertEquals("Helck", md.getSeriesName());
        assertEquals(1f, md.getSeriesNumber());
        assertEquals(Integer.valueOf(12), md.getSeriesTotal());
        assertEquals("Viz", md.getPublisher());
        assertTrue(md.getAuthors().contains("Nanaki Nanao"));
    }

    @Test
    void extractMetadata_multipleVolumes_shouldHaveSameSeriesName() throws Exception {
        String xmlVol1 = "<ComicInfo>" +
                "<Title>Vol. 1</Title>" +
                "<Series>Helck</Series>" +
                "<Number>1</Number>" +
                "<Volume>1</Volume>" +
                "</ComicInfo>";

        String xmlVol2 = "<ComicInfo>" +
                "<Title>Vol. 2</Title>" +
                "<Series>Helck</Series>" +
                "<Number>2</Number>" +
                "<Volume>2</Volume>" +
                "</ComicInfo>";

        String xmlVol3 = "<ComicInfo>" +
                "<Title>Vol. 3</Title>" +
                "<Series>Helck</Series>" +
                "<Number>3</Number>" +
                "<Volume>3</Volume>" +
                "</ComicInfo>";

        File cbz1 = createCbz("helck_vol1.cbz", new LinkedHashMap<>() {{
            put("ComicInfo.xml", xmlVol1.getBytes(StandardCharsets.UTF_8));
        }});
        File cbz2 = createCbz("helck_vol2.cbz", new LinkedHashMap<>() {{
            put("ComicInfo.xml", xmlVol2.getBytes(StandardCharsets.UTF_8));
        }});
        File cbz3 = createCbz("helck_vol3.cbz", new LinkedHashMap<>() {{
            put("ComicInfo.xml", xmlVol3.getBytes(StandardCharsets.UTF_8));
        }});

        BookMetadata md1 = extractor.extractMetadata(cbz1);
        BookMetadata md2 = extractor.extractMetadata(cbz2);
        BookMetadata md3 = extractor.extractMetadata(cbz3);

        assertEquals("Helck", md1.getSeriesName());
        assertEquals("Helck", md2.getSeriesName());
        assertEquals("Helck", md3.getSeriesName());
        assertEquals(md1.getSeriesName(), md2.getSeriesName());
        assertEquals(md2.getSeriesName(), md3.getSeriesName());
    }

    @Test
    void extractMetadata_withVolumeField_shouldNotAppendVolumeToSeriesName() throws Exception {
        String xml = "<ComicInfo>" +
                "<Title>My Comic</Title>" +
                "<Series>Series Name</Series>" +
                "<Volume>5</Volume>" +
                "<Number>10</Number>" +
                "</ComicInfo>";

        File cbz = createCbz("comic.cbz", new LinkedHashMap<>() {{
            put("ComicInfo.xml", xml.getBytes(StandardCharsets.UTF_8));
        }});

        BookMetadata md = extractor.extractMetadata(cbz);
        assertEquals("Series Name", md.getSeriesName());
        assertEquals(10f, md.getSeriesNumber());
    }

    // ---------- helpers ----------

    private File createCbz(String name, Map<String, byte[]> entries) throws IOException {
        Path out = tempDir.resolve(name);
        try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(out.toFile()))) {
            for (Map.Entry<String, byte[]> e : entries.entrySet()) {
                String entryName = e.getKey();
                byte[] data = e.getValue();
                ZipEntry ze = new ZipEntry(entryName);
                // set a fixed time to avoid platform-dependent headers
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