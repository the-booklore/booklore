package com.adityachandel.booklore.util;

import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

@Slf4j
@UtilityClass
public class ArchiveUtils {

    public enum ArchiveType {
        ZIP,
        RAR,
        SEVEN_ZIP,
        UNKNOWN
    }

    private static final byte[] ZIP_MAGIC = {0x50, 0x4B, 0x03, 0x04};
    private static final byte[] RAR_MAGIC = {0x52, 0x61, 0x72, 0x21, 0x1A, 0x07};
    private static final byte[] SEVEN_ZIP_MAGIC = {0x37, 0x7A, (byte) 0xBC, (byte) 0xAF, 0x27, 0x1C};

    public static ArchiveType detectArchiveType(File file) {
        if (file == null || !file.exists() || !file.isFile()) {
            return ArchiveType.UNKNOWN;
        }

        try (InputStream is = new BufferedInputStream(new FileInputStream(file))) {
            byte[] buffer = new byte[8];
            int bytesRead = is.read(buffer);
            if (bytesRead < 4) {
                return detectArchiveTypeByExtension(file.getName());
            }

            if (startsWith(buffer, ZIP_MAGIC)) {
                return ArchiveType.ZIP;
            }
            if (startsWith(buffer, RAR_MAGIC)) {
                return ArchiveType.RAR;
            }
            if (startsWith(buffer, SEVEN_ZIP_MAGIC)) {
                return ArchiveType.SEVEN_ZIP;
            }
        } catch (IOException e) {
            log.warn("Failed to detect archive type by content for file: {}", file.getAbsolutePath());
        }

        return detectArchiveTypeByExtension(file.getName());
    }

    public static ArchiveType detectArchiveTypeByExtension(String fileName) {
        if (fileName == null) {
            return ArchiveType.UNKNOWN;
        }
        String lower = fileName.toLowerCase();
        if (lower.endsWith(".cbz") || lower.endsWith(".zip")) {
            return ArchiveType.ZIP;
        }
        if (lower.endsWith(".cbr") || lower.endsWith(".rar")) {
            return ArchiveType.RAR;
        }
        if (lower.endsWith(".cb7") || lower.endsWith(".7z")) {
            return ArchiveType.SEVEN_ZIP;
        }
        return ArchiveType.UNKNOWN;
    }

    private static boolean startsWith(byte[] buffer, byte[] magic) {
        if (buffer.length < magic.length) {
            return false;
        }
        for (int i = 0; i < magic.length; i++) {
            if (buffer[i] != magic[i]) {
                return false;
            }
        }
        return true;
    }
}
