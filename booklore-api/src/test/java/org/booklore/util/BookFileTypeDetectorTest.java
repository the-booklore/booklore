package com.adityachandel.booklore.util;

import com.adityachandel.booklore.model.enums.BookFileExtension;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Optional;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.*;

class BookFileTypeDetectorTest {

    @TempDir
    Path tempDir;

    @Test
    void detectType_epubWithNoExtension_detectsAsEpub() throws IOException {
        File file = createValidEpub(tempDir.resolve(".epub").toFile());
        Optional<BookFileExtension> result = BookFileTypeDetector.detectType(file);
        assertTrue(result.isPresent());
        assertEquals(BookFileExtension.EPUB, result.get());
    }

    @Test
    void detectType_epubWithWrongExtension_detectsAsEpub() throws IOException {
        File file = createValidEpub(tempDir.resolve("book.cbz").toFile());
        Optional<BookFileExtension> result = BookFileTypeDetector.detectType(file);
        assertTrue(result.isPresent());
        assertEquals(BookFileExtension.EPUB, result.get());
    }

    @Test
    void detectType_cbzWithNoExtension_detectsAsCbz() throws IOException {
        File file = createCbzArchive(tempDir.resolve(".cbz").toFile());
        Optional<BookFileExtension> result = BookFileTypeDetector.detectType(file);
        assertTrue(result.isPresent());
        assertEquals(BookFileExtension.CBZ, result.get());
    }

    @Test
    void detectType_cbzWithEpubExtension_detectsAsCbz() throws IOException {
        File file = createCbzArchive(tempDir.resolve("comic.epub").toFile());
        Optional<BookFileExtension> result = BookFileTypeDetector.detectType(file);
        assertTrue(result.isPresent());
        assertEquals(BookFileExtension.CBZ, result.get());
    }

    @Test
    void detectType_pdfWithNoExtension_detectsAsPdf() throws IOException {
        File file = tempDir.resolve(".pdf").toFile();
        try (FileOutputStream fos = new FileOutputStream(file)) {
            fos.write("%PDF-1.4\n".getBytes(StandardCharsets.US_ASCII));
        }
        Optional<BookFileExtension> result = BookFileTypeDetector.detectType(file);
        assertTrue(result.isPresent());
        assertEquals(BookFileExtension.PDF, result.get());
    }

    @Test
    void detectType_pdfWithWrongExtension_detectsAsPdf() throws IOException {
        File file = tempDir.resolve("document.epub").toFile();
        try (FileOutputStream fos = new FileOutputStream(file)) {
            fos.write("%PDF-1.7\n".getBytes(StandardCharsets.US_ASCII));
        }
        Optional<BookFileExtension> result = BookFileTypeDetector.detectType(file);
        assertTrue(result.isPresent());
        assertEquals(BookFileExtension.PDF, result.get());
    }

    @Test
    void detectType_rarArchive_detectsAsCbr() throws IOException {
        File file = tempDir.resolve("comic.rar").toFile();
        try (FileOutputStream fos = new FileOutputStream(file)) {
            fos.write(new byte[]{0x52, 0x61, 0x72, 0x21, 0x1A, 0x07, 0x00});
        }
        Optional<BookFileExtension> result = BookFileTypeDetector.detectType(file);
        assertTrue(result.isPresent());
        assertEquals(BookFileExtension.CBR, result.get());
    }

    @Test
    void detectType_sevenZipArchive_detectsAsCb7() throws IOException {
        File file = tempDir.resolve("comic.7z").toFile();
        try (FileOutputStream fos = new FileOutputStream(file)) {
            fos.write(new byte[]{0x37, 0x7A, (byte)0xBC, (byte)0xAF, 0x27, 0x1C});
        }
        Optional<BookFileExtension> result = BookFileTypeDetector.detectType(file);
        assertTrue(result.isPresent());
        assertEquals(BookFileExtension.CB7, result.get());
    }

    @Test
    void detectType_mobiFile_detectsAsMobi() throws IOException {
        File file = tempDir.resolve("book.mobi").toFile();
        byte[] mobiHeader = new byte[68];
        System.arraycopy("BOOKMOBI".getBytes(StandardCharsets.US_ASCII), 0, mobiHeader, 60, 8);
        try (FileOutputStream fos = new FileOutputStream(file)) {
            fos.write(mobiHeader);
        }
        Optional<BookFileExtension> result = BookFileTypeDetector.detectType(file);
        assertTrue(result.isPresent());
        assertEquals(BookFileExtension.MOBI, result.get());
    }

    @Test
    void detectType_fb2XmlFile_detectsAsFb2() throws IOException {
        File file = tempDir.resolve("book.fb2").toFile();
        String fb2Content = "<?xml version=\"1.0\"?><FictionBook xmlns=\"http://www.gribuser.ru/xml/fictionbook/2.0\"></FictionBook>";
        try (FileOutputStream fos = new FileOutputStream(file)) {
            fos.write(fb2Content.getBytes(StandardCharsets.UTF_8));
        }
        Optional<BookFileExtension> result = BookFileTypeDetector.detectType(file);
        assertTrue(result.isPresent());
        assertEquals(BookFileExtension.FB2, result.get());
    }

    @Test
    void detectType_unknownFormat_returnsEmpty() throws IOException {
        File file = tempDir.resolve("unknown.xyz").toFile();
        try (FileOutputStream fos = new FileOutputStream(file)) {
            fos.write("random content".getBytes(StandardCharsets.UTF_8));
        }
        Optional<BookFileExtension> result = BookFileTypeDetector.detectType(file);
        assertTrue(result.isEmpty());
    }

    @Test
    void detectType_emptyFile_returnsEmpty() throws IOException {
        File file = tempDir.resolve("empty.epub").toFile();
        file.createNewFile();
        Optional<BookFileExtension> result = BookFileTypeDetector.detectType(file);
        assertTrue(result.isEmpty());
    }

    @Test
    void detectType_nonExistentFile_returnsEmpty() {
        File file = tempDir.resolve("nonexistent.epub").toFile();
        Optional<BookFileExtension> result = BookFileTypeDetector.detectType(file);
        assertTrue(result.isEmpty());
    }

    @Test
    void detectType_epubVsCbzDifferentiation_worksCorrectly() throws IOException {
        File epub = createValidEpub(tempDir.resolve("book1.zip").toFile());
        File cbz = createCbzArchive(tempDir.resolve("book2.zip").toFile());

        Optional<BookFileExtension> epubResult = BookFileTypeDetector.detectType(epub);
        Optional<BookFileExtension> cbzResult = BookFileTypeDetector.detectType(cbz);

        assertTrue(epubResult.isPresent());
        assertTrue(cbzResult.isPresent());
        assertEquals(BookFileExtension.EPUB, epubResult.get());
        assertEquals(BookFileExtension.CBZ, cbzResult.get());
    }

    private File createValidEpub(File file) throws IOException {
        try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(file))) {
            ZipEntry mimetypeEntry = new ZipEntry("mimetype");
            mimetypeEntry.setMethod(ZipEntry.STORED);
            byte[] mimetypeContent = "application/epub+zip".getBytes(StandardCharsets.US_ASCII);
            mimetypeEntry.setSize(mimetypeContent.length);
            mimetypeEntry.setCompressedSize(mimetypeContent.length);
            java.util.zip.CRC32 crc = new java.util.zip.CRC32();
            crc.update(mimetypeContent);
            mimetypeEntry.setCrc(crc.getValue());
            zos.putNextEntry(mimetypeEntry);
            zos.write(mimetypeContent);
            zos.closeEntry();

            zos.putNextEntry(new ZipEntry("META-INF/container.xml"));
            zos.write("<container></container>".getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();
        }
        return file;
    }

    private File createCbzArchive(File file) throws IOException {
        try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(file))) {
            zos.putNextEntry(new ZipEntry("page001.jpg"));
            zos.write(new byte[]{(byte)0xFF, (byte)0xD8, (byte)0xFF, (byte)0xE0});
            zos.closeEntry();
            zos.putNextEntry(new ZipEntry("page002.jpg"));
            zos.write(new byte[]{(byte)0xFF, (byte)0xD8, (byte)0xFF, (byte)0xE0});
            zos.closeEntry();
        }
        return file;
    }

    @Test
    void isLikelyBookFile_withValidExtensions_returnsTrue() {
        assertTrue(BookFileTypeDetector.isLikelyBookFile("book.pdf"));
        assertTrue(BookFileTypeDetector.isLikelyBookFile("book.epub"));
        assertTrue(BookFileTypeDetector.isLikelyBookFile("book.mobi"));
        assertTrue(BookFileTypeDetector.isLikelyBookFile("book.cbz"));
        assertTrue(BookFileTypeDetector.isLikelyBookFile("book.cbr"));
        assertTrue(BookFileTypeDetector.isLikelyBookFile("book.cb7"));
        assertTrue(BookFileTypeDetector.isLikelyBookFile("book.fb2"));
    }

    @Test
    void isLikelyBookFile_withInvalidExtensions_returnsFalse() {
        assertFalse(BookFileTypeDetector.isLikelyBookFile("document.txt"));
        assertFalse(BookFileTypeDetector.isLikelyBookFile("image.jpg"));
        assertFalse(BookFileTypeDetector.isLikelyBookFile("video.mp4"));
        assertFalse(BookFileTypeDetector.isLikelyBookFile("noextension"));
    }

    @Test
    void isLikelyBookFile_withPath_usesFileName() {
        assertTrue(BookFileTypeDetector.isLikelyBookFile(Path.of("/some/path/book.epub")));
        assertFalse(BookFileTypeDetector.isLikelyBookFile(Path.of("/some/path/file.txt")));
    }

    @Test
    void isLikelyBookFile_withNullPath_returnsFalse() {
        assertFalse(BookFileTypeDetector.isLikelyBookFile((Path) null));
    }
}
