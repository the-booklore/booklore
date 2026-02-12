package org.booklore.service.metadata;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.booklore.model.MetadataClearFlags;
import org.booklore.model.dto.BookMetadata;
import org.booklore.model.dto.settings.AppSettingKey;
import org.booklore.model.dto.settings.MetadataPersistenceSettings;
import org.booklore.model.entity.*;
import org.booklore.model.enums.BookFileExtension;
import org.booklore.model.enums.MetadataProvider;
import org.booklore.repository.AppSettingsRepository;
import org.booklore.service.appsettings.AppSettingService;
import org.booklore.service.metadata.extractor.MetadataExtractorFactory;
import org.booklore.service.metadata.writer.MetadataWriter;
import org.booklore.service.metadata.writer.MetadataWriterFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class EndToEndMetadataPersistenceTest {

    @Autowired
    private MetadataWriterFactory metadataWriterFactory;

    @Autowired
    private MetadataExtractorFactory metadataExtractorFactory;

    @Autowired
    private AppSettingService appSettingService;

    @Autowired
    private AppSettingsRepository appSettingsRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @TempDir
    Path tempDir;

    private File pdfFile;
    private File epubFile;
    private File cbzFile;

    @BeforeEach
    void setUp() throws IOException {
        // Ensure clean state for settings using repository directly (transactional rollout will handle cleanup after test)
        appSettingsRepository.deleteAll();
        appSettingsRepository.flush();

        MetadataPersistenceSettings settings = MetadataPersistenceSettings.builder()
                .saveToOriginalFile(MetadataPersistenceSettings.SaveToOriginalFile.builder()
                        .epub(MetadataPersistenceSettings.FormatSettings.builder().enabled(true).maxFileSizeInMb(500).build())
                        .pdf(MetadataPersistenceSettings.FormatSettings.builder().enabled(true).maxFileSizeInMb(500).build())
                        .cbx(MetadataPersistenceSettings.FormatSettings.builder().enabled(true).maxFileSizeInMb(500).build())
                        .build())
                .build();

        AppSettingEntity entity = new AppSettingEntity();
        entity.setName(AppSettingKey.METADATA_PERSISTENCE_SETTINGS.getDbKey());
        entity.setVal(objectMapper.writeValueAsString(settings));
        appSettingsRepository.saveAndFlush(entity);

        // Force reload settings cache
        ReflectionTestUtils.setField(appSettingService, "appSettings", null);

        pdfFile = tempDir.resolve("test.pdf").toFile();
        createDummyPdf(pdfFile);

        epubFile = tempDir.resolve("test.epub").toFile();
        createDummyEpub(epubFile);

        cbzFile = tempDir.resolve("test.cbz").toFile();
        createDummyCbz(cbzFile);
    }

    @Test
    void shouldPersistAndRetrieveCompleteMetadataForPdf() {
        verifyMetadataPersistence(pdfFile, BookFileExtension.PDF);
    }

    @Test
    void shouldPersistAndRetrieveCompleteMetadataForEpub() {
        verifyMetadataPersistence(epubFile, BookFileExtension.EPUB);
    }

    @Test
    void shouldPersistAndRetrieveCompleteMetadataForCbz() {
        verifyMetadataPersistence(cbzFile, BookFileExtension.CBZ);
    }

    private void verifyMetadataPersistence(File file, BookFileExtension extension) {
        BookMetadata originalMetadata = createFullMetadata();
        BookMetadataEntity entity = toEntity(originalMetadata);

        MetadataWriter writer = metadataWriterFactory.getWriter(extension.getType())
                .orElseThrow(() -> new RuntimeException("No writer found for " + extension));

        writer.saveMetadataToFile(file, entity, originalMetadata.getThumbnailUrl(), new MetadataClearFlags());

        assertThat(file).exists();

        BookMetadata rescannedMetadata = metadataExtractorFactory.extractMetadata(extension, file);

        assertMetadataEquality(originalMetadata, rescannedMetadata, extension);
    }

    private BookMetadata createFullMetadata() {
        return BookMetadata.builder()
                .title("The Ultimate Test Book")
                .subtitle("A Comprehensive Guide to Metadata")
                .provider(MetadataProvider.Google)
                .authors(Set.of("Author One", "Author Two"))
                .publisher("Test Publisher")
                .publishedDate(LocalDate.of(2023, 10, 27))
                .description("This is a description used for testing metadata persistence.")
                .seriesName("The Test Series")
                .seriesNumber(1.5f)
                .seriesTotal(3)
                .isbn13("978-3-16-148410-0")
                .isbn10("3-16-148410-0")
                .asin("B00TESTASI")
                .pageCount(350)
                .language("en")
                .categories(Set.of("Fiction", "Testing", "Science"))
                .tags(Set.of("Best Seller", "Must Read"))
                .moods(Set.of("Happy", "Suspenseful"))

                // Ratings
                .amazonRating(4.5)
                .goodreadsRating(4.2)
                .hardcoverRating(85.0)
                .lubimyczytacRating(7.5)
                .ranobedbRating(9.0)

                // Identifiers
                .goodreadsId("12345")
                .comicvineId("4000-12345")
                .hardcoverId("test-book-slug")
                .hardcoverBookId("999")
                .googleId("google_id_123")
                .ranobedbId("ranobe-000")
                .lubimyczytacId("lub-123")
                .externalUrl("https://booklore.org")

                // Ensure all strict locks are disabled to allow writing
                .titleLocked(false)
                .subtitleLocked(false)
                .publisherLocked(false)
                .publishedDateLocked(false)
                .descriptionLocked(false)
                .seriesNameLocked(false)
                .seriesNumberLocked(false)
                .seriesTotalLocked(false)
                .isbn13Locked(false)
                .isbn10Locked(false)
                .asinLocked(false)
                .goodreadsIdLocked(false)
                .comicvineIdLocked(false)
                .hardcoverIdLocked(false)
                .hardcoverBookIdLocked(false)
                .doubanIdLocked(false)
                .googleIdLocked(false)
                .languageLocked(false)
                .amazonRatingLocked(false)
                .amazonReviewCountLocked(false)
                .goodreadsRatingLocked(false)
                .goodreadsReviewCountLocked(false)
                .hardcoverRatingLocked(false)
                .hardcoverReviewCountLocked(false)
                .doubanRatingLocked(false)
                .doubanReviewCountLocked(false)
                .lubimyczytacIdLocked(false)
                .lubimyczytacRatingLocked(false)
                .ranobedbIdLocked(false)
                .ranobedbRatingLocked(false)
                .externalUrlLocked(false)
                .coverLocked(false)
                .authorsLocked(false)
                .categoriesLocked(false)
                .moodsLocked(false)
                .tagsLocked(false)
                .reviewsLocked(false)
                .build();
    }

    // Manual mapping for test purposes
    private BookMetadataEntity toEntity(BookMetadata dto) {
        BookMetadataEntity entity = new BookMetadataEntity();
        entity.setTitle(dto.getTitle());
        entity.setSubtitle(dto.getSubtitle());
        entity.setPublisher(dto.getPublisher());
        entity.setPublishedDate(dto.getPublishedDate());
        entity.setDescription(dto.getDescription());
        entity.setSeriesName(dto.getSeriesName());
        entity.setSeriesNumber(dto.getSeriesNumber());
        entity.setSeriesTotal(dto.getSeriesTotal());
        entity.setIsbn13(dto.getIsbn13());
        entity.setIsbn10(dto.getIsbn10());
        entity.setAsin(dto.getAsin());
        entity.setPageCount(dto.getPageCount());
        entity.setLanguage(dto.getLanguage());
        entity.setGoodreadsId(dto.getGoodreadsId());
        entity.setComicvineId(dto.getComicvineId());
        entity.setHardcoverId(dto.getHardcoverId());
        entity.setGoogleId(dto.getGoogleId());
        entity.setRanobedbId(dto.getRanobedbId());
        entity.setLubimyczytacId(dto.getLubimyczytacId());

        entity.setAmazonRating(dto.getAmazonRating());
        entity.setGoodreadsRating(dto.getGoodreadsRating());
        entity.setHardcoverRating(dto.getHardcoverRating());
        entity.setHardcoverBookId(dto.getHardcoverBookId());
        entity.setLubimyczytacRating(dto.getLubimyczytacRating());
        entity.setRanobedbRating(dto.getRanobedbRating());

        if (dto.getAuthors() != null) {
            entity.setAuthors(dto.getAuthors().stream().map(name -> {
                var a = new AuthorEntity();
                a.setName(name);
                return a;
            }).collect(Collectors.toSet()));
        }
        if (dto.getCategories() != null) {
            entity.setCategories(dto.getCategories().stream().map(name -> {
                var c = new CategoryEntity();
                c.setName(name);
                return c;
            }).collect(Collectors.toSet()));
        }
        if (dto.getTags() != null) {
            entity.setTags(dto.getTags().stream().map(name -> {
                var t = new TagEntity();
                t.setName(name);
                return t;
            }).collect(Collectors.toSet()));
        }
        if (dto.getMoods() != null) {
            entity.setMoods(dto.getMoods().stream().map(name -> {
                var m = new MoodEntity();
                m.setName(name);
                return m;
            }).collect(Collectors.toSet()));
        }
        return entity;
    }

    private void assertMetadataEquality(BookMetadata expected, BookMetadata actual, BookFileExtension extension) {
        // 1. Basic Fields
        assertThat(actual.getTitle()).as("Title").isEqualTo(expected.getTitle());
        assertThat(actual.getPublisher()).as("Publisher").isEqualTo(expected.getPublisher());
        assertThat(actual.getLanguage()).as("Language").isEqualTo(expected.getLanguage());
        assertThat(actual.getDescription()).as("Description").isEqualTo(expected.getDescription());

        // 2. Series Info
        assertThat(actual.getSeriesName()).as("SeriesName").isEqualTo(expected.getSeriesName());
        assertThat(actual.getSeriesNumber()).as("SeriesNumber").isEqualTo(expected.getSeriesNumber());
        assertThat(actual.getSeriesTotal()).as("SeriesTotal").isEqualTo(expected.getSeriesTotal());

        // 3. Collections
        assertThat(actual.getAuthors()).as("Authors").containsExactlyInAnyOrderElementsOf(expected.getAuthors());
        assertThat(actual.getCategories()).as("Categories").containsExactlyInAnyOrderElementsOf(expected.getCategories());

        if (extension != BookFileExtension.CBZ) {
            assertThat(actual.getTags()).as("Tags").containsExactlyInAnyOrderElementsOf(expected.getTags());
            assertThat(actual.getMoods()).as("Moods").containsExactlyInAnyOrderElementsOf(expected.getMoods());
        } else {
            assertThat(actual.getTags()).as("Tags").containsExactlyInAnyOrderElementsOf(expected.getTags());
            assertThat(actual.getMoods()).as("Moods").containsExactlyInAnyOrderElementsOf(expected.getMoods());
        }

        // 4. Identifiers & ISBN Normalization
        String expectedIsbn13 = expected.getIsbn13() != null ? expected.getIsbn13().replaceAll("[- ]", "") : null;
        String actualIsbn13 = actual.getIsbn13() != null ? actual.getIsbn13().replaceAll("[- ]", "") : null;
        assertThat(actualIsbn13).as("ISBN13").isEqualTo(expectedIsbn13);

        String expectedIsbn10 = expected.getIsbn10() != null ? expected.getIsbn10().replaceAll("[- ]", "") : null;
        String actualIsbn10 = actual.getIsbn10() != null ? actual.getIsbn10().replaceAll("[- ]", "") : null;
        assertThat(actualIsbn10).as("ISBN10").isEqualTo(expectedIsbn10);

        assertThat(actual.getAsin()).as("ASIN").isEqualTo(expected.getAsin());
        assertThat(actual.getGoodreadsId()).as("GoodreadsId").isEqualTo(expected.getGoodreadsId());
        assertThat(actual.getComicvineId()).as("ComicvineId").isEqualTo(expected.getComicvineId());
        assertThat(actual.getHardcoverId()).as("HardcoverId").isEqualTo(expected.getHardcoverId());
        assertThat(actual.getHardcoverBookId()).as("HardcoverBookId").isEqualTo(expected.getHardcoverBookId());
        assertThat(actual.getGoogleId()).as("GoogleId").isEqualTo(expected.getGoogleId());
        assertThat(actual.getRanobedbId()).as("RanobeDBId").isEqualTo(expected.getRanobedbId());
        assertThat(actual.getLubimyczytacId()).as("LubimyczytacId").isEqualTo(expected.getLubimyczytacId());

        // 5. Ratings
        assertThat(actual.getAmazonRating()).as("AmazonRating").isEqualTo(expected.getAmazonRating());
        assertThat(actual.getGoodreadsRating()).as("GoodreadsRating").isEqualTo(expected.getGoodreadsRating());
        assertThat(actual.getHardcoverRating()).as("HardcoverRating").isEqualTo(expected.getHardcoverRating());
        assertThat(actual.getLubimyczytacRating()).as("LubimyczytacRating").isEqualTo(expected.getLubimyczytacRating());
        assertThat(actual.getRanobedbRating()).as("RanobeDBRating").isEqualTo(expected.getRanobedbRating());

        // 6. Format Specific Limitations
        if (extension != BookFileExtension.PDF) {
            // PDF page count corresponds to physical pages, which is 1 in dummy file.
            assertThat(actual.getPageCount()).as("PageCount").isEqualTo(expected.getPageCount());

            // PDF extractor reads CreationDate which might differ from PublishedDate
            assertThat(actual.getPublishedDate()).as("PublishedDate").isEqualTo(expected.getPublishedDate());
        } else {
             // For PDF, we expect 1 page because the dummy pdf only has 1 page
             assertThat(actual.getPageCount()).as("PdfPageCount").isEqualTo(1);
        }

        // 7. Subtitle Check
        assertThat(actual.getSubtitle()).as("Subtitle").isEqualTo(expected.getSubtitle());
    }

    private void createDummyPdf(File file) throws IOException {
        try (PDDocument doc = new PDDocument()) {
            doc.addPage(new PDPage());
            doc.save(file);
        }
    }

    private void createDummyEpub(File file) throws IOException {
        try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(file))) {
            // mimetype file must be first and uncompressed
            ZipEntry mimetype = new ZipEntry("mimetype");
            mimetype.setMethod(ZipEntry.STORED);
            mimetype.setSize(20);
            mimetype.setCompressedSize(20);
            mimetype.setCrc(0x2cab616f); // CRC for "application/epub+zip"
            zos.putNextEntry(mimetype);
            zos.write("application/epub+zip".getBytes());
            zos.closeEntry();

            // META-INF/container.xml
            zos.putNextEntry(new ZipEntry("META-INF/container.xml"));
            String containerXml = "<?xml version=\"1.0\"?>\n" +
                    "<container version=\"1.0\" xmlns=\"urn:oasis:names:tc:opendocument:xmlns:container\">\n" +
                    "   <rootfiles>\n" +
                    "      <rootfile full-path=\"content.opf\" media-type=\"application/oebps-package+xml\"/>\n" +
                    "   </rootfiles>\n" +
                    "</container>";
            zos.write(containerXml.getBytes());
            zos.closeEntry();

            // content.opf
            zos.putNextEntry(new ZipEntry("content.opf"));
            String opf = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                    "<package xmlns=\"http://www.idpf.org/2007/opf\" unique-identifier=\"BookId\" version=\"2.0\">\n" +
                    "   <metadata xmlns:dc=\"http://purl.org/dc/elements/1.1/\" xmlns:opf=\"http://www.idpf.org/2007/opf\">\n" +
                    "      <dc:title>Dummy Title</dc:title>\n" +
                    "   </metadata>\n" +
                    "</package>";
            zos.write(opf.getBytes());
            zos.closeEntry();
        }
    }

    private void createDummyCbz(File file) throws IOException {
        try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(file))) {
            ZipEntry entry = new ZipEntry("dummy.txt");
            zos.putNextEntry(entry);
            zos.write("dummy content".getBytes());
            zos.closeEntry();
        }
    }
}
