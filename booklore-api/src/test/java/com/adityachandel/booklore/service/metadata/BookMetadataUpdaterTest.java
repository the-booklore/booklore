package com.adityachandel.booklore.service.metadata;

import com.adityachandel.booklore.model.MetadataUpdateContext;
import com.adityachandel.booklore.model.MetadataUpdateWrapper;
import com.adityachandel.booklore.model.dto.BookMetadata;
import com.adityachandel.booklore.model.dto.settings.AppSettings;
import com.adityachandel.booklore.model.dto.settings.MetadataPersistenceSettings;
import com.adityachandel.booklore.model.entity.BookEntity;
import com.adityachandel.booklore.model.entity.BookMetadataEntity;
import com.adityachandel.booklore.model.entity.AuthorEntity;
import com.adityachandel.booklore.model.entity.CategoryEntity;
import com.adityachandel.booklore.model.entity.MoodEntity;
import com.adityachandel.booklore.model.entity.TagEntity;
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
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BookMetadataUpdaterTest {

    @Mock private AuthorRepository authorRepository;
    @Mock private CategoryRepository categoryRepository;
    @Mock private MoodRepository moodRepository;
    @Mock private TagRepository tagRepository;
    @Mock private BookRepository bookRepository;
    @Mock private FileService fileService;
    @Mock private MetadataMatchService metadataMatchService;
    @Mock private AppSettingService appSettingService;
    @Mock private MetadataWriterFactory metadataWriterFactory;
    @Mock private BookReviewUpdateService bookReviewUpdateService;
    @Mock private FileMoveService fileMoveService;

    @InjectMocks
    private BookMetadataUpdater bookMetadataUpdater;

    @BeforeEach
    void setUp() {
        AppSettings appSettings = new AppSettings();
        appSettings.setMetadataPersistenceSettings(new MetadataPersistenceSettings());
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
        bookEntity.setMetadata(metadataEntity);

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
        bookEntity.setMetadata(metadataEntity);

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
        bookEntity.setMetadata(metadataEntity);

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
        bookEntity.setMetadata(metadataEntity);

        BookMetadata newMetadata = new BookMetadata();
        newMetadata.setMoods(Set.of("Mood1"));

        MetadataUpdateWrapper wrapper = MetadataUpdateWrapper.builder()
                .metadata(newMetadata)
                .build();

        MetadataUpdateContext context = MetadataUpdateContext.builder()
                .bookEntity(bookEntity)
                .metadataUpdateWrapper(wrapper)
                .mergeMoods(false)
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
        bookEntity.setMetadata(metadataEntity);

        BookMetadata newMetadata = new BookMetadata();
        newMetadata.setMoods(Collections.emptySet());

        MetadataUpdateWrapper wrapper = MetadataUpdateWrapper.builder()
                .metadata(newMetadata)
                .build();

        MetadataUpdateContext context = MetadataUpdateContext.builder()
                .bookEntity(bookEntity)
                .metadataUpdateWrapper(wrapper)
                .mergeMoods(false)
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
        bookEntity.setMetadata(metadataEntity);

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
    void setBookMetadata_withThreeOrMoreNewTags_shouldPreserveAllTags() {
        BookEntity bookEntity = new BookEntity();
        bookEntity.setId(1L);
        BookMetadataEntity metadataEntity = new BookMetadataEntity();
        metadataEntity.setTags(new HashSet<>());
        bookEntity.setMetadata(metadataEntity);

        BookMetadata newMetadata = new BookMetadata();
        newMetadata.setTags(Set.of("Tag1", "Tag2", "Tag3"));

        MetadataUpdateWrapper wrapper = MetadataUpdateWrapper.builder()
                .metadata(newMetadata)
                .build();

        MetadataUpdateContext context = MetadataUpdateContext.builder()
                .bookEntity(bookEntity)
                .metadataUpdateWrapper(wrapper)
                .mergeTags(false)
                .replaceMode(MetadataReplaceMode.REPLACE_ALL)
                .build();

        when(tagRepository.findByName("Tag1")).thenReturn(Optional.of(TagEntity.builder().id(1L).name("Tag1").build()));
        when(tagRepository.findByName("Tag2")).thenReturn(Optional.of(TagEntity.builder().id(2L).name("Tag2").build()));
        when(tagRepository.findByName("Tag3")).thenReturn(Optional.of(TagEntity.builder().id(3L).name("Tag3").build()));

        bookMetadataUpdater.setBookMetadata(context);

        Set<TagEntity> resultTags = bookEntity.getMetadata().getTags();
        assertEquals(3, resultTags.size(), "All 3 tags should be preserved");
        
        Set<String> tagNames = resultTags.stream().map(TagEntity::getName).collect(Collectors.toSet());
        assertTrue(tagNames.contains("Tag1"), "Tag1 should be present");
        assertTrue(tagNames.contains("Tag2"), "Tag2 should be present");
        assertTrue(tagNames.contains("Tag3"), "Tag3 should be present");
    }

    @Test
    void setBookMetadata_withThreeOrMoreNewAuthors_shouldPreserveAllAuthors() {
        BookEntity bookEntity = new BookEntity();
        bookEntity.setId(1L);
        BookMetadataEntity metadataEntity = new BookMetadataEntity();
        metadataEntity.setAuthors(new HashSet<>());
        bookEntity.setMetadata(metadataEntity);

        BookMetadata newMetadata = new BookMetadata();
        newMetadata.setAuthors(Set.of("Author1", "Author2", "Author3", "Author4"));

        MetadataUpdateWrapper wrapper = MetadataUpdateWrapper.builder()
                .metadata(newMetadata)
                .build();

        MetadataUpdateContext context = MetadataUpdateContext.builder()
                .bookEntity(bookEntity)
                .metadataUpdateWrapper(wrapper)
                .mergeCategories(false) // Note: mergeCategories flag also controls author merging in updater
                .replaceMode(MetadataReplaceMode.REPLACE_ALL)
                .build();

        when(authorRepository.findByName("Author1")).thenReturn(Optional.of(AuthorEntity.builder().id(1L).name("Author1").build()));
        when(authorRepository.findByName("Author2")).thenReturn(Optional.of(AuthorEntity.builder().id(2L).name("Author2").build()));
        when(authorRepository.findByName("Author3")).thenReturn(Optional.of(AuthorEntity.builder().id(3L).name("Author3").build()));
        when(authorRepository.findByName("Author4")).thenReturn(Optional.of(AuthorEntity.builder().id(4L).name("Author4").build()));

        bookMetadataUpdater.setBookMetadata(context);

        Set<AuthorEntity> resultAuthors = bookEntity.getMetadata().getAuthors();
        assertEquals(4, resultAuthors.size(), "All 4 authors should be preserved");
        
        Set<String> authorNames = resultAuthors.stream().map(AuthorEntity::getName).collect(Collectors.toSet());
        assertTrue(authorNames.contains("Author1"), "Author1 should be present");
        assertTrue(authorNames.contains("Author2"), "Author2 should be present");
        assertTrue(authorNames.contains("Author3"), "Author3 should be present");
        assertTrue(authorNames.contains("Author4"), "Author4 should be present");
    }

    @Test
    void setBookMetadata_withThreeOrMoreNewCategories_shouldPreserveAllCategories() {
        BookEntity bookEntity = new BookEntity();
        bookEntity.setId(1L);
        BookMetadataEntity metadataEntity = new BookMetadataEntity();
        metadataEntity.setCategories(new HashSet<>());
        bookEntity.setMetadata(metadataEntity);

        BookMetadata newMetadata = new BookMetadata();
        newMetadata.setCategories(Set.of("Category1", "Category2", "Category3"));

        MetadataUpdateWrapper wrapper = MetadataUpdateWrapper.builder()
                .metadata(newMetadata)
                .build();

        MetadataUpdateContext context = MetadataUpdateContext.builder()
                .bookEntity(bookEntity)
                .metadataUpdateWrapper(wrapper)
                .mergeCategories(false)
                .replaceMode(MetadataReplaceMode.REPLACE_ALL)
                .build();

        when(categoryRepository.findByName("Category1")).thenReturn(Optional.of(com.adityachandel.booklore.model.entity.CategoryEntity.builder().id(1L).name("Category1").build()));
        when(categoryRepository.findByName("Category2")).thenReturn(Optional.of(com.adityachandel.booklore.model.entity.CategoryEntity.builder().id(2L).name("Category2").build()));
        when(categoryRepository.findByName("Category3")).thenReturn(Optional.of(com.adityachandel.booklore.model.entity.CategoryEntity.builder().id(3L).name("Category3").build()));

        bookMetadataUpdater.setBookMetadata(context);
        Set<CategoryEntity> resultCategories = bookEntity.getMetadata().getCategories();
        assertEquals(3, resultCategories.size(), "All 3 categories should be preserved");
        
        Set<String> categoryNames = resultCategories.stream().map(CategoryEntity::getName).collect(Collectors.toSet());
        assertTrue(categoryNames.contains("Category1"), "Category1 should be present");
        assertTrue(categoryNames.contains("Category2"), "Category2 should be present");
        assertTrue(categoryNames.contains("Category3"), "Category3 should be present");
    }

    @Test
    void setBookMetadata_withNewTagsCreatedOnTheFly_shouldPreserveAllTags() {
        BookEntity bookEntity = new BookEntity();
        bookEntity.setId(1L);
        BookMetadataEntity metadataEntity = new BookMetadataEntity();
        metadataEntity.setTags(new HashSet<>());
        bookEntity.setMetadata(metadataEntity);

        BookMetadata newMetadata = new BookMetadata();
        newMetadata.setTags(Set.of("NewTag1", "NewTag2", "NewTag3"));

        MetadataUpdateWrapper wrapper = MetadataUpdateWrapper.builder()
                .metadata(newMetadata)
                .build();

        MetadataUpdateContext context = MetadataUpdateContext.builder()
                .bookEntity(bookEntity)
                .metadataUpdateWrapper(wrapper)
                .mergeTags(false)
                .replaceMode(MetadataReplaceMode.REPLACE_ALL)
                .build();

        // Tags not found - will be created with save()
        when(tagRepository.findByName("NewTag1")).thenReturn(Optional.empty());
        when(tagRepository.findByName("NewTag2")).thenReturn(Optional.empty());
        when(tagRepository.findByName("NewTag3")).thenReturn(Optional.empty());
        
        when(tagRepository.save(org.mockito.ArgumentMatchers.any(TagEntity.class)))
                .thenAnswer(invocation -> {
                    TagEntity tag = invocation.getArgument(0);
                    String tagName = tag.getName();
                    return TagEntity.builder()
                            .id(tagName.hashCode() & 0xFFFFFFFFL) // Generate ID based on name
                            .name(tagName)
                            .build();
                });

        bookMetadataUpdater.setBookMetadata(context);

        Set<TagEntity> resultTags = bookEntity.getMetadata().getTags();
        assertEquals(3, resultTags.size(), "All 3 new tags should be preserved");
        
        Set<String> tagNames = resultTags.stream().map(TagEntity::getName).collect(Collectors.toSet());
        assertTrue(tagNames.contains("NewTag1"), "NewTag1 should be present");
        assertTrue(tagNames.contains("NewTag2"), "NewTag2 should be present");
        assertTrue(tagNames.contains("NewTag3"), "NewTag3 should be present");
    }

    @Test
    void setBookMetadata_withMixedExistingAndNewTags_shouldPreserveAll() {
        BookEntity bookEntity = new BookEntity();
        bookEntity.setId(1L);
        BookMetadataEntity metadataEntity = new BookMetadataEntity();
        
        Set<TagEntity> existingTags = new HashSet<>();
        existingTags.add(TagEntity.builder().id(100L).name("ExistingTag").build());
        metadataEntity.setTags(existingTags);
        bookEntity.setMetadata(metadataEntity);

        BookMetadata newMetadata = new BookMetadata();
        newMetadata.setTags(Set.of("ExistingTag", "NewTag1", "NewTag2", "NewTag3"));

        MetadataUpdateWrapper wrapper = MetadataUpdateWrapper.builder()
                .metadata(newMetadata)
                .build();

        MetadataUpdateContext context = MetadataUpdateContext.builder()
                .bookEntity(bookEntity)
                .metadataUpdateWrapper(wrapper)
                .mergeTags(false)
                .replaceMode(MetadataReplaceMode.REPLACE_ALL)
                .build();

        when(tagRepository.findByName("ExistingTag")).thenReturn(Optional.of(TagEntity.builder().id(100L).name("ExistingTag").build()));
        when(tagRepository.findByName("NewTag1")).thenReturn(Optional.of(TagEntity.builder().id(1L).name("NewTag1").build()));
        when(tagRepository.findByName("NewTag2")).thenReturn(Optional.of(TagEntity.builder().id(2L).name("NewTag2").build()));
        when(tagRepository.findByName("NewTag3")).thenReturn(Optional.of(TagEntity.builder().id(3L).name("NewTag3").build()));

        bookMetadataUpdater.setBookMetadata(context);

        // Assert - all 4 tags should be present
        Set<TagEntity> resultTags = bookEntity.getMetadata().getTags();
        assertEquals(4, resultTags.size(), "All 4 tags (1 existing + 3 new) should be preserved");
        
        Set<String> tagNames = resultTags.stream().map(TagEntity::getName).collect(Collectors.toSet());
        assertTrue(tagNames.contains("ExistingTag"), "ExistingTag should be present");
        assertTrue(tagNames.contains("NewTag1"), "NewTag1 should be present");
        assertTrue(tagNames.contains("NewTag2"), "NewTag2 should be present");
        assertTrue(tagNames.contains("NewTag3"), "NewTag3 should be present");
    }

    @Test
    void setBookMetadata_withTagsHavingDifferentIds_shouldPreserveAllTags() {
        BookEntity bookEntity = new BookEntity();
        bookEntity.setId(1L);
        BookMetadataEntity metadataEntity = new BookMetadataEntity();
        metadataEntity.setTags(new HashSet<>());
        bookEntity.setMetadata(metadataEntity);

        BookMetadata newMetadata = new BookMetadata();
        newMetadata.setTags(Set.of("A", "B", "C", "D", "E"));

        MetadataUpdateWrapper wrapper = MetadataUpdateWrapper.builder()
                .metadata(newMetadata)
                .build();

        MetadataUpdateContext context = MetadataUpdateContext.builder()
                .bookEntity(bookEntity)
                .metadataUpdateWrapper(wrapper)
                .mergeTags(false)
                .replaceMode(MetadataReplaceMode.REPLACE_ALL)
                .build();

        when(tagRepository.findByName("A")).thenReturn(Optional.of(TagEntity.builder().id(1L).name("A").build()));
        when(tagRepository.findByName("B")).thenReturn(Optional.of(TagEntity.builder().id(2L).name("B").build()));
        when(tagRepository.findByName("C")).thenReturn(Optional.of(TagEntity.builder().id(3L).name("C").build()));
        when(tagRepository.findByName("D")).thenReturn(Optional.of(TagEntity.builder().id(4L).name("D").build()));
        when(tagRepository.findByName("E")).thenReturn(Optional.of(TagEntity.builder().id(5L).name("E").build()));

        bookMetadataUpdater.setBookMetadata(context);

        // Assert - all 5 tags should be present
        Set<TagEntity> resultTags = bookEntity.getMetadata().getTags();
        assertEquals(5, resultTags.size(), "All 5 tags should be preserved");
        
        Set<String> tagNames = resultTags.stream().map(TagEntity::getName).collect(Collectors.toSet());
        assertEquals(Set.of("A", "B", "C", "D", "E"), tagNames, "All tag names should match");
    }
}
