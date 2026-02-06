package org.booklore.util;

import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import org.booklore.model.enums.BookFileExtension;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Optional;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

@Slf4j
@UtilityClass
public class BookFileTypeDetector {

    private final byte[] PDF_MAGIC = "%PDF".getBytes(StandardCharsets.US_ASCII);
    private final byte[] ZIP_MAGIC = {0x50, 0x4B, 0x03, 0x04};
    private final byte[] RAR_MAGIC = {0x52, 0x61, 0x72, 0x21, 0x1A, 0x07};
    private final byte[] SEVEN_ZIP_MAGIC = {0x37, 0x7A, (byte)0xBC, (byte)0xAF, 0x27, 0x1C};
    private final String EPUB_MIMETYPE = "application/epub+zip";
    private final String BOOKMOBI_MAGIC = "BOOKMOBI";
    private final int MOBI_MAGIC_OFFSET = 60;

    public boolean isLikelyBookFile(Path path) {
        if (path == null) {
            return false;
        }
        return BookFileExtension.fromFileName(path.getFileName().toString()).isPresent();
    }

    public boolean isLikelyBookFile(String fileName) {
        return BookFileExtension.fromFileName(fileName).isPresent();
    }

    public Optional<BookFileExtension> detectType(File file) {
        if (file == null || !file.exists() || !file.isFile() || file.length() == 0) {
            return Optional.empty();
        }

        try {
            byte[] header = readHeader(file, 68);
            if (header.length < 4) {
                return fallbackToExtension(file.getName());
            }

            if (startsWith(header, PDF_MAGIC)) {
                return Optional.of(BookFileExtension.PDF);
            }

            if (startsWith(header, ZIP_MAGIC)) {
                return detectZipBasedFormat(file);
            }

            if (startsWith(header, RAR_MAGIC)) {
                return Optional.of(BookFileExtension.CBR);
            }

            if (startsWith(header, SEVEN_ZIP_MAGIC)) {
                return Optional.of(BookFileExtension.CB7);
            }

            if (header.length >= MOBI_MAGIC_OFFSET + BOOKMOBI_MAGIC.length()) {
                if (matchesAt(header, MOBI_MAGIC_OFFSET, BOOKMOBI_MAGIC.getBytes(StandardCharsets.US_ASCII))) {
                    return Optional.of(BookFileExtension.MOBI);
                }
            }

            String fileName = file.getName().toLowerCase();
            if (fileName.endsWith(".fb2") && isFb2Content(file)) {
                return Optional.of(BookFileExtension.FB2);
            }

            return fallbackToExtension(file.getName());
        } catch (IOException e) {
            log.warn("Failed to detect file type for: {}", file.getAbsolutePath(), e);
            return fallbackToExtension(file.getName());
        }
    }

    public Optional<BookFileExtension> detectType(Path path) {
        if (path == null) {
            return Optional.empty();
        }
        return detectType(path.toFile());
    }

    private Optional<BookFileExtension> detectZipBasedFormat(File file) {
        try (ZipFile zipFile = new ZipFile(file)) {
            ZipEntry mimetypeEntry = zipFile.getEntry("mimetype");
            if (mimetypeEntry != null) {
                try (InputStream is = zipFile.getInputStream(mimetypeEntry)) {
                    byte[] content = is.readNBytes(100);
                    String mimetype = new String(content, StandardCharsets.US_ASCII).trim();
                    if (EPUB_MIMETYPE.equals(mimetype)) {
                        return Optional.of(BookFileExtension.EPUB);
                    }
                }
            }

            if (zipFile.getEntry("META-INF/container.xml") != null) {
                return Optional.of(BookFileExtension.EPUB);
            }

            return Optional.of(BookFileExtension.CBZ);
        } catch (IOException e) {
            log.debug("Failed to read zip contents, treating as CBZ: {}", file.getName());
            return Optional.of(BookFileExtension.CBZ);
        }
    }

    private boolean isFb2Content(File file) {
        try (BufferedReader reader = new BufferedReader(new FileReader(file, StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            int charsRead = 0;
            int ch;
            while ((ch = reader.read()) != -1 && charsRead < 500) {
                sb.append((char) ch);
                charsRead++;
            }
            String content = sb.toString().toLowerCase();
            return content.contains("<fictionbook") || content.contains("fictionbook/2.0");
        } catch (IOException e) {
            return false;
        }
    }

    private byte[] readHeader(File file, int length) throws IOException {
        try (InputStream is = new BufferedInputStream(new FileInputStream(file))) {
            return is.readNBytes(length);
        }
    }

    private boolean startsWith(byte[] data, byte[] prefix) {
        if (data.length < prefix.length) {
            return false;
        }
        for (int i = 0; i < prefix.length; i++) {
            if (data[i] != prefix[i]) {
                return false;
            }
        }
        return true;
    }

    private boolean matchesAt(byte[] data, int offset, byte[] pattern) {
        if (data.length < offset + pattern.length) {
            return false;
        }
        for (int i = 0; i < pattern.length; i++) {
            if (data[offset + i] != pattern[i]) {
                return false;
            }
        }
        return true;
    }

    private Optional<BookFileExtension> fallbackToExtension(String fileName) {
        return BookFileExtension.fromFileName(fileName);
    }
}
