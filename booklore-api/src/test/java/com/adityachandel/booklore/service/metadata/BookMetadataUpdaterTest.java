package com.adityachandel.booklore.service.metadata;

import com.adityachandel.booklore.model.MetadataUpdateContext;
import com.adityachandel.booklore.model.MetadataUpdateWrapper;
import com.adityachandel.booklore.model.dto.BookMetadata;
import com.adityachandel.booklore.model.dto.settings.AppSettings;
import com.adityachandel.booklore.model.dto.settings.MetadataPersistenceSettings;
import com.adityachandel.booklore.model.entity.BookEntity;
import com.adityachandel.booklore.model.entity.BookMetadataEntity;
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
    void setBookMetadata_withLockField_shouldUpdateAndLock() {
        BookEntity bookEntity = new BookEntity();
        bookEntity.setId(1L);

        BookMetadataEntity metadataEntity = new BookMetadataEntity();
        metadataEntity.setTitle("Old Title");
        metadataEntity.setTitleLocked(false);
        bookEntity.setMetadata(metadataEntity);

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
}
