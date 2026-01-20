package com.adityachandel.booklore.service.metadata;

import com.adityachandel.booklore.model.MetadataUpdateContext;
import com.adityachandel.booklore.model.MetadataUpdateWrapper;
import com.adityachandel.booklore.model.dto.BookMetadata;
import com.adityachandel.booklore.model.dto.settings.AppSettings;
import com.adityachandel.booklore.model.dto.settings.MetadataPersistenceSettings;
import com.adityachandel.booklore.model.entity.*;
import com.adityachandel.booklore.model.enums.BookFileType;
import com.adityachandel.booklore.model.enums.MetadataReplaceMode;
import com.adityachandel.booklore.repository.*;
import com.adityachandel.booklore.service.appsettings.AppSettingService;
import com.adityachandel.booklore.service.file.FileMoveService;
import com.adityachandel.booklore.service.metadata.writer.MetadataWriterFactory;
import com.adityachandel.booklore.util.FileService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BookMetadataUpdaterTest {

    @Mock
    private AuthorRepository authorRepository;
    @Mock
    private CategoryRepository categoryRepository;
    @Mock
    private MoodRepository moodRepository;
    @Mock
    private TagRepository tagRepository;
    @Mock
    private BookRepository bookRepository;
    @Mock
    private FileService fileService;
    @Mock
    private MetadataMatchService metadataMatchService;
    @Mock
    private AppSettingService appSettingService;
    @Mock
    private MetadataWriterFactory metadataWriterFactory;
    @Mock
    private BookReviewUpdateService bookReviewUpdateService;
    @Mock
    private FileMoveService fileMoveService;

    @InjectMocks
    private BookMetadataUpdater bookMetadataUpdater;

    @BeforeEach
    void setUp() {
        AppSettings appSettings = new AppSettings();
        MetadataPersistenceSettings persistenceSettings = new MetadataPersistenceSettings();

        MetadataPersistenceSettings.SaveToOriginalFile saveToOriginalFile = Mockito.mock(MetadataPersistenceSettings.SaveToOriginalFile.class);
        when(saveToOriginalFile.isAnyFormatEnabled()).thenReturn(false);
        persistenceSettings.setSaveToOriginalFile(saveToOriginalFile);

        appSettings.setMetadataPersistenceSettings(persistenceSettings);
        when(appSettingService.getAppSettings()).thenReturn(appSettings);
    }

    @Test
    void setBookMetadata_withMergeTagsFalse_shouldReplaceTags() {
        BookEntity bookEntity = new BookEntity();
        bookEntity.setId(1L);
        BookMetadataEntity metadataEntity = new BookMetadataEntity();

        Set<TagEntity> existingTags = new HashSet<>();
        existingTags.add(TagEntity.builder().name("Tag1").build());
        existingTags.add(TagEntity.builder().name("Tag2").build());
        metadataEntity.setTags(existingTags);
        metadataEntity.setBook(bookEntity);
        bookEntity.setMetadata(metadataEntity);

        BookFileEntity primaryFile = new BookFileEntity();
        primaryFile.setBook(bookEntity);
        primaryFile.setBookType(BookFileType.EPUB);
        primaryFile.setBookFormat(true);
        primaryFile.setFileSubPath("sub");
        primaryFile.setFileName("file.epub");
        bookEntity.setBookFiles(List.of(primaryFile));

        BookMetadata newMetadata = new BookMetadata();
        newMetadata.setTags(Set.of("Tag1"));

        MetadataUpdateWrapper wrapper = MetadataUpdateWrapper.builder()
                .metadata(newMetadata)
                .build();

        MetadataUpdateContext context = MetadataUpdateContext.builder()
                .bookEntity(bookEntity)
                .metadataUpdateWrapper(wrapper)
                .mergeTags(false)
                .replaceMode(MetadataReplaceMode.REPLACE_ALL)
                .build();

        when(tagRepository.findByName("Tag1")).thenReturn(Optional.of(TagEntity.builder().name("Tag1").build()));

        bookMetadataUpdater.setBookMetadata(context);

        assertEquals(1, bookEntity.getMetadata().getTags().size());
        assertTrue(bookEntity.getMetadata().getTags().stream().anyMatch(t -> t.getName().equals("Tag1")));
        assertFalse(bookEntity.getMetadata().getTags().stream().anyMatch(t -> t.getName().equals("Tag2")));
    }

    @Test
    void setBookMetadata_withMergeTagsFalse_andEmptyIncomingSet_shouldClearAllTags() {
        BookEntity bookEntity = new BookEntity();
        bookEntity.setId(1L);
        BookMetadataEntity metadataEntity = new BookMetadataEntity();

        Set<TagEntity> existingTags = new HashSet<>();
        existingTags.add(TagEntity.builder().name("Tag1").build());
        existingTags.add(TagEntity.builder().name("Tag2").build());
        metadataEntity.setTags(existingTags);
        metadataEntity.setBook(bookEntity);
        bookEntity.setMetadata(metadataEntity);

        BookFileEntity primaryFile = new BookFileEntity();
        primaryFile.setBook(bookEntity);
        primaryFile.setBookType(BookFileType.EPUB);
        primaryFile.setBookFormat(true);
        primaryFile.setFileSubPath("sub");
        primaryFile.setFileName("file.epub");
        bookEntity.setBookFiles(List.of(primaryFile));

        BookMetadata newMetadata = new BookMetadata();
        newMetadata.setTags(Collections.emptySet());

        MetadataUpdateWrapper wrapper = MetadataUpdateWrapper.builder()
                .metadata(newMetadata)
                .build();

        MetadataUpdateContext context = MetadataUpdateContext.builder()
                .bookEntity(bookEntity)
                .metadataUpdateWrapper(wrapper)
                .mergeTags(false)
                .replaceMode(MetadataReplaceMode.REPLACE_ALL)
                .build();

        bookMetadataUpdater.setBookMetadata(context);

        assertTrue(bookEntity.getMetadata().getTags().isEmpty(), "All tags should be cleared when incoming set is empty");
    }

    @Test
    void setBookMetadata_withMergeTagsTrue_shouldMergeTags() {
        BookEntity bookEntity = new BookEntity();
        bookEntity.setId(1L);
        BookMetadataEntity metadataEntity = new BookMetadataEntity();

        Set<TagEntity> existingTags = new HashSet<>();
        existingTags.add(TagEntity.builder().name("Tag1").build());
        existingTags.add(TagEntity.builder().name("Tag2").build());
        metadataEntity.setTags(existingTags);
        metadataEntity.setBook(bookEntity);
        bookEntity.setMetadata(metadataEntity);

        BookFileEntity primaryFile = new BookFileEntity();
        primaryFile.setBook(bookEntity);
        primaryFile.setBookType(BookFileType.EPUB);
        primaryFile.setBookFormat(true);
        primaryFile.setFileSubPath("sub");
        primaryFile.setFileName("file.epub");
        bookEntity.setBookFiles(List.of(primaryFile));

        BookMetadata newMetadata = new BookMetadata();
        newMetadata.setTags(Set.of("Tag3"));

        MetadataUpdateWrapper wrapper = MetadataUpdateWrapper.builder()
                .metadata(newMetadata)
                .build();

        MetadataUpdateContext context = MetadataUpdateContext.builder()
                .bookEntity(bookEntity)
                .metadataUpdateWrapper(wrapper)
                .mergeTags(true)
                .replaceMode(MetadataReplaceMode.REPLACE_ALL)
                .build();

        when(tagRepository.findByName("Tag3")).thenReturn(Optional.of(TagEntity.builder().name("Tag3").build()));

        // Act
        bookMetadataUpdater.setBookMetadata(context);

        // Assert
        assertEquals(3, bookEntity.getMetadata().getTags().size()); // Tag1, Tag2, Tag3
        assertTrue(bookEntity.getMetadata().getTags().stream().anyMatch(t -> t.getName().equals("Tag1")));
        assertTrue(bookEntity.getMetadata().getTags().stream().anyMatch(t -> t.getName().equals("Tag2")));
        assertTrue(bookEntity.getMetadata().getTags().stream().anyMatch(t -> t.getName().equals("Tag3")));
    }

    @Test
    void setBookMetadata_withMergeMoodsFalse_shouldReplaceMoods() {
        BookEntity bookEntity = new BookEntity();
        bookEntity.setId(1L);
        BookMetadataEntity metadataEntity = new BookMetadataEntity();

        Set<MoodEntity> existingMoods = new HashSet<>();
        existingMoods.add(MoodEntity.builder().name("Mood1").build());
        existingMoods.add(MoodEntity.builder().name("Mood2").build());
        metadataEntity.setMoods(existingMoods);
        metadataEntity.setBook(bookEntity);
        bookEntity.setMetadata(metadataEntity);

        BookFileEntity primaryFile = new BookFileEntity();
        primaryFile.setBook(bookEntity);
        primaryFile.setBookType(BookFileType.EPUB);
        primaryFile.setBookFormat(true);
        primaryFile.setFileSubPath("sub");
        primaryFile.setFileName("file.epub");
        bookEntity.setBookFiles(List.of(primaryFile));

        BookMetadata newMetadata = new BookMetadata();
        newMetadata.setMoods(Set.of("Mood1"));

        MetadataUpdateWrapper wrapper = MetadataUpdateWrapper.builder()
                .metadata(newMetadata)
                .build();

        MetadataUpdateContext context = MetadataUpdateContext.builder()
                .bookEntity(bookEntity)
                .metadataUpdateWrapper(wrapper)
                .replaceMode(MetadataReplaceMode.REPLACE_ALL)
                .build();

        when(moodRepository.findByName("Mood1")).thenReturn(Optional.of(MoodEntity.builder().name("Mood1").build()));

        bookMetadataUpdater.setBookMetadata(context);

        // Assert
        assertEquals(1, bookEntity.getMetadata().getMoods().size());
        assertTrue(bookEntity.getMetadata().getMoods().stream().anyMatch(m -> m.getName().equals("Mood1")));
        assertFalse(bookEntity.getMetadata().getMoods().stream().anyMatch(m -> m.getName().equals("Mood2")));
    }

    @Test
    void setBookMetadata_withMergeMoodsFalse_andEmptyIncomingSet_shouldClearAllMoods() {
        BookEntity bookEntity = new BookEntity();
        bookEntity.setId(1L);
        BookMetadataEntity metadataEntity = new BookMetadataEntity();

        Set<MoodEntity> existingMoods = new HashSet<>();
        existingMoods.add(MoodEntity.builder().name("Mood1").build());
        existingMoods.add(MoodEntity.builder().name("Mood2").build());
        metadataEntity.setMoods(existingMoods);
        metadataEntity.setBook(bookEntity);
        bookEntity.setMetadata(metadataEntity);

        BookFileEntity primaryFile = new BookFileEntity();
        primaryFile.setBook(bookEntity);
        primaryFile.setBookType(BookFileType.EPUB);
        primaryFile.setBookFormat(true);
        primaryFile.setFileSubPath("sub");
        primaryFile.setFileName("file.epub");
        bookEntity.setBookFiles(List.of(primaryFile));

        BookMetadata newMetadata = new BookMetadata();
        newMetadata.setMoods(Collections.emptySet());

        MetadataUpdateWrapper wrapper = MetadataUpdateWrapper.builder()
                .metadata(newMetadata)
                .build();

        MetadataUpdateContext context = MetadataUpdateContext.builder()
                .bookEntity(bookEntity)
                .metadataUpdateWrapper(wrapper)
                .replaceMode(MetadataReplaceMode.REPLACE_ALL)
                .build();

        bookMetadataUpdater.setBookMetadata(context);

        assertTrue(bookEntity.getMetadata().getMoods().isEmpty(), "All moods should be cleared when incoming set is empty");
    }

    @Test
    void setBookMetadata_withMergeMoodsTrue_shouldMergeMoods() {
        BookEntity bookEntity = new BookEntity();
        bookEntity.setId(1L);
        BookMetadataEntity metadataEntity = new BookMetadataEntity();

        Set<MoodEntity> existingMoods = new HashSet<>();
        existingMoods.add(MoodEntity.builder().name("Mood1").build());
        existingMoods.add(MoodEntity.builder().name("Mood2").build());
        metadataEntity.setMoods(existingMoods);
        metadataEntity.setBook(bookEntity);
        bookEntity.setMetadata(metadataEntity);

        BookFileEntity primaryFile = new BookFileEntity();
        primaryFile.setBook(bookEntity);
        primaryFile.setBookType(BookFileType.EPUB);
        primaryFile.setBookFormat(true);
        primaryFile.setFileSubPath("sub");
        primaryFile.setFileName("file.epub");
        bookEntity.setBookFiles(List.of(primaryFile));

        BookMetadata newMetadata = new BookMetadata();
        newMetadata.setMoods(Set.of("Mood3"));

        MetadataUpdateWrapper wrapper = MetadataUpdateWrapper.builder()
                .metadata(newMetadata)
                .build();

        MetadataUpdateContext context = MetadataUpdateContext.builder()
                .bookEntity(bookEntity)
                .metadataUpdateWrapper(wrapper)
                .mergeMoods(true)
                .replaceMode(MetadataReplaceMode.REPLACE_ALL)
                .build();

        when(moodRepository.findByName("Mood3")).thenReturn(Optional.of(MoodEntity.builder().name("Mood3").build()));

        bookMetadataUpdater.setBookMetadata(context);

        assertEquals(3, bookEntity.getMetadata().getMoods().size()); // Mood1, Mood2, Mood3
        assertTrue(bookEntity.getMetadata().getMoods().stream().anyMatch(m -> m.getName().equals("Mood1")));
        assertTrue(bookEntity.getMetadata().getMoods().stream().anyMatch(m -> m.getName().equals("Mood2")));
        assertTrue(bookEntity.getMetadata().getMoods().stream().anyMatch(m -> m.getName().equals("Mood3")));
    }

    @Test
    void setBookMetadata_withLockField_shouldUpdateAndLock() {
        BookEntity bookEntity = new BookEntity();
        bookEntity.setId(1L);

        BookMetadataEntity metadataEntity = new BookMetadataEntity();
        metadataEntity.setTitle("Old Title");
        metadataEntity.setTitleLocked(false);
        metadataEntity.setBook(bookEntity);
        bookEntity.setMetadata(metadataEntity);

        BookFileEntity primaryFile = new BookFileEntity();
        primaryFile.setBook(bookEntity);
        primaryFile.setBookType(BookFileType.EPUB);
        primaryFile.setBookFormat(true);
        primaryFile.setFileSubPath("sub");
        primaryFile.setFileName("file.epub");
        bookEntity.setBookFiles(List.of(primaryFile));

        BookMetadata newMetadata = new BookMetadata();
        newMetadata.setTitle("New Title");
        newMetadata.setTitleLocked(true);

        MetadataUpdateWrapper wrapper = MetadataUpdateWrapper.builder()
                .metadata(newMetadata)
                .build();

        MetadataUpdateContext context = MetadataUpdateContext.builder()
                .bookEntity(bookEntity)
                .metadataUpdateWrapper(wrapper)
                .replaceMode(MetadataReplaceMode.REPLACE_ALL)
                .build();

        bookMetadataUpdater.setBookMetadata(context);

        assertEquals("New Title", bookEntity.getMetadata().getTitle());
        assertTrue(bookEntity.getMetadata().getTitleLocked());
    }

    @Test
    void testUpdateAuthors_WithMergeFalse_ShouldReplaceAuthors() {
        // Setup existing book with "Old Author"
        BookEntity bookEntity = new BookEntity();
        bookEntity.setId(1L);
        BookMetadataEntity metadataEntity = new BookMetadataEntity();
        metadataEntity.setBook(bookEntity);
        bookEntity.setMetadata(metadataEntity);

        Set<com.adityachandel.booklore.model.entity.AuthorEntity> existingAuthors = new HashSet<>();
        existingAuthors.add(com.adityachandel.booklore.model.entity.AuthorEntity.builder().name("Old Author").build());
        metadataEntity.setAuthors(existingAuthors);

        BookFileEntity primaryFile = new BookFileEntity();
        primaryFile.setBook(bookEntity);
        primaryFile.setBookType(BookFileType.EPUB);
        primaryFile.setBookFormat(true);
        primaryFile.setFileSubPath("sub");
        primaryFile.setFileName("file.epub");
        bookEntity.setBookFiles(List.of(primaryFile));

        // New metadata with "New Author" only
        BookMetadata newMetadata = new BookMetadata();
        newMetadata.setAuthors(Set.of("New Author"));

        MetadataUpdateWrapper wrapper = MetadataUpdateWrapper.builder()
                .metadata(newMetadata)
                .build();

        // Update with mergeCategories = false
        MetadataUpdateContext context = MetadataUpdateContext.builder()
                .bookEntity(bookEntity)
                .metadataUpdateWrapper(wrapper)
                .mergeCategories(false)
                .replaceMode(MetadataReplaceMode.REPLACE_ALL)
                .build();

        when(authorRepository.findByName("New Author")).thenReturn(Optional.of(com.adityachandel.booklore.model.entity.AuthorEntity.builder().name("New Author").build()));

        bookMetadataUpdater.setBookMetadata(context);

        // Verify authors are replaced
        Set<com.adityachandel.booklore.model.entity.AuthorEntity> authors = bookEntity.getMetadata().getAuthors();
        assertEquals(1, authors.size());
        assertTrue(authors.stream().anyMatch(a -> a.getName().equals("New Author")));
        assertFalse(authors.stream().anyMatch(a -> a.getName().equals("Old Author")));
    }

    @Test
    void testUpdateCategories_WithMergeFalse_ShouldReplaceCategories() {
        // Setup existing book with "Old Category"
        BookEntity bookEntity = new BookEntity();
        bookEntity.setId(1L);
        BookMetadataEntity metadataEntity = new BookMetadataEntity();
        metadataEntity.setBook(bookEntity);
        bookEntity.setMetadata(metadataEntity);

        Set<com.adityachandel.booklore.model.entity.CategoryEntity> existingCategories = new HashSet<>();
        existingCategories.add(com.adityachandel.booklore.model.entity.CategoryEntity.builder().name("Old Category").build());
        metadataEntity.setCategories(existingCategories);

        BookFileEntity primaryFile = new BookFileEntity();
        primaryFile.setBook(bookEntity);
        primaryFile.setBookType(BookFileType.EPUB);
        primaryFile.setBookFormat(true);
        primaryFile.setFileSubPath("sub");
        primaryFile.setFileName("file.epub");
        bookEntity.setBookFiles(List.of(primaryFile));

        // New metadata with "New Category" only
        BookMetadata newMetadata = new BookMetadata();
        newMetadata.setCategories(Set.of("New Category"));

        MetadataUpdateWrapper wrapper = MetadataUpdateWrapper.builder()
                .metadata(newMetadata)
                .build();

        // Update with mergeCategories = false
        MetadataUpdateContext context = MetadataUpdateContext.builder()
                .bookEntity(bookEntity)
                .metadataUpdateWrapper(wrapper)
                .mergeCategories(false)
                .replaceMode(MetadataReplaceMode.REPLACE_ALL)
                .build();

        when(categoryRepository.findByName("New Category")).thenReturn(Optional.of(com.adityachandel.booklore.model.entity.CategoryEntity.builder().name("New Category").build()));

        bookMetadataUpdater.setBookMetadata(context);

        // Verify categories are replaced
        Set<com.adityachandel.booklore.model.entity.CategoryEntity> categories = bookEntity.getMetadata().getCategories();
        assertEquals(1, categories.size());
        assertTrue(categories.stream().anyMatch(c -> c.getName().equals("New Category")));
        assertFalse(categories.stream().anyMatch(c -> c.getName().equals("Old Category")));
    }
@Test
void setBookMetadata_shouldUseLocalCoverFile_whenAvailable() throws java.io.IOException {
    BookEntity bookEntity = new BookEntity();
    bookEntity.setId(1L);
    BookMetadataEntity metadataEntity = new BookMetadataEntity();
    metadataEntity.setBook(bookEntity);
    bookEntity.setMetadata(metadataEntity);

    BookFileEntity primaryFile = new BookFileEntity();
    primaryFile.setBook(bookEntity);
    primaryFile.setBookType(BookFileType.EPUB);
    primaryFile.setBookFormat(true);
    primaryFile.setFileSubPath("sub");
    primaryFile.setFileName("file.epub");

    java.nio.file.Path tempBookFile = java.nio.file.Files.createTempFile("test_book", ".epub");
    LibraryPathEntity libraryPathEntity = new LibraryPathEntity();
    libraryPathEntity.setPath(tempBookFile.getParent().toString());
    bookEntity.setLibraryPath(libraryPathEntity);

    primaryFile.setFileSubPath("");
    primaryFile.setFileName(tempBookFile.getFileName().toString());
    bookEntity.setBookFiles(List.of(primaryFile));

    BookMetadata newMetadata = new BookMetadata();
    newMetadata.setThumbnailUrl("http://8.8.8.8/image.jpg");

    MetadataUpdateWrapper wrapper = MetadataUpdateWrapper.builder()
            .metadata(newMetadata)
            .build();

    MetadataUpdateContext context = MetadataUpdateContext.builder()
            .bookEntity(bookEntity)
            .metadataUpdateWrapper(wrapper)
            .updateThumbnail(true)
            .replaceMode(MetadataReplaceMode.REPLACE_ALL)
            .build();

    AppSettings appSettings = appSettingService.getAppSettings();
    MetadataPersistenceSettings persistenceSettings = appSettings.getMetadataPersistenceSettings();
    MetadataPersistenceSettings.SaveToOriginalFile saveToOriginalFile = persistenceSettings.getSaveToOriginalFile();
    when(saveToOriginalFile.isAnyFormatEnabled()).thenReturn(true);

    com.adityachandel.booklore.service.metadata.writer.MetadataWriter writer = Mockito.mock(com.adityachandel.booklore.service.metadata.writer.MetadataWriter.class);
    when(metadataWriterFactory.getWriter(BookFileType.EPUB)).thenReturn(Optional.of(writer));

    java.nio.file.Path tempCover = java.nio.file.Files.createTempFile("cover", ".jpg");
    when(fileService.getCoverFile(1L)).thenReturn(tempCover.toString());
    when(fileService.createThumbnailFromUrl(Mockito.eq(1L), Mockito.anyString())).thenReturn(true);

    bookMetadataUpdater.setBookMetadata(context);

    Mockito.verify(writer).saveMetadataToFile(
            Mockito.any(java.io.File.class),
            Mockito.eq(metadataEntity),
            Mockito.eq(tempCover.toString()),
            Mockito.any()
    );

    java.nio.file.Files.deleteIfExists(tempCover);
    java.nio.file.Files.deleteIfExists(tempBookFile);
}

@Test
void setBookMetadata_withReplaceAllMode_shouldReplaceExistingTitle() {
    // Bug test: When user triggers metadata refresh, the title should be replaced
    // even if the book already has a title (like "V for Vendetta 03 (1988) (c2c) (theProletariat-DCP)")

    BookEntity bookEntity = new BookEntity();
    bookEntity.setId(1L);
    BookMetadataEntity metadataEntity = new BookMetadataEntity();
    metadataEntity.setTitle("V for Vendetta 03 (1988) (c2c) (theProletariat-DCP)"); // Existing file-based title
    metadataEntity.setTitleLocked(false);
    metadataEntity.setBook(bookEntity);
    bookEntity.setMetadata(metadataEntity);

    BookFileEntity primaryFile = new BookFileEntity();
    primaryFile.setBook(bookEntity);
    primaryFile.setBookType(BookFileType.EPUB);
    primaryFile.setBookFormat(true);
    primaryFile.setFileSubPath("sub");
    primaryFile.setFileName("file.epub");
    bookEntity.setBookFiles(List.of(primaryFile));

    BookMetadata newMetadata = new BookMetadata();
    newMetadata.setTitle("V for Vendetta #3"); // Fetched correct title

    MetadataUpdateWrapper wrapper = MetadataUpdateWrapper.builder()
            .metadata(newMetadata)
            .build();

    MetadataUpdateContext context = MetadataUpdateContext.builder()
            .bookEntity(bookEntity)
            .metadataUpdateWrapper(wrapper)
            .replaceMode(MetadataReplaceMode.REPLACE_ALL)
            .build();

    bookMetadataUpdater.setBookMetadata(context);

    assertEquals("V for Vendetta #3", bookEntity.getMetadata().getTitle(),
            "Title should be replaced when using REPLACE_ALL mode");
}

    @Test
    void setBookMetadata_withReplaceMissingMode_shouldNotReplaceExistingTitle() {
        // This test verifies the old behavior that was causing the bug
        // REPLACE_MISSING mode should NOT replace existing title

        BookEntity bookEntity = new BookEntity();
        bookEntity.setId(1L);
        BookMetadataEntity metadataEntity = new BookMetadataEntity();
        metadataEntity.setTitle("Existing Title"); // Already has a title
        metadataEntity.setTitleLocked(false);
        metadataEntity.setBook(bookEntity);
        bookEntity.setMetadata(metadataEntity);

        BookFileEntity primaryFile = new BookFileEntity();
        primaryFile.setBook(bookEntity);
        primaryFile.setBookType(BookFileType.EPUB);
        primaryFile.setBookFormat(true);
        primaryFile.setFileSubPath("sub");
        primaryFile.setFileName("file.epub");
        bookEntity.setBookFiles(List.of(primaryFile));

        BookMetadata newMetadata = new BookMetadata();
        newMetadata.setTitle("New Title"); // Fetched title

        MetadataUpdateWrapper wrapper = MetadataUpdateWrapper.builder()
                .metadata(newMetadata)
                .build();

        MetadataUpdateContext context = MetadataUpdateContext.builder()
                .bookEntity(bookEntity)
                .metadataUpdateWrapper(wrapper)
                .replaceMode(MetadataReplaceMode.REPLACE_MISSING)
                .build();

        bookMetadataUpdater.setBookMetadata(context);

        assertEquals("Existing Title", bookEntity.getMetadata().getTitle(),
                "Title should NOT be replaced when using REPLACE_MISSING mode and title exists");
    }

    @Test
    void setBookMetadata_shouldNotWriteFile_whenCoverNotUpdatedAndNoMetadataChanges() {
        BookEntity bookEntity = new BookEntity();
        bookEntity.setId(1L);
        BookMetadataEntity metadataEntity = new BookMetadataEntity();
        metadataEntity.setTitle("Title");
        metadataEntity.setBook(bookEntity);
        bookEntity.setMetadata(metadataEntity);

        BookFileEntity primaryFile = new BookFileEntity();
        primaryFile.setBook(bookEntity);
        primaryFile.setBookType(BookFileType.EPUB);
        primaryFile.setBookFormat(true);
        primaryFile.setFileSubPath("sub");
        primaryFile.setFileName("file.epub");
        bookEntity.setBookFiles(List.of(primaryFile));
        LibraryPathEntity libraryPathEntity = new LibraryPathEntity();
        libraryPathEntity.setPath("/tmp");
        bookEntity.setLibraryPath(libraryPathEntity);

        BookMetadata newMetadata = new BookMetadata();
        newMetadata.setTitle("Title"); // Same title
        newMetadata.setThumbnailUrl("http://example.com/image.jpg");

        MetadataUpdateWrapper wrapper = MetadataUpdateWrapper.builder()
                .metadata(newMetadata)
                .build();

        MetadataUpdateContext context = MetadataUpdateContext.builder()
                .bookEntity(bookEntity)
                .metadataUpdateWrapper(wrapper)
                .updateThumbnail(true)
                .replaceMode(MetadataReplaceMode.REPLACE_ALL)
                .build();

        // App settings allow writing
        AppSettings appSettings = appSettingService.getAppSettings();
        MetadataPersistenceSettings persistenceSettings = appSettings.getMetadataPersistenceSettings();
        MetadataPersistenceSettings.SaveToOriginalFile saveToOriginalFile = persistenceSettings.getSaveToOriginalFile();
        when(saveToOriginalFile.isAnyFormatEnabled()).thenReturn(true);

        // Mock fileService to return false (not updated)
        when(fileService.createThumbnailFromUrl(Mockito.eq(1L), Mockito.anyString())).thenReturn(false);

        bookMetadataUpdater.setBookMetadata(context);

        // Verify writer was NOT called
        Mockito.verify(metadataWriterFactory, Mockito.never()).getWriter(Mockito.any());
    }

    @Test
    void setBookMetadata_shouldWriteFile_whenCoverUpdated() {
        BookEntity bookEntity = new BookEntity();
        bookEntity.setId(1L);
        BookMetadataEntity metadataEntity = new BookMetadataEntity();
        metadataEntity.setTitle("Title");
        metadataEntity.setBook(bookEntity);
        bookEntity.setMetadata(metadataEntity);

        BookFileEntity primaryFile = new BookFileEntity();
        primaryFile.setBook(bookEntity);
        primaryFile.setBookType(BookFileType.EPUB);
        primaryFile.setBookFormat(true);
        primaryFile.setFileSubPath("sub");
        primaryFile.setFileName("file.epub");
        bookEntity.setBookFiles(List.of(primaryFile));
        LibraryPathEntity libraryPathEntity = new LibraryPathEntity();
        libraryPathEntity.setPath("/tmp");
        bookEntity.setLibraryPath(libraryPathEntity);

        BookMetadata newMetadata = new BookMetadata();
        newMetadata.setTitle("Title"); // Same title
        newMetadata.setThumbnailUrl("http://example.com/image.jpg");

        MetadataUpdateWrapper wrapper = MetadataUpdateWrapper.builder()
                .metadata(newMetadata)
                .build();

        MetadataUpdateContext context = MetadataUpdateContext.builder()
                .bookEntity(bookEntity)
                .metadataUpdateWrapper(wrapper)
                .updateThumbnail(true)
                .replaceMode(MetadataReplaceMode.REPLACE_ALL)
                .build();

        // App settings allow writing
        AppSettings appSettings = appSettingService.getAppSettings();
        MetadataPersistenceSettings persistenceSettings = appSettings.getMetadataPersistenceSettings();
        MetadataPersistenceSettings.SaveToOriginalFile saveToOriginalFile = persistenceSettings.getSaveToOriginalFile();
        when(saveToOriginalFile.isAnyFormatEnabled()).thenReturn(true);

        // Mock fileService to return true (updated)
        when(fileService.createThumbnailFromUrl(Mockito.eq(1L), Mockito.anyString())).thenReturn(true);
        when(fileService.getCoverFile(1L)).thenReturn("/tmp/cover.jpg");
        
        com.adityachandel.booklore.service.metadata.writer.MetadataWriter writer = Mockito.mock(com.adityachandel.booklore.service.metadata.writer.MetadataWriter.class);
        when(metadataWriterFactory.getWriter(BookFileType.EPUB)).thenReturn(Optional.of(writer));

        bookMetadataUpdater.setBookMetadata(context);

        // Verify writer WAS called
        Mockito.verify(writer).saveMetadataToFile(Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any());
    }
}
