package com.adityachandel.booklore.service.metadata.writer;

import com.adityachandel.booklore.model.MetadataClearFlags;
import com.adityachandel.booklore.model.entity.AuthorEntity;
import com.adityachandel.booklore.model.entity.BookEntity;
import com.adityachandel.booklore.model.entity.BookMetadataEntity;
import com.adityachandel.booklore.model.entity.LibraryPathEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.zip.ZipFile;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.*;

class EpubMetadataWriterTest {

    private EpubMetadataWriter writer;
    private BookMetadataEntity metadata;
    private BookEntity bookEntity;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        writer = new EpubMetadataWriter();
        metadata = new BookMetadataEntity();
        metadata.setTitle("Test Book");
        AuthorEntity author = new AuthorEntity();
        author.setName("Test Author");
        metadata.setAuthors(Collections.singleton(author));

        bookEntity = new BookEntity();
        LibraryPathEntity libraryPath = new LibraryPathEntity();
        libraryPath.setPath(tempDir.toString());
        bookEntity.setLibraryPath(libraryPath);
        bookEntity.setFileSubPath("");
        bookEntity.setFileName("test.epub");
    }

    @Nested
    @DisplayName("Metadata writing Tests")
    class MetadataWritingTests {
        @Test
        @DisplayName("Should only overwrite authors of EPUB metadata")
        void writeMetadata_withAuthor_onlyAuthor() throws IOException {
            StringBuilder existingMetadata = new StringBuilder();
            existingMetadata.append("<metadata xmlns:dc=\"http://purl.org/dc/elements/1.1/\">");
            existingMetadata.append("<dc:creator id=\"creator02\">Alice</dc:creator>");
            existingMetadata.append("<meta property=\"role\" refines=\"#creator02\">ill</meta>");
            existingMetadata.append("</metadata>");
            String opfContent = String.format("""
                <?xml version="1.0" encoding="UTF-8"?>
                <package xmlns="http://www.idpf.org/2007/opf" version="3.0">
                    %s
                </package>
                """, existingMetadata);
            File epubFile = createEpubWithOpf(opfContent, "test-metadata-" + System.nanoTime() + ".epub");

            assertDoesNotThrow(() -> writer.writeMetadataToFile(epubFile, metadata, null, new MetadataClearFlags()));

            assertTrue(epubFile.exists());
            assertTrue(epubFile.length() > 0);
            try (ZipFile zf = new ZipFile(epubFile)) {
                ZipEntry ze = zf.getEntry("OEBPS/content.opf");
                try (InputStream is = zf.getInputStream(ze)) {
                    byte[] fileBytes = is.readAllBytes();
                    String fileString = new String(fileBytes);
                    assertTrue(fileString.contains("id=\"creator02\""));
                }
            }
        }
    }

    @Nested
    @DisplayName("URL Decoding Tests")
    class UrlDecodingTests {

        @Test
        @DisplayName("Should properly handle URL-encoded href values in manifest")
        void writeMetadataToFile_withUnicodeHref_handlesDecoding() throws IOException {
            byte[] epubContent = createEpubWithUnicodeCoverHref();
            File epubFile = tempDir.resolve("test_unicode.epub").toFile();
            Files.write(epubFile.toPath(), epubContent);

            assertDoesNotThrow(() -> writer.writeMetadataToFile(epubFile, metadata, null, new MetadataClearFlags()));

            assertTrue(epubFile.exists());
            assertTrue(epubFile.length() > 0);
        }

        @Test
        @DisplayName("Should handle URL-encoded cover href during cover replacement")
        void replaceCoverImageFromUpload_withUnicodeHref_handlesDecoding() throws IOException {
            byte[] epubContent = createEpubWithUnicodeCoverHref();
            File epubFile = tempDir.resolve("test_cover_unicode.epub").toFile();
            Files.write(epubFile.toPath(), epubContent);

            byte[] imageBytes = createMinimalPngImage();
            MultipartFile coverFile = new MockMultipartFile(
                "cover.png",
                "cover.png",
                "image/png",
                imageBytes
            );

            assertDoesNotThrow(() -> {
                writer.replaceCoverImageFromUpload(bookEntity, coverFile);
            });
        }
    }

    private File createEpubWithOpf(String opfContent, String filename) throws IOException {
        File epubFile = tempDir.resolve(filename).toFile();

        String containerXml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container">
                <rootfiles>
                    <rootfile full-path="OEBPS/content.opf" media-type="application/oebps-package+xml"/>
                </rootfiles>
            </container>
            """;

        try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(epubFile))) {
            zos.putNextEntry(new ZipEntry("mimetype"));
            zos.write("application/epub+zip".getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();

            zos.putNextEntry(new ZipEntry("META-INF/container.xml"));
            zos.write(containerXml.getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();

            zos.putNextEntry(new ZipEntry("OEBPS/content.opf"));
            zos.write(opfContent.getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();
        }

        return epubFile;
    }

    private byte[] createEpubWithUnicodeCoverHref() throws IOException {
        String opfContent = """
            <?xml version="1.0" encoding="UTF-8"?>
            <package xmlns="http://www.idpf.org/2007/opf" version="3.0">
                <metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
                    <dc:title>Test Book with Unicode Cover</dc:title>
                    <dc:creator>Test Author</dc:creator>
                    <meta name="cover" content="cover-image"/>
                </metadata>
                <manifest>
                    <item id="ncx" href="toc.ncx" media-type="application/x-dtbncx+xml"/>
                    <item id="cover-image" href="cover%C3%A1.png" media-type="image/png" properties="cover-image"/>
                    <item id="text" href="index.html" media-type="application/xhtml+xml"/>
                </manifest>
                <spine toc="ncx">
                    <itemref idref="text"/>
                </spine>
            </package>
            """;

        String containerXml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container">
                <rootfiles>
                    <rootfile full-path="OEBPS/content.opf" media-type="application/oebps-package+xml"/>
                </rootfiles>
            </container>
            """;

        String htmlContent = """
            <?xml version="1.0" encoding="UTF-8"?>
            <!DOCTYPE html>
            <html xmlns="http://www.w3.org/1999/xhtml">
            <head>
                <title>Test</title>
            </head>
            <body>
                <h1>Test Content</h1>
            </body>
            </html>
            """;

        String ncxContent = """
            <?xml version="1.0" encoding="UTF-8"?>
            <!DOCTYPE ncx PUBLIC "-//NISO//DTD ncx 2005-1//EN"
                "http://www.daisy.org/z3986/2005/ncx-2005-1.dtd">
            <ncx version="2005-1" xml:lang="en">
                <head>
                    <meta name="dtb:uid" content="test-book"/>
                </head>
                <docTitle>
                    <text>Test Book</text>
                </docTitle>
                <navMap>
                    <navPoint id="navpoint-1" playOrder="1">
                        <navLabel>
                            <text>Test</text>
                        </navLabel>
                        <content src="index.html"/>
                    </navPoint>
                </navMap>
            </ncx>
            """;

        byte[] coverImage = createMinimalPngImage();

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos)) {

            zos.putNextEntry(new ZipEntry("mimetype"));
            zos.write("application/epub+zip".getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();

            zos.putNextEntry(new ZipEntry("META-INF/container.xml"));
            zos.write(containerXml.getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();

            zos.putNextEntry(new ZipEntry("OEBPS/content.opf"));
            zos.write(opfContent.getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();

            zos.putNextEntry(new ZipEntry("OEBPS/index.html"));
            zos.write(htmlContent.getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();

            zos.putNextEntry(new ZipEntry("OEBPS/toc.ncx"));
            zos.write(ncxContent.getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();

            String decodedCoverPath = java.net.URLDecoder.decode("cover%C3%A1.png", java.nio.charset.StandardCharsets.UTF_8);
            zos.putNextEntry(new ZipEntry("OEBPS/" + decodedCoverPath));
            zos.write(coverImage);
            zos.closeEntry();
        }

        return baos.toByteArray();
    }

    private byte[] createMinimalPngImage() {
        return new byte[]{
            (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A,
            0x00, 0x00, 0x00, 0x0D,
            0x49, 0x48, 0x44, 0x52,
            0x00, 0x00, 0x00, 0x01,
            0x00, 0x00, 0x00, 0x01,
            0x08, 0x06,
            0x00, 0x00, 0x00,
            (byte) 0x90, (byte) 0x77, (byte) 0x53, (byte) 0xDE,
            0x00, 0x00, 0x00, 0x0A,
            0x49, 0x44, 0x41, 0x54,
            0x78, (byte) 0x9C, 0x63, 0x00, 0x01, 0x00, 0x00, 0x05,
            0x00, 0x01,
            0x0D, (byte) 0x0A, 0x2D, (byte) 0xB4,
            0x00, 0x00, 0x00, 0x00,
            0x49, 0x45, 0x4E, 0x44,
            (byte) 0xAE, 0x42, 0x60, (byte) 0x82
        };
    }
}