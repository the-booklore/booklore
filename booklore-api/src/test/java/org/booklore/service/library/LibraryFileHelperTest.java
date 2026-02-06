package org.booklore.service.library;

import org.booklore.model.dto.settings.LibraryFile;
import org.booklore.model.entity.LibraryEntity;
import org.booklore.model.entity.LibraryPathEntity;
import org.booklore.model.enums.LibraryOrganizationMode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.List;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;

@ExtendWith(MockitoExtension.class)
class LibraryFileHelperTest {
    @TempDir
    Path tempDir;

    @Test
    void testGetLibraryFiles_HandlesInaccessibleDirectories() throws IOException {
        LibraryFileHelper libraryFileHelper = new LibraryFileHelper();

        createValidEpub(tempDir.resolve("happy.epub"));
        Files.createDirectory(tempDir.resolve("some_other_random_named_dir"), PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("---------")));
        createValidEpub(tempDir.resolve("zzzz_happ.epub"));

        LibraryPathEntity libraryPath = new LibraryPathEntity();
        libraryPath.setId(10L);
        libraryPath.setPath(tempDir.toString());

        LibraryEntity testLibrary = LibraryEntity.builder()
                .name("Test Library")
                .icon("book")
                .organizationMode(LibraryOrganizationMode.AUTO_DETECT)
                .watch(false)
                .libraryPaths(List.of(libraryPath))
                .build();

        List<LibraryFile> libraryFiles = libraryFileHelper.getLibraryFiles(testLibrary);
        assertEquals(libraryFiles.stream().map(LibraryFile::getFileName).sorted().toList(), List.of("happy.epub", "zzzz_happ.epub"));
    }

    private void createValidEpub(Path path) throws IOException {
        try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(path.toFile()))) {
            byte[] mimetypeContent = "application/epub+zip".getBytes(StandardCharsets.US_ASCII);
            ZipEntry mimetypeEntry = new ZipEntry("mimetype");
            mimetypeEntry.setMethod(ZipEntry.STORED);
            mimetypeEntry.setSize(mimetypeContent.length);
            mimetypeEntry.setCompressedSize(mimetypeContent.length);
            CRC32 crc = new CRC32();
            crc.update(mimetypeContent);
            mimetypeEntry.setCrc(crc.getValue());
            zos.putNextEntry(mimetypeEntry);
            zos.write(mimetypeContent);
            zos.closeEntry();
        }
    }
}