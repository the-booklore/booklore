// package test.java.com.adityachandel.booklore.util;
// import com.adityachandel.booklore.util.FileUtils;

package com.adityachandel.booklore.util;

import org.junit.jupiter.api.Test;
import java.nio.file.Path;
import static org.assertj.core.api.Assertions.assertThat;

class FileUtilsTest {

    @Test
    void getRelativeSubPath_shouldHandleStandardCase() {
        String basePath = "C:\\Library\\Books";
        Path fullFilePath = Path.of("C:\\Library\\Books\\Series A\\book.epub");
        String result = FileUtils.getRelativeSubPath(basePath, fullFilePath);
        assertThat(result).isEqualTo("Series A");
    }

    @Test
    void getRelativeSubPath_shouldHandleNestedFolders() {
        String basePath = "C:\\Library\\Books";
        Path fullFilePath = Path.of("C:\\Library\\Books\\Series A\\Subfolder 1\\book.epub");
        String result = FileUtils.getRelativeSubPath(basePath, fullFilePath);
        assertThat(result).isEqualTo("Series A/Subfolder 1");
    }

    @Test
    void getRelativeSubPath_shouldHandleFileInRoot() {
        String basePath = "C:\\Library\\Books";
        Path fullFilePath = Path.of("C:\\Library\\Books\\book.epub");
        String result = FileUtils.getRelativeSubPath(basePath, fullFilePath);
        assertThat(result).isEqualTo("");
    }

    @Test
    void getRelativeSubPath_shouldHandleTrailingSlashOnBase() {
        // This was a likely cause of the bug
        String basePath = "C:\\Library\\Books\\"; 
        Path fullFilePath = Path.of("C:\\Library\\Books\\Series A\\book.epub");
        String result = FileUtils.getRelativeSubPath(basePath, fullFilePath);
        assertThat(result).isEqualTo("Series A");
    }

    @Test
    void getRelativeSubPath_shouldHandleLinuxPaths() {
        String basePath = "/home/user/books";
        Path fullFilePath = Path.of("/home/user/books/Series A/book.epub");
        String result = FileUtils.getRelativeSubPath(basePath, fullFilePath);
        assertThat(result).isEqualTo("Series A");
    }

    @Test
    void getRelativeSubPath_shouldHandleNonNormalizedPaths() {
        String basePath = "C:\\Library\\.\\Books";
        Path fullFilePath = Path.of("C:\\Library\\Books\\Series A\\..\\Series A\\book.epub");
        String result = FileUtils.getRelativeSubPath(basePath, fullFilePath);
        // The normalization should fix both paths and find the correct relative path
        assertThat(result).isEqualTo("Series A");
    }
}