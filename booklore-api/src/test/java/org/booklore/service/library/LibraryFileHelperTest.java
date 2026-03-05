package org.booklore.service.library;

import org.booklore.model.dto.settings.LibraryFile;
import org.booklore.model.entity.LibraryEntity;
import org.booklore.model.entity.LibraryPathEntity;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.junit.jupiter.MockitoExtension;

import org.booklore.model.enums.LibraryOrganizationMode;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class LibraryFileHelperTest {
    @TempDir
    Path tempDir;

    @Test
    void testGetLibraryFiles_HandlesInaccessibleDirectories() throws IOException {
        LibraryFileHelper libraryFileHelper = new LibraryFileHelper();

        Files.write(tempDir.resolve("happy.epub"), new byte[]{1});
        Files.createDirectory(tempDir.resolve("some_other_random_named_dir"), PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("---------")));
        Files.write(tempDir.resolve("zzzz_happ.epub"), new byte[]{1});

        LibraryPathEntity libraryPath = new LibraryPathEntity();
        libraryPath.setId(10L);
        libraryPath.setPath(tempDir.toString());

        LibraryEntity testLibrary = LibraryEntity.builder()
                .name("Test Library")
                .icon("book")
                .watch(false)
                .libraryPaths(List.of(libraryPath))
                .build();

        List<LibraryFile> libraryFiles = libraryFileHelper.getLibraryFiles(testLibrary);
        assertEquals(libraryFiles.stream().map(LibraryFile::getFileName).sorted().toList(), List.of("happy.epub", "zzzz_happ.epub"));
    }

    private LibraryEntity createLibrary(Path path) {
        LibraryPathEntity libraryPath = new LibraryPathEntity();
        libraryPath.setId(1L);
        libraryPath.setPath(path.toString());

        return LibraryEntity.builder()
                .name("Test Library")
                .icon("book")
                .watch(false)
                .organizationMode(LibraryOrganizationMode.BOOK_PER_FILE)
                .libraryPaths(List.of(libraryPath))
                .build();
    }

    @Test
    void testGetLibraryFiles_skipsDirectoryWithIgnoreFile() throws IOException {
        LibraryFileHelper helper = new LibraryFileHelper();

        Path ignoredDir = Files.createDirectories(tempDir.resolve("ignored-folder"));
        Files.createFile(ignoredDir.resolve(".ignore"));
        Files.write(ignoredDir.resolve("hidden-book.epub"), new byte[]{1});

        Files.write(tempDir.resolve("visible-book.epub"), new byte[]{1});

        List<LibraryFile> files = helper.getLibraryFiles(createLibrary(tempDir));
        assertThat(files).hasSize(1);
        assertThat(files.getFirst().getFileName()).isEqualTo("visible-book.epub");
    }

    @Test
    void testGetLibraryFiles_doesNotSkipRootWithIgnoreFile() throws IOException {
        LibraryFileHelper helper = new LibraryFileHelper();

        Files.createFile(tempDir.resolve(".ignore"));
        Files.write(tempDir.resolve("root-book.epub"), new byte[]{1});

        List<LibraryFile> files = helper.getLibraryFiles(createLibrary(tempDir));
        assertThat(files).hasSize(1);
        assertThat(files.getFirst().getFileName()).isEqualTo("root-book.epub");
    }

    @Test
    void testGetLibraryFiles_ignoreFileOnlyAffectsItsDirectory() throws IOException {
        LibraryFileHelper helper = new LibraryFileHelper();

        Path ignoredDir = Files.createDirectories(tempDir.resolve("ignored"));
        Files.createFile(ignoredDir.resolve(".ignore"));
        Files.write(ignoredDir.resolve("hidden.epub"), new byte[]{1});

        Path visibleDir = Files.createDirectories(tempDir.resolve("visible"));
        Files.write(visibleDir.resolve("book.epub"), new byte[]{1});

        List<LibraryFile> files = helper.getLibraryFiles(createLibrary(tempDir));
        assertThat(files).hasSize(1);
        assertThat(files.getFirst().getFileName()).isEqualTo("book.epub");
    }

    @Test
    void testGetLibraryFiles_skipsZeroByteFiles() throws IOException {
        LibraryFileHelper helper = new LibraryFileHelper();

        Files.createFile(tempDir.resolve("empty.epub"));
        Files.write(tempDir.resolve("real.epub"), new byte[]{1, 2, 3});

        List<LibraryFile> files = helper.getLibraryFiles(createLibrary(tempDir));
        assertThat(files).hasSize(1);
        assertThat(files.getFirst().getFileName()).isEqualTo("real.epub");
    }

    @Test
    void testGetLibraryFiles_includesNonZeroByteFiles() throws IOException {
        LibraryFileHelper helper = new LibraryFileHelper();

        Files.write(tempDir.resolve("book1.epub"), new byte[]{1});
        Files.write(tempDir.resolve("book2.pdf"), new byte[]{1, 2});

        List<LibraryFile> files = helper.getLibraryFiles(createLibrary(tempDir));
        assertThat(files).hasSize(2);
    }
}
