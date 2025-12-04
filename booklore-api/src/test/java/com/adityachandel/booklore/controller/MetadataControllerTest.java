package com.adityachandel.booklore.controller;

import com.adityachandel.booklore.config.security.service.AuthenticationService;
import com.adityachandel.booklore.mapper.BookMetadataMapper;
import com.adityachandel.booklore.model.MetadataUpdateContext;
import com.adityachandel.booklore.model.MetadataUpdateWrapper;
import com.adityachandel.booklore.model.dto.BookMetadata;
import com.adityachandel.booklore.model.entity.BookEntity;
import com.adityachandel.booklore.model.entity.BookMetadataEntity;
import com.adityachandel.booklore.model.entity.TagEntity;
import com.adityachandel.booklore.model.entity.AuthorEntity;
import com.adityachandel.booklore.model.entity.CategoryEntity;
import com.adityachandel.booklore.repository.BookRepository;
import com.adityachandel.booklore.service.metadata.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MetadataControllerTest {

    @Mock
    private BookMetadataService bookMetadataService;
    @Mock
    private BookMetadataUpdater bookMetadataUpdater;
    @Mock
    private AuthenticationService authenticationService;
    @Mock
    private BookMetadataMapper bookMetadataMapper;
    @Mock
    private MetadataMatchService metadataMatchService;
    @Mock
    private DuckDuckGoCoverService duckDuckGoCoverService;
    @Mock
    private BookRepository bookRepository;
    @Mock
    private MetadataManagementService metadataManagementService;

    @InjectMocks
    private MetadataController metadataController;

    private MetadataUpdateContext captureContextFromUpdate() {
        long bookId = 1L;
        MetadataUpdateWrapper wrapper = MetadataUpdateWrapper.builder().build();
        BookEntity bookEntity = new BookEntity();
        bookEntity.setId(bookId);
        bookEntity.setMetadata(new BookMetadataEntity());

        when(bookRepository.findById(bookId)).thenReturn(Optional.of(bookEntity));
        when(bookRepository.saveAndFlush(bookEntity)).thenReturn(bookEntity);
        when(bookMetadataMapper.toBookMetadata(any(), anyBoolean())).thenReturn(new BookMetadata());

        metadataController.updateMetadata(wrapper, bookId, true);

        ArgumentCaptor<MetadataUpdateContext> captor = ArgumentCaptor.forClass(MetadataUpdateContext.class);
        verify(bookMetadataUpdater).setBookMetadata(captor.capture());
        return captor.getValue();
    }

    @Test
    void updateMetadata_shouldDisableMergingForTagsAndMoods() {
        MetadataUpdateContext context = captureContextFromUpdate();

        assertFalse(context.isMergeTags(), "mergeTags should be false to allow deletion of tags");
        assertFalse(context.isMergeMoods(), "mergeMoods should be false to allow deletion of moods");
    }

    @Test
    void updateMetadata_shouldFlushBeforeMapping() {
        long bookId = 1L;
        BookMetadata inputMetadata = BookMetadata.builder()
                .tags(Set.of("Tag1", "Tag2", "Tag3"))
                .authors(Set.of("Author1", "Author2", "Author3"))
                .categories(Set.of("Category1", "Category2", "Category3"))
                .build();
        
        MetadataUpdateWrapper wrapper = MetadataUpdateWrapper.builder()
                .metadata(inputMetadata)
                .build();
        
        BookEntity bookEntity = new BookEntity();
        bookEntity.setId(bookId);
        BookMetadataEntity metadataEntity = new BookMetadataEntity();
        
        // Setup tags, authors, categories on the entity
        Set<TagEntity> tags = new HashSet<>();
        tags.add(TagEntity.builder().id(1L).name("Tag1").build());
        tags.add(TagEntity.builder().id(2L).name("Tag2").build());
        tags.add(TagEntity.builder().id(3L).name("Tag3").build());
        metadataEntity.setTags(tags);
        
        Set<AuthorEntity> authors = new HashSet<>();
        authors.add(AuthorEntity.builder().id(1L).name("Author1").build());
        authors.add(AuthorEntity.builder().id(2L).name("Author2").build());
        authors.add(AuthorEntity.builder().id(3L).name("Author3").build());
        metadataEntity.setAuthors(authors);
        
        Set<CategoryEntity> categories = new HashSet<>();
        categories.add(CategoryEntity.builder().id(1L).name("Category1").build());
        categories.add(CategoryEntity.builder().id(2L).name("Category2").build());
        categories.add(CategoryEntity.builder().id(3L).name("Category3").build());
        metadataEntity.setCategories(categories);
        
        bookEntity.setMetadata(metadataEntity);

        when(bookRepository.findById(bookId)).thenReturn(Optional.of(bookEntity));
        when(bookRepository.saveAndFlush(bookEntity)).thenReturn(bookEntity);
        
        BookMetadata expectedResponse = BookMetadata.builder()
                .tags(Set.of("Tag1", "Tag2", "Tag3"))
                .authors(Set.of("Author1", "Author2", "Author3"))
                .categories(Set.of("Category1", "Category2", "Category3"))
                .build();
        when(bookMetadataMapper.toBookMetadata(any(), anyBoolean())).thenReturn(expectedResponse);

        var response = metadataController.updateMetadata(wrapper, bookId, false);

        verify(bookRepository).saveAndFlush(bookEntity); // Verify saveAndFlush is called
        verify(bookMetadataMapper).toBookMetadata(metadataEntity, true);
        
        BookMetadata responseBody = response.getBody();
        assertNotNull(responseBody);
        assertEquals(3, responseBody.getTags().size(), "All 3 tags should be in response");
        assertEquals(3, responseBody.getAuthors().size(), "All 3 authors should be in response");
        assertEquals(3, responseBody.getCategories().size(), "All 3 categories should be in response");
    }

    @Test
    void updateMetadata_withMultipleTagsAuthorsCategories_shouldMapAllItems() {
        long bookId = 1L;
        
        MetadataUpdateWrapper wrapper = MetadataUpdateWrapper.builder()
                .metadata(BookMetadata.builder()
                        .tags(Set.of("A", "B", "C", "D", "E"))
                        .build())
                .build();
        
        BookEntity bookEntity = new BookEntity();
        bookEntity.setId(bookId);
        BookMetadataEntity metadataEntity = new BookMetadataEntity();
        
        Set<TagEntity> tags = new HashSet<>();
        tags.add(TagEntity.builder().id(1L).name("A").build());
        tags.add(TagEntity.builder().id(2L).name("B").build());
        tags.add(TagEntity.builder().id(3L).name("C").build());
        tags.add(TagEntity.builder().id(4L).name("D").build());
        tags.add(TagEntity.builder().id(5L).name("E").build());
        metadataEntity.setTags(tags);
        
        bookEntity.setMetadata(metadataEntity);

        when(bookRepository.findById(bookId)).thenReturn(Optional.of(bookEntity));
        when(bookRepository.saveAndFlush(bookEntity)).thenReturn(bookEntity);
        
        BookMetadata mappedMetadata = BookMetadata.builder()
                .tags(Set.of("A", "B", "C", "D", "E"))
                .build();
        when(bookMetadataMapper.toBookMetadata(any(), anyBoolean())).thenReturn(mappedMetadata);

        var response = metadataController.updateMetadata(wrapper, bookId, false);

        BookMetadata responseBody = response.getBody();
        assertNotNull(responseBody);
        assertNotNull(responseBody.getTags());
        assertEquals(5, responseBody.getTags().size(), "All 5 tags should be in response");
        assertTrue(responseBody.getTags().containsAll(Set.of("A", "B", "C", "D", "E")));
    }

    @Test
    void updateMetadata_shouldCallMethodsInCorrectOrder() {
        long bookId = 1L;
        BookMetadata inputMetadata = BookMetadata.builder()
                .tags(Set.of("Tag1", "Tag2", "Tag3"))
                .build();
        
        MetadataUpdateWrapper wrapper = MetadataUpdateWrapper.builder()
                .metadata(inputMetadata)
                .build();
        
        BookEntity bookEntity = new BookEntity();
        bookEntity.setId(bookId);
        BookMetadataEntity metadataEntity = new BookMetadataEntity();
        bookEntity.setMetadata(metadataEntity);

        when(bookRepository.findById(bookId)).thenReturn(Optional.of(bookEntity));
        when(bookRepository.saveAndFlush(bookEntity)).thenReturn(bookEntity);
        when(bookMetadataMapper.toBookMetadata(any(), anyBoolean())).thenReturn(new BookMetadata());

        metadataController.updateMetadata(wrapper, bookId, false);

        InOrder inOrder = inOrder(bookMetadataUpdater, bookRepository, bookMetadataMapper);
        inOrder.verify(bookMetadataUpdater).setBookMetadata(any());
        inOrder.verify(bookRepository).saveAndFlush(any());
        inOrder.verify(bookMetadataMapper).toBookMetadata(any(), anyBoolean());
    }
}
