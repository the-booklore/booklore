package com.adityachandel.booklore.util;

import com.adityachandel.booklore.model.entity.BookEntity;
import com.adityachandel.booklore.model.entity.LibraryPathEntity;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class FileUtilsTest {

    @TempDir
    Path tempDir;

    private BookEntity createBookEntity(Path libraryPath, String subPath, String fileName) {
        LibraryPathEntity libraryPathEntity = new LibraryPathEntity();
        libraryPathEntity.setPath(libraryPath.toString());

        BookEntity bookEntity = new BookEntity();
        bookEntity.setLibraryPath(libraryPathEntity);
        bookEntity.setFileSubPath(subPath);
        bookEntity.setFileName(fileName);

        return bookEntity;
    }

    @Test
    void testGetBookFullPath() {
        Path libraryPath = tempDir;
        String subPath = "sub/folder";
        String fileName = "test.pdf";

        BookEntity book = createBookEntity(libraryPath, subPath, fileName);

        String fullPath = FileUtils.getBookFullPath(book);

        String expected = libraryPath.resolve(subPath).resolve(fileName)
                .toString().replace("\\", "/");

        assertEquals(expected, fullPath);
    }

    @Test
    void testGetRelativeSubPath() {
        Path base = tempDir;
        Path nested = base.resolve("a/b/c/file.txt");

        String relative = FileUtils.getRelativeSubPath(base.toString(), nested);

        assertEquals("a/b/c", relative);
    }

    @Test
    void testGetRelativeSubPath_noParent() {
        Path base = tempDir;
        Path file = base.resolve("file.txt");

        String result = FileUtils.getRelativeSubPath(base.toString(), file);

        assertEquals("", result);
    }

    @Test
    void testGetFileSizeInKb_path() throws IOException {
        Path file = tempDir.resolve("sample.txt");
        byte[] content = new byte[2048]; // 2 KB
        Files.write(file, content);

        Long size = FileUtils.getFileSizeInKb(file);

        assertEquals(2, size);
    }

    @Test
    void testGetFileSizeInKb_pathFileNotFound() {
        Path file = tempDir.resolve("missing.txt");

        Long size = FileUtils.getFileSizeInKb(file);

        assertNull(size);
    }

    @Test
    void testGetFileSizeInKb_bookEntity() throws IOException {
        Path library = tempDir.resolve("lib");
        Files.createDirectories(library);

        String sub = "files";
        Path subFolder = library.resolve(sub);
        Files.createDirectories(subFolder);

        Path file = subFolder.resolve("book.epub");
        Files.write(file, new byte[4096]); // 4 KB

        BookEntity book = createBookEntity(library, sub, "book.epub");

        Long size = FileUtils.getFileSizeInKb(book);

        assertEquals(4, size);
    }

    @Test
    void testDeleteDirectoryRecursively() throws IOException {
        Path dir = tempDir.resolve("deleteMe");
        Files.createDirectories(dir);

        Files.write(dir.resolve("file1.txt"), "data".getBytes());
        Files.createDirectories(dir.resolve("nested"));
        Files.write(dir.resolve("nested/file2.txt"), "more".getBytes());

        assertTrue(Files.exists(dir));

        FileUtils.deleteDirectoryRecursively(dir);

        assertFalse(Files.exists(dir));
    }

    @Test
    void testDeleteDirectoryRecursively_nonExistentPath() {
        Path nonExistent = tempDir.resolve("doesNotExist");
        assertDoesNotThrow(() -> FileUtils.deleteDirectoryRecursively(nonExistent));
    }

    @Test
    void testShouldIgnore_hiddenFile_returnsTrue() {
        Path hiddenFile = tempDir.resolve(".hidden");
        assertTrue(FileUtils.shouldIgnore(hiddenFile));
    }

    @Test
    void testShouldIgnore_hiddenDirectory_returnsTrue() {
        Path hiddenDir = tempDir.resolve(".git");
        assertTrue(FileUtils.shouldIgnore(hiddenDir));
    }

    @Test
    void testShouldIgnore_normalFile_returnsFalse() {
        Path normalFile = tempDir.resolve("normal.txt");
        assertFalse(FileUtils.shouldIgnore(normalFile));
    }

    @Test
    void testShouldIgnore_pathWithCaltrash_returnsTrue() {
        Path caltrashPath = tempDir.resolve(".caltrash").resolve("file.txt");
        assertTrue(FileUtils.shouldIgnore(caltrashPath));
    }

    @Test
    void testShouldIgnore_pathWithCaltrashInSubdirectory_returnsTrue() {
        Path caltrashPath = tempDir.resolve("subdir").resolve(".caltrash").resolve("file.txt");
        assertTrue(FileUtils.shouldIgnore(caltrashPath));
    }

    @Test
    void testShouldIgnore_pathWithoutCaltrash_returnsFalse() {
        Path normalPath = tempDir.resolve("subdir").resolve("file.txt");
        assertFalse(FileUtils.shouldIgnore(normalPath));
    }

    @Test
    void testShouldIgnore_emptyFileName_returnsFalse() {
        Path emptyPath = tempDir.resolve("");
        assertFalse(FileUtils.shouldIgnore(emptyPath));
    }

    @Test
    void testGetFileSizeInKb_validFile_returnsSize() throws IOException {
        Path file = tempDir.resolve("test.txt");
        Files.write(file, "test".getBytes());
        
        Long size = FileUtils.getFileSizeInKb(file);
        assertNotNull(size, "File size should not be null for existing file");
        assertTrue(size >= 0, "File size should be non-negative");
    }
}