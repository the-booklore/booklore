package com.adityachandel.booklore.service.file;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class FileFingerprint {

    public static String generateHash(Path filePath) {
        final long base = 1024L;
        final int blockSize = 1024;

        try (RandomAccessFile raf = new RandomAccessFile(filePath.toFile(), "r")) {
            MessageDigest md5 = MessageDigest.getInstance("MD5");
            byte[] buffer = new byte[blockSize];

            for (int i = -1; i <= 10; i++) {
                long position = base << (2 * i);
                if (position >= raf.length()) break;

                raf.seek(position);
                int read = raf.read(buffer);
                if (read > 0) {
                    md5.update(buffer, 0, read);
                }
            }

            byte[] hash = md5.digest();
            StringBuilder result = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                result.append(String.format("%02x", b));
            }
            return result.toString();

        } catch (IOException | NoSuchAlgorithmException e) {
            throw new RuntimeException("Failed to compute partial MD5 hash for: " + filePath, e);
        }
    }
}
