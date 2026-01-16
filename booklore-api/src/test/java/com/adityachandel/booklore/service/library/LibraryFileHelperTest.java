package com.adityachandel.booklore.service.library;

import com.adityachandel.booklore.model.dto.LibraryPath;
import com.adityachandel.booklore.model.dto.settings.LibraryFile;
import com.adityachandel.booklore.model.entity.LibraryEntity;
import com.adityachandel.booklore.model.entity.LibraryPathEntity;
import com.adityachandel.booklore.model.enums.LibraryScanMode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class LibraryFileHelperTest {
    @TempDir
    Path tempDir;

    @Mock
    private LibraryFileProcessor processor;

    @Test
    void testGetLibraryFiles_HandlesInaccessibleDirectories() throws IOException {
        LibraryFileHelper libraryFileHelper = new LibraryFileHelper();

        Files.createFile(tempDir.resolve("happy.epub"));
        Files.createDirectory(tempDir.resolve("some_other_random_named_dir"), PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("---------")));
        Files.createFile(tempDir.resolve("zzzz_happ.epub"));

        LibraryPathEntity libraryPath = new LibraryPathEntity();
        libraryPath.setId(10L);
        libraryPath.setPath(tempDir.toString());

        LibraryEntity testLibrary = LibraryEntity.builder()
                .name("Test Library")
                .icon("book")
                .scanMode(LibraryScanMode.FILE_AS_BOOK)
                .watch(false)
                .libraryPaths(List.of(libraryPath))
                .build();

        List<LibraryFile> libraryFiles = libraryFileHelper.getLibraryFiles(testLibrary, processor);
        assertEquals(libraryFiles.stream().map(LibraryFile::getFileName).sorted().toList(), List.of("happy.epub", "zzzz_happ.epub"));
    }
}