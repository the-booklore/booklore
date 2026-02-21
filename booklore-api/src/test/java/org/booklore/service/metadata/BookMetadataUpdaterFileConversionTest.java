package org.booklore.service.metadata;

import org.booklore.model.entity.BookFileEntity;
import org.booklore.repository.*;
import org.booklore.service.appsettings.AppSettingService;
import org.booklore.service.file.FileMoveService;
import org.booklore.service.metadata.sidecar.SidecarMetadataWriter;
import org.booklore.service.metadata.writer.MetadataWriterFactory;
import org.booklore.util.FileService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

@ExtendWith(MockitoExtension.class)
class BookMetadataUpdaterFileConversionTest {

    @Mock private AuthorRepository authorRepository;
    @Mock private CategoryRepository categoryRepository;
    @Mock private MoodRepository moodRepository;
    @Mock private TagRepository tagRepository;
    @Mock private BookRepository bookRepository;
    @Mock private ComicMetadataRepository comicMetadataRepository;
    @Mock private ComicCharacterRepository comicCharacterRepository;
    @Mock private ComicTeamRepository comicTeamRepository;
    @Mock private ComicLocationRepository comicLocationRepository;
    @Mock private ComicCreatorRepository comicCreatorRepository;
    @Mock private FileService fileService;
    @Mock private MetadataMatchService metadataMatchService;
    @Mock private AppSettingService appSettingService;
    @Mock private MetadataWriterFactory metadataWriterFactory;
    @Mock private BookReviewUpdateService bookReviewUpdateService;
    @Mock private FileMoveService fileMoveService;
    @Mock private SidecarMetadataWriter sidecarMetadataWriter;

    @InjectMocks
    private BookMetadataUpdater bookMetadataUpdater;

    @TempDir
    Path tempDir;

    private BookFileEntity bookFile;

    @BeforeEach
    void setUp() {
        bookFile = new BookFileEntity();
    }

    @Test
    void updateFileNameIfConverted_cbrConvertedToCbz_shouldUpdateFileName() throws IOException {
        bookFile.setFileName("Spawn 044.cbr");
        Path cbzFile = Files.createFile(tempDir.resolve("Spawn 044.cbz"));
        Path originalPath = tempDir.resolve("Spawn 044.cbr");

        bookMetadataUpdater.updateFileNameIfConverted(bookFile, originalPath);

        assertEquals("Spawn 044.cbz", bookFile.getFileName());
    }

    @Test
    void updateFileNameIfConverted_cb7ConvertedToCbz_shouldUpdateFileName() throws IOException {
        bookFile.setFileName("comic.cb7");
        Files.createFile(tempDir.resolve("comic.cbz"));
        Path originalPath = tempDir.resolve("comic.cb7");

        bookMetadataUpdater.updateFileNameIfConverted(bookFile, originalPath);

        assertEquals("comic.cbz", bookFile.getFileName());
    }

    @Test
    void updateFileNameIfConverted_originalStillExists_shouldNotChangeFileName() throws IOException {
        bookFile.setFileName("comic.cbr");
        Files.createFile(tempDir.resolve("comic.cbr"));
        Path originalPath = tempDir.resolve("comic.cbr");

        bookMetadataUpdater.updateFileNameIfConverted(bookFile, originalPath);

        assertEquals("comic.cbr", bookFile.getFileName());
    }

    @Test
    void updateFileNameIfConverted_noCbzExists_shouldNotChangeFileName() {
        bookFile.setFileName("comic.cbr");
        Path originalPath = tempDir.resolve("comic.cbr");

        bookMetadataUpdater.updateFileNameIfConverted(bookFile, originalPath);

        assertEquals("comic.cbr", bookFile.getFileName());
    }

    @Test
    void updateFileNameIfConverted_fileNameWithMultipleDots_shouldUpdateCorrectly() throws IOException {
        bookFile.setFileName("Spawn 044 (1996) (digital).cbr");
        Files.createFile(tempDir.resolve("Spawn 044 (1996) (digital).cbz"));
        Path originalPath = tempDir.resolve("Spawn 044 (1996) (digital).cbr");

        bookMetadataUpdater.updateFileNameIfConverted(bookFile, originalPath);

        assertEquals("Spawn 044 (1996) (digital).cbz", bookFile.getFileName());
    }
}
