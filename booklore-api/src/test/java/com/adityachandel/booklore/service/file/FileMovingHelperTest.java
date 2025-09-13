package com.adityachandel.booklore.service.file;

import com.adityachandel.booklore.model.dto.BookMetadata;
import com.adityachandel.booklore.model.dto.settings.AppSettings;
import com.adityachandel.booklore.model.entity.*;
import com.adityachandel.booklore.repository.BookAdditionalFileRepository;
import com.adityachandel.booklore.service.appsettings.AppSettingService;
import com.adityachandel.booklore.util.PathPatternResolver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FileMovingHelperTest {

    @Mock
    BookAdditionalFileRepository additionalFileRepository;

    @Mock
    AppSettingService appSettingService;

    @InjectMocks
    FileMovingHelper helper;

    @TempDir
    Path tmp;

    LibraryEntity library;
    LibraryPathEntity libraryPath;
    BookEntity book;

    @BeforeEach
    void init() {
        library = new LibraryEntity();
        library.setId(11L);
        library.setName("lib");
        libraryPath = new LibraryPathEntity();
        libraryPath.setId(22L);
        libraryPath.setPath(tmp.toString());
        library.setLibraryPaths(List.of(libraryPath));

        book = new BookEntity();
        book.setId(101L);
        BookMetadataEntity metaEntity = new BookMetadataEntity();
        metaEntity.setTitle("Title");
        book.setMetadata(metaEntity);
        metaEntity.setBook(book);
        book.setLibraryPath(libraryPath);
    }

    @Test
    void generateNewFilePath_fromBook_usesResolvedPattern() {
        try (MockedStatic<PathPatternResolver> ms = mockStatic(PathPatternResolver.class)) {
            ms.when(() -> PathPatternResolver.resolvePattern(eq(book), eq("{pattern}")))
                    .thenReturn("some/sub/path/book.pdf");

            Path result = helper.generateNewFilePath(book, "{pattern}");
            assertTrue(result.toAbsolutePath().toString().contains("some/sub/path/book.pdf"));
        }
    }

    @Test
    void generateNewFilePath_fromMetadata_usesResolvedPattern() {
        BookMetadata metadata = new BookMetadata();
        try (MockedStatic<PathPatternResolver> ms = mockStatic(PathPatternResolver.class)) {
            ms.when(() -> PathPatternResolver.resolvePattern(eq(metadata), eq("{p}"), eq("orig.pdf")))
                    .thenReturn("meta/path/orig.pdf");

            Path result = helper.generateNewFilePath(tmp.toString(), metadata, "{p}", "orig.pdf");
            assertTrue(result.toString().endsWith("meta/path/orig.pdf"));
        }
    }

    @Test
    void getFileNamingPattern_prefersLibrary_thenApp_thenFallback() {
        library.setFileNamingPattern("LIB_PATTERN/{currentFilename}");
        String p1 = helper.getFileNamingPattern(library);
        assertEquals("LIB_PATTERN/{currentFilename}", p1);

        library.setFileNamingPattern(null);
        AppSettings settings = new AppSettings();
        settings.setUploadPattern("APP_PATTERN/{currentFilename}");
        when(appSettingService.getAppSettings()).thenReturn(settings);
        String p2 = helper.getFileNamingPattern(library);
        assertEquals("APP_PATTERN/{currentFilename}", p2);

        settings.setUploadPattern(null);
        when(appSettingService.getAppSettings()).thenReturn(settings);
        String p3 = helper.getFileNamingPattern(library);
        assertEquals("{currentFilename}", p3);
    }

    @Test
    void hasRequiredPathComponents_returnsFalseWhenMissing() {
        BookEntity b = new BookEntity();
        b.setId(1L);
        b.setFileName("f");
        b.setFileSubPath("s");
        assertFalse(helper.hasRequiredPathComponents(b));

        book.setFileSubPath("s");
        book.setFileName(null);
        assertFalse(helper.hasRequiredPathComponents(book));

        book.setFileName("file.pdf");
        assertTrue(helper.hasRequiredPathComponents(book));
    }

    @Test
    void moveBookFileIfNeeded_noOpWhenPathsEqual() throws IOException {
        book.setFileSubPath("same");
        book.setFileName("file.pdf");
        try (MockedStatic<PathPatternResolver> ms = mockStatic(PathPatternResolver.class)) {
            ms.when(() -> PathPatternResolver.resolvePattern(eq(book), anyString()))
                    .thenReturn("same/file.pdf");

            boolean changed = helper.moveBookFileIfNeeded(book, "{p}");
            assertFalse(changed);
        }
    }

    @Test
    void moveBookFileIfNeeded_movesAndUpdatesPaths() throws Exception {
        book.setFileSubPath("olddir");
        book.setFileName("file.pdf");
        Path oldDir = tmp.resolve("olddir");
        Files.createDirectories(oldDir);
        Path oldFile = oldDir.resolve("file.pdf");
        Files.createFile(oldFile);

        try (MockedStatic<PathPatternResolver> ms = mockStatic(PathPatternResolver.class)) {
            ms.when(() -> PathPatternResolver.resolvePattern(eq(book), anyString()))
                    .thenReturn("newdir/file.pdf");

            boolean moved = helper.moveBookFileIfNeeded(book, "{p}");
            assertTrue(moved);

            Path newFile = tmp.resolve("newdir").resolve("file.pdf");
            assertTrue(Files.exists(newFile));
            assertEquals("newdir", book.getFileSubPath());
            assertEquals("file.pdf", book.getFileName());
            assertFalse(Files.exists(oldFile));
        }
    }

    @Test
    void moveAdditionalFiles_movesFilesAndSaves() throws Exception {
        book.setFileSubPath(".");
        book.setFileName("book.pdf");
        BookAdditionalFileEntity add = new BookAdditionalFileEntity();
        add.setId(555L);
        add.setFileSubPath("oldsub");
        add.setFileName("add.pdf");
        add.setBook(book);
        book.setAdditionalFiles(List.of(add));

        Path oldDir = tmp.resolve("oldsub");
        Files.createDirectories(oldDir);
        Path oldFile = oldDir.resolve("add.pdf");
        Files.createFile(oldFile);

        try (MockedStatic<PathPatternResolver> ms = mockStatic(PathPatternResolver.class)) {
            ms.when(() -> PathPatternResolver.resolvePattern(eq(book.getMetadata()), anyString(), eq("add.pdf")))
                    .thenReturn("additional/newadd.pdf");

            helper.moveAdditionalFiles(book, "{pattern}");

            Path newFile = tmp.resolve("additional").resolve("newadd.pdf");
            assertTrue(Files.exists(newFile));
            verify(additionalFileRepository).save(add);
            assertEquals("newadd.pdf", add.getFileName());
        }
    }

    @Test
    void generateNewFilePath_trimsLeadingSeparator() {
        try (MockedStatic<PathPatternResolver> ms = mockStatic(PathPatternResolver.class)) {
            ms.when(() -> PathPatternResolver.resolvePattern(eq(book), eq("{pattern}")))
                    .thenReturn("/leading/path/book.pdf");

            Path result = helper.generateNewFilePath(book, "{pattern}");
            assertTrue(result.toString().endsWith("leading/path/book.pdf"));
            assertFalse(result.toString().contains("//"));
        }
    }

    @Test
    void getFileNamingPattern_appendsFilename_whenEndsWithSeparator() {
        library.setFileNamingPattern("SOME/PATTERN/");
        String pattern = helper.getFileNamingPattern(library);
        assertTrue(pattern.endsWith("{currentFilename}"));
        assertEquals("SOME/PATTERN/{currentFilename}", pattern);
    }

    @Test
    void moveFile_createsParentDirectories_and_movesFile() throws Exception {
        Path srcDir = tmp.resolve("srcdir");
        Files.createDirectories(srcDir);
        Path src = srcDir.resolve("file.txt");
        Files.writeString(src, "hello");

        Path target = tmp.resolve("nested").resolve("deep").resolve("file.txt");
        assertFalse(Files.exists(target.getParent()));

        helper.moveFile(src, target);

        assertTrue(Files.exists(target));
        assertFalse(Files.exists(src));
        assertTrue(Files.exists(target.getParent()));
    }

    @Test
    void deleteEmptyParentDirsUpToLibraryFolders_deletesIgnoredFilesAndDirs() throws Exception {
        Path libRoot = tmp.resolve("libroot");
        Path dir1 = libRoot.resolve("dir1");
        Path dir2 = dir1.resolve("dir2");
        Files.createDirectories(dir2);

        Files.writeString(dir2.resolve(".DS_Store"), "");
        Files.writeString(dir1.resolve(".DS_Store"), "");

        assertTrue(Files.exists(dir2));
        assertTrue(Files.exists(dir1));
        assertTrue(Files.exists(libRoot) || Files.createDirectories(libRoot) != null);

        helper.deleteEmptyParentDirsUpToLibraryFolders(dir2, Set.of(libRoot));

        assertFalse(Files.exists(dir2));
        assertFalse(Files.exists(dir1));
        assertTrue(Files.exists(libRoot));
    }

    @Test
    void deleteEmptyParentDirsUpToLibraryFolders_stopsWhenNonIgnoredPresent() throws Exception {
        Path libRoot = tmp.resolve("libroot2");
        Path dir = libRoot.resolve("keepdir");
        Files.createDirectories(dir);

        Files.writeString(dir.resolve("keep.txt"), "keep");

        helper.deleteEmptyParentDirsUpToLibraryFolders(dir, Set.of(libRoot));

        assertTrue(Files.exists(dir));
    }

    @Test
    void moveAdditionalFiles_handles_duplicate_target_names_and_saves() throws Exception {
        book.setFileSubPath(".");
        book.setFileName("book.pdf");

        BookAdditionalFileEntity a1 = new BookAdditionalFileEntity();
        a1.setId(1L);
        a1.setFileSubPath("oldsub");
        a1.setFileName("add.pdf");
        a1.setBook(book);

        BookAdditionalFileEntity a2 = new BookAdditionalFileEntity();
        a2.setId(2L);
        a2.setFileSubPath("oldsub");
        a2.setFileName("add_2.pdf");
        a2.setBook(book);

        book.setAdditionalFiles(List.of(a1, a2));

        Path oldDir = tmp.resolve("oldsub");
        Files.createDirectories(oldDir);
        Files.writeString(oldDir.resolve("add.pdf"), "one");
        Files.writeString(oldDir.resolve("add_2.pdf"), "two");

        try (MockedStatic<PathPatternResolver> ms = mockStatic(PathPatternResolver.class)) {
            ms.when(() -> PathPatternResolver.resolvePattern(eq(book.getMetadata()), anyString(), eq("add.pdf")))
                    .thenReturn("additional/add.pdf");
            ms.when(() -> PathPatternResolver.resolvePattern(eq(book.getMetadata()), anyString(), eq("add_2.pdf")))
                    .thenReturn("additional/add.pdf");

            helper.moveAdditionalFiles(book, "{pattern}");

            Path first = tmp.resolve("additional").resolve("add.pdf");
            Path second = tmp.resolve("additional").resolve("add_2.pdf");

            assertTrue(Files.exists(first));
            assertTrue(Files.exists(second));

            verify(additionalFileRepository, atLeastOnce()).save(any(BookAdditionalFileEntity.class));

            assertTrue(a1.getFileName().equals("add.pdf") || a1.getFileName().equals("add_2.pdf"));
            assertTrue(a2.getFileName().equals("add.pdf") || a2.getFileName().equals("add_2.pdf"));
            assertNotEquals(a1.getFileName(), a2.getFileName());
        }
    }
}
