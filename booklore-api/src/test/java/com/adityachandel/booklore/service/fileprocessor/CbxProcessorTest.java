package com.adityachandel.booklore.service.fileprocessor;

import com.adityachandel.booklore.mapper.BookMapper;
import com.adityachandel.booklore.model.entity.BookEntity;
import com.adityachandel.booklore.model.entity.BookFileEntity;
import com.adityachandel.booklore.model.entity.BookMetadataEntity;
import com.adityachandel.booklore.model.entity.LibraryPathEntity;
import com.adityachandel.booklore.repository.BookAdditionalFileRepository;
import com.adityachandel.booklore.repository.BookRepository;
import com.adityachandel.booklore.service.book.BookCreatorService;
import com.adityachandel.booklore.service.metadata.MetadataMatchService;
import com.adityachandel.booklore.service.metadata.extractor.CbxMetadataExtractor;
import com.adityachandel.booklore.util.FileService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CbxProcessorTest {

    @Mock private BookRepository bookRepository;
    @Mock private BookAdditionalFileRepository bookAdditionalFileRepository;
    @Mock private BookCreatorService bookCreatorService;
    @Mock private BookMapper bookMapper;
    @Mock private FileService fileService;
    @Mock private MetadataMatchService metadataMatchService;
    @Mock private CbxMetadataExtractor cbxMetadataExtractor;

    private CbxProcessor cbxProcessor;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        cbxProcessor = new CbxProcessor(
                bookRepository,
                bookAdditionalFileRepository,
                bookCreatorService,
                bookMapper,
                fileService,
                metadataMatchService,
                cbxMetadataExtractor
        );
    }

    @Test
    void generateCover_ZipNamedAsCbr_ShouldExtractImage() throws IOException {
        // Create a ZIP file named .cbr
        File zipAsCbr = tempDir.resolve("mismatched.cbr").toFile();
        try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(zipAsCbr))) {
            ZipEntry entry = new ZipEntry("cover.jpg");
            zos.putNextEntry(entry);
            BufferedImage image = new BufferedImage(10, 10, BufferedImage.TYPE_INT_RGB);
            ImageIO.write(image, "jpg", zos);
            zos.closeEntry();
        }

        BookEntity bookEntity = new BookEntity();
        bookEntity.setId(1L);

        // Create and configure the primary book file
        BookFileEntity bookFile = new BookFileEntity();
        bookFile.setFileName(zipAsCbr.getName());
        bookFile.setFileSubPath("");
        bookFile.setBook(bookEntity);
        bookEntity.getBookFiles().add(bookFile);
        LibraryPathEntity libPath = new LibraryPathEntity();
        libPath.setPath(tempDir.toString());
        bookEntity.setLibraryPath(libPath);
        bookEntity.setMetadata(new BookMetadataEntity());

        when(fileService.saveCoverImages(any(BufferedImage.class), eq(1L))).thenReturn(true);

        cbxProcessor.generateCover(bookEntity);

        verify(fileService).saveCoverImages(any(BufferedImage.class), eq(1L));
    }
}
