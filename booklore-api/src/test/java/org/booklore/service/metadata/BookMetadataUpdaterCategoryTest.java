package org.booklore.service.metadata;

import org.booklore.model.MetadataClearFlags;
import org.booklore.model.MetadataUpdateContext;
import org.booklore.model.MetadataUpdateWrapper;
import org.booklore.model.dto.BookMetadata;
import org.booklore.model.dto.settings.AppSettings;
import org.booklore.model.dto.settings.MetadataPersistenceSettings;
import org.booklore.model.entity.BookEntity;
import org.booklore.model.entity.BookFileEntity;
import org.booklore.model.entity.BookMetadataEntity;
import org.booklore.model.entity.CategoryEntity;
import org.booklore.model.enums.BookFileType;
import org.booklore.model.enums.MetadataReplaceMode;
import org.booklore.repository.*;
import org.booklore.service.appsettings.AppSettingService;
import org.booklore.service.file.FileMoveService;
import org.booklore.service.metadata.writer.MetadataWriterFactory;
import org.booklore.util.FileService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BookMetadataUpdaterCategoryTest {

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
        lenient().when(appSettingService.getAppSettings()).thenReturn(appSettings);

        lenient().when(categoryRepository.findByName(anyString())).thenAnswer(invocation -> {
            String name = invocation.getArgument(0);
            return Optional.of(CategoryEntity.builder().name(name).build());
        });

        lenient().when(categoryRepository.save(any(CategoryEntity.class))).thenAnswer(invocation ->
                invocation.getArgument(0));
    }

    @Test
    void replaceAll_withMergeFalse_shouldClearAndAddNew() {
        BookEntity bookEntity = createBookWithCategories("Old1", "Old2");
        BookMetadata newMetadata = new BookMetadata();
        newMetadata.setCategories(Set.of("New1"));

        MetadataUpdateContext context = createContext(bookEntity, newMetadata, false, MetadataReplaceMode.REPLACE_ALL);

        bookMetadataUpdater.setBookMetadata(context);

        Set<CategoryEntity> categories = bookEntity.getMetadata().getCategories();
        assertEquals(1, categories.size());
        assertTrue(containsCategory(categories, "New1"));
        assertFalse(containsCategory(categories, "Old1"));
        assertFalse(containsCategory(categories, "Old2"));
    }

    @Test
    void replaceAll_withMergeTrue_shouldAddWithoutClearing() {
        BookEntity bookEntity = createBookWithCategories("Old1", "Old2");
        BookMetadata newMetadata = new BookMetadata();
        newMetadata.setCategories(Set.of("New1", "New2"));

        MetadataUpdateContext context = createContext(bookEntity, newMetadata, true, MetadataReplaceMode.REPLACE_ALL);

        bookMetadataUpdater.setBookMetadata(context);

        Set<CategoryEntity> categories = bookEntity.getMetadata().getCategories();
        assertEquals(4, categories.size());
        assertTrue(containsCategory(categories, "Old1"));
        assertTrue(containsCategory(categories, "Old2"));
        assertTrue(containsCategory(categories, "New1"));
        assertTrue(containsCategory(categories, "New2"));
    }

    @Test
    void replaceAll_withMergeFalse_andEmptyInput_shouldClearAll() {
        BookEntity bookEntity = createBookWithCategories("Old1", "Old2");
        BookMetadata newMetadata = new BookMetadata();
        newMetadata.setCategories(Set.of());

        MetadataUpdateContext context = createContext(bookEntity, newMetadata, false, MetadataReplaceMode.REPLACE_ALL);

        bookMetadataUpdater.setBookMetadata(context);

        assertTrue(bookEntity.getMetadata().getCategories().isEmpty());
    }

    @Test
    void replaceAll_withMergeTrue_andEmptyInput_shouldClearAll() {
        BookEntity bookEntity = createBookWithCategories("Old1", "Old2");
        BookMetadata newMetadata = new BookMetadata();
        newMetadata.setCategories(Set.of());

        MetadataUpdateContext context = createContext(bookEntity, newMetadata, true, MetadataReplaceMode.REPLACE_ALL);

        bookMetadataUpdater.setBookMetadata(context);

        assertTrue(bookEntity.getMetadata().getCategories().isEmpty());
    }

    @Test
    void replaceMissing_whenCategoriesEmpty_shouldAddNew() {
        BookEntity bookEntity = createBookWithCategories();
        BookMetadata newMetadata = new BookMetadata();
        newMetadata.setCategories(Set.of("New1", "New2"));

        MetadataUpdateContext context = createContext(bookEntity, newMetadata, false, MetadataReplaceMode.REPLACE_MISSING);

        bookMetadataUpdater.setBookMetadata(context);

        Set<CategoryEntity> categories = bookEntity.getMetadata().getCategories();
        assertEquals(2, categories.size());
        assertTrue(containsCategory(categories, "New1"));
        assertTrue(containsCategory(categories, "New2"));
    }

    @Test
    void replaceMissing_whenCategoriesExist_shouldNotChange() {
        BookEntity bookEntity = createBookWithCategories("Old1");
        BookMetadata newMetadata = new BookMetadata();
        newMetadata.setCategories(Set.of("New1"));

        MetadataUpdateContext context = createContext(bookEntity, newMetadata, false, MetadataReplaceMode.REPLACE_MISSING);

        bookMetadataUpdater.setBookMetadata(context);

        Set<CategoryEntity> categories = bookEntity.getMetadata().getCategories();
        assertEquals(1, categories.size());
        assertTrue(containsCategory(categories, "Old1"));
        assertFalse(containsCategory(categories, "New1"));
    }

    @Test
    void nullReplaceMode_withMergeFalse_shouldReplaceAll() {
        BookEntity bookEntity = createBookWithCategories("Old1", "Old2");
        BookMetadata newMetadata = new BookMetadata();
        newMetadata.setCategories(Set.of("New1"));

        MetadataUpdateContext context = createContext(bookEntity, newMetadata, false, null);

        bookMetadataUpdater.setBookMetadata(context);

        Set<CategoryEntity> categories = bookEntity.getMetadata().getCategories();
        assertEquals(1, categories.size());
        assertTrue(containsCategory(categories, "New1"));
    }

    @Test
    void nullReplaceMode_withMergeTrue_shouldAddToExisting() {
        BookEntity bookEntity = createBookWithCategories("Old1");
        BookMetadata newMetadata = new BookMetadata();
        newMetadata.setCategories(Set.of("New1"));

        MetadataUpdateContext context = createContext(bookEntity, newMetadata, true, null);

        bookMetadataUpdater.setBookMetadata(context);

        Set<CategoryEntity> categories = bookEntity.getMetadata().getCategories();
        assertEquals(2, categories.size());
        assertTrue(containsCategory(categories, "Old1"));
        assertTrue(containsCategory(categories, "New1"));
    }

    @Test
    void whenCategoriesLocked_shouldNotUpdate() {
        BookEntity bookEntity = createBookWithCategories("Old1");
        bookEntity.getMetadata().setCategoriesLocked(true);

        BookMetadata newMetadata = new BookMetadata();
        newMetadata.setCategories(Set.of("New1"));

        MetadataUpdateContext context = createContext(bookEntity, newMetadata, false, MetadataReplaceMode.REPLACE_ALL);

        bookMetadataUpdater.setBookMetadata(context);

        Set<CategoryEntity> categories = bookEntity.getMetadata().getCategories();
        assertEquals(1, categories.size());
        assertTrue(containsCategory(categories, "Old1"));
    }

    @Test
    void whenClearFlagSet_shouldClearCategories() {
        BookEntity bookEntity = createBookWithCategories("Old1", "Old2");
        BookMetadata newMetadata = new BookMetadata();
        newMetadata.setCategories(Set.of("New1"));

        MetadataClearFlags clearFlags = new MetadataClearFlags();
        clearFlags.setCategories(true);

        MetadataUpdateWrapper wrapper = MetadataUpdateWrapper.builder()
                .metadata(newMetadata)
                .clearFlags(clearFlags)
                .build();

        MetadataUpdateContext context = MetadataUpdateContext.builder()
                .bookEntity(bookEntity)
                .metadataUpdateWrapper(wrapper)
                .mergeCategories(false)
                .replaceMode(MetadataReplaceMode.REPLACE_ALL)
                .build();

        bookMetadataUpdater.setBookMetadata(context);

        assertTrue(bookEntity.getMetadata().getCategories().isEmpty());
    }

    @Test
    void whenInputHasBlankNames_shouldFilterThem() {
        BookEntity bookEntity = createBookWithCategories();
        BookMetadata newMetadata = new BookMetadata();

        Set<String> categoriesWithBlanks = new HashSet<>();
        categoriesWithBlanks.add("Valid");
        categoriesWithBlanks.add("");
        categoriesWithBlanks.add("  ");
        categoriesWithBlanks.add(null);
        newMetadata.setCategories(categoriesWithBlanks);

        MetadataUpdateContext context = createContext(bookEntity, newMetadata, false, MetadataReplaceMode.REPLACE_ALL);

        bookMetadataUpdater.setBookMetadata(context);

        Set<CategoryEntity> categories = bookEntity.getMetadata().getCategories();
        assertEquals(1, categories.size());
        assertTrue(containsCategory(categories, "Valid"));
    }

    @Test
    void whenNewCategoryDoesNotExist_shouldCreateIt() {
        BookEntity bookEntity = createBookWithCategories();
        BookMetadata newMetadata = new BookMetadata();
        newMetadata.setCategories(Set.of("BrandNew"));

        when(categoryRepository.findByName("BrandNew")).thenReturn(Optional.empty());
        when(categoryRepository.save(any(CategoryEntity.class))).thenAnswer(invocation -> {
            CategoryEntity saved = invocation.getArgument(0);
            saved.setId(999L);
            return saved;
        });

        MetadataUpdateContext context = createContext(bookEntity, newMetadata, false, MetadataReplaceMode.REPLACE_ALL);

        bookMetadataUpdater.setBookMetadata(context);

        verify(categoryRepository).save(any(CategoryEntity.class));
        Set<CategoryEntity> categories = bookEntity.getMetadata().getCategories();
        assertEquals(1, categories.size());
        assertTrue(containsCategory(categories, "BrandNew"));
    }

    @Test
    void bug_addingItemsWithEmptyArrayCausesDisappearance() {
        BookEntity bookEntity = createBookWithCategories("Fantasy", "SciFi", "Horror");

        BookMetadata newMetadata = new BookMetadata();
        newMetadata.setCategories(Set.of("Fantasy", "SciFi", "Mystery"));

        MetadataUpdateContext context = createContext(bookEntity, newMetadata, false, null);

        bookMetadataUpdater.setBookMetadata(context);

        Set<CategoryEntity> categories = bookEntity.getMetadata().getCategories();
        assertEquals(3, categories.size(), "Expected 3 categories but got " + categories.size());
        assertTrue(containsCategory(categories, "Fantasy"));
        assertTrue(containsCategory(categories, "SciFi"));
        assertTrue(containsCategory(categories, "Mystery"));
        assertFalse(containsCategory(categories, "Horror"), "BUG: Horror disappeared!");
    }

    @Test
    void bug_emptyArrayFromUIWithMergeFalseClearsEverything() {
        BookEntity bookEntity = createBookWithCategories("Fantasy", "SciFi");

        BookMetadata newMetadata = new BookMetadata();
        newMetadata.setCategories(Set.of());

        MetadataUpdateContext context = createContext(bookEntity, newMetadata, false, MetadataReplaceMode.REPLACE_ALL);

        bookMetadataUpdater.setBookMetadata(context);

        assertTrue(bookEntity.getMetadata().getCategories().isEmpty(),
                "BUG REPRODUCED: Empty array cleared all categories");
    }

    @Test
    void lockShouldPreventUpdateEvenWithEmptyArray() {
        BookEntity bookEntity = createBookWithCategories("Fantasy", "SciFi");
        bookEntity.getMetadata().setCategoriesLocked(true);

        BookMetadata newMetadata = new BookMetadata();
        newMetadata.setCategories(Set.of());

        MetadataUpdateContext context = createContext(bookEntity, newMetadata, false, MetadataReplaceMode.REPLACE_ALL);

        bookMetadataUpdater.setBookMetadata(context);

        Set<CategoryEntity> categories = bookEntity.getMetadata().getCategories();
        assertEquals(2, categories.size(), "Lock should prevent clearing");
        assertTrue(containsCategory(categories, "Fantasy"));
        assertTrue(containsCategory(categories, "SciFi"));
    }

    @Test
    void fixProposal_mergeTrueShouldPreserveExisting() {
        BookEntity bookEntity = createBookWithCategories("Fantasy", "SciFi", "Horror");

        BookMetadata newMetadata = new BookMetadata();
        newMetadata.setCategories(Set.of("Fantasy", "SciFi", "Mystery"));

        MetadataUpdateContext context = createContext(bookEntity, newMetadata, true, MetadataReplaceMode.REPLACE_ALL);

        bookMetadataUpdater.setBookMetadata(context);

        Set<CategoryEntity> categories = bookEntity.getMetadata().getCategories();
        assertTrue(categories.size() >= 3);
        assertTrue(containsCategory(categories, "Fantasy"));
        assertTrue(containsCategory(categories, "SciFi"));
        assertTrue(containsCategory(categories, "Mystery"));
    }

    @Test
    void fixProposal_uiShouldSendFullListOrNull() {
        BookEntity bookEntity = createBookWithCategories("Fantasy", "SciFi");

        BookMetadata newMetadata = new BookMetadata();
        newMetadata.setCategories(null);

        MetadataUpdateContext context = createContext(bookEntity, newMetadata, false, MetadataReplaceMode.REPLACE_ALL);

        bookMetadataUpdater.setBookMetadata(context);

        Set<CategoryEntity> categories = bookEntity.getMetadata().getCategories();
        assertTrue(categories.isEmpty());
    }


    private BookEntity createBookWithCategories(String... categoryNames) {
        BookEntity bookEntity = new BookEntity();
        bookEntity.setId(1L);

        BookMetadataEntity metadataEntity = new BookMetadataEntity();
        Set<CategoryEntity> categories = new HashSet<>();

        for (String name : categoryNames) {
            categories.add(CategoryEntity.builder().name(name).build());
        }

        metadataEntity.setCategories(categories);
        metadataEntity.setBook(bookEntity);
        bookEntity.setMetadata(metadataEntity);

        BookFileEntity primaryFile = new BookFileEntity();
        primaryFile.setBook(bookEntity);
        primaryFile.setBookType(BookFileType.EPUB);
        primaryFile.setBookFormat(true);
        primaryFile.setFileSubPath("sub");
        primaryFile.setFileName("file.epub");
        bookEntity.setBookFiles(List.of(primaryFile));

        return bookEntity;
    }

    private MetadataUpdateContext createContext(BookEntity bookEntity, BookMetadata newMetadata,
                                                boolean merge, MetadataReplaceMode replaceMode) {
        MetadataUpdateWrapper wrapper = MetadataUpdateWrapper.builder()
                .metadata(newMetadata)
                .build();

        return MetadataUpdateContext.builder()
                .bookEntity(bookEntity)
                .metadataUpdateWrapper(wrapper)
                .mergeCategories(merge)
                .replaceMode(replaceMode)
                .build();
    }

    private boolean containsCategory(Set<CategoryEntity> categories, String name) {
        return categories.stream().anyMatch(c -> c.getName().equals(name));
    }
}

