package org.booklore.service.book;

import org.booklore.model.entity.AuthorEntity;
import org.booklore.model.entity.BookEntity;
import org.booklore.model.entity.BookMetadataEntity;
import org.booklore.model.entity.CategoryEntity;
import org.booklore.model.entity.MoodEntity;
import org.booklore.model.entity.TagEntity;
import org.booklore.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BookCreatorServiceTest {

    @Mock private AuthorRepository authorRepository;
    @Mock private CategoryRepository categoryRepository;
    @Mock private MoodRepository moodRepository;
    @Mock private TagRepository tagRepository;
    @Mock private BookRepository bookRepository;
    @Mock private BookMetadataRepository bookMetadataRepository;
    @Mock private ComicMetadataRepository comicMetadataRepository;
    @Mock private ComicCharacterRepository comicCharacterRepository;
    @Mock private ComicTeamRepository comicTeamRepository;
    @Mock private ComicLocationRepository comicLocationRepository;
    @Mock private ComicCreatorRepository comicCreatorRepository;

    @InjectMocks
    private BookCreatorService bookCreatorService;

    private BookEntity bookEntity;

    @BeforeEach
    void setUp() {
        BookMetadataEntity metadata = new BookMetadataEntity();
        bookEntity = BookEntity.builder().metadata(metadata).build();
    }

    @Test
    void addAuthorsToBook_nullAuthors_doesNothing() {
        bookCreatorService.addAuthorsToBook(null, bookEntity);

        assertNull(bookEntity.getMetadata().getAuthors());
        verifyNoInteractions(authorRepository);
    }

    @Test
    void addAuthorsToBook_emptyAuthors_doesNothing() {
        bookCreatorService.addAuthorsToBook(Set.of(), bookEntity);

        assertNull(bookEntity.getMetadata().getAuthors());
        verifyNoInteractions(authorRepository);
    }

    @Test
    void addAuthorsToBook_validAuthors_addsToBook() {
        AuthorEntity author = AuthorEntity.builder().name("Test Author").build();
        when(authorRepository.findByName("Test Author")).thenReturn(Optional.of(author));

        bookCreatorService.addAuthorsToBook(Set.of("Test Author"), bookEntity);

        assertNotNull(bookEntity.getMetadata().getAuthors());
        assertEquals(1, bookEntity.getMetadata().getAuthors().size());
    }

    @Test
    void addAuthorsToBook_nullExistingAuthorsOnEntity_initializesSet() {
        assertNull(bookEntity.getMetadata().getAuthors());

        AuthorEntity author = AuthorEntity.builder().name("Author").build();
        when(authorRepository.findByName("Author")).thenReturn(Optional.of(author));

        bookCreatorService.addAuthorsToBook(Set.of("Author"), bookEntity);

        assertNotNull(bookEntity.getMetadata().getAuthors());
        assertTrue(bookEntity.getMetadata().getAuthors().contains(author));
    }

    @Test
    void addCategoriesToBook_nullCategories_doesNothing() {
        bookCreatorService.addCategoriesToBook(null, bookEntity);

        assertNull(bookEntity.getMetadata().getCategories());
        verifyNoInteractions(categoryRepository);
    }

    @Test
    void addCategoriesToBook_emptyCategories_doesNothing() {
        bookCreatorService.addCategoriesToBook(Set.of(), bookEntity);

        assertNull(bookEntity.getMetadata().getCategories());
        verifyNoInteractions(categoryRepository);
    }

    @Test
    void addCategoriesToBook_validCategories_addsToBook() {
        CategoryEntity category = CategoryEntity.builder().name("Fiction").build();
        when(categoryRepository.findByName("Fiction")).thenReturn(Optional.of(category));

        bookCreatorService.addCategoriesToBook(Set.of("Fiction"), bookEntity);

        assertNotNull(bookEntity.getMetadata().getCategories());
        assertEquals(1, bookEntity.getMetadata().getCategories().size());
    }

    @Test
    void addMoodsToBook_nullMoods_doesNothing() {
        bookCreatorService.addMoodsToBook(null, bookEntity);

        assertNull(bookEntity.getMetadata().getMoods());
        verifyNoInteractions(moodRepository);
    }

    @Test
    void addMoodsToBook_emptyMoods_doesNothing() {
        bookCreatorService.addMoodsToBook(Set.of(), bookEntity);

        assertNull(bookEntity.getMetadata().getMoods());
        verifyNoInteractions(moodRepository);
    }

    @Test
    void addMoodsToBook_validMoods_addsToBook() {
        MoodEntity mood = MoodEntity.builder().name("Dark").build();
        when(moodRepository.findByName("Dark")).thenReturn(Optional.of(mood));

        bookCreatorService.addMoodsToBook(Set.of("Dark"), bookEntity);

        assertNotNull(bookEntity.getMetadata().getMoods());
        assertEquals(1, bookEntity.getMetadata().getMoods().size());
    }

    @Test
    void addTagsToBook_nullTags_doesNothing() {
        bookCreatorService.addTagsToBook(null, bookEntity);

        assertNull(bookEntity.getMetadata().getTags());
        verifyNoInteractions(tagRepository);
    }

    @Test
    void addTagsToBook_emptyTags_doesNothing() {
        bookCreatorService.addTagsToBook(Set.of(), bookEntity);

        assertNull(bookEntity.getMetadata().getTags());
        verifyNoInteractions(tagRepository);
    }

    @Test
    void addTagsToBook_validTags_addsToBook() {
        TagEntity tag = TagEntity.builder().name("favorite").build();
        when(tagRepository.findByName("favorite")).thenReturn(Optional.of(tag));

        bookCreatorService.addTagsToBook(Set.of("favorite"), bookEntity);

        assertNotNull(bookEntity.getMetadata().getTags());
        assertEquals(1, bookEntity.getMetadata().getTags().size());
    }

    @Test
    void addAuthorsToBook_existingAuthorsOnEntity_appendsWithoutOverwriting() {
        AuthorEntity existingAuthor = AuthorEntity.builder().name("Existing").build();
        bookEntity.getMetadata().setAuthors(new HashSet<>(Set.of(existingAuthor)));

        AuthorEntity newAuthor = AuthorEntity.builder().name("New Author").build();
        when(authorRepository.findByName("New Author")).thenReturn(Optional.of(newAuthor));

        bookCreatorService.addAuthorsToBook(Set.of("New Author"), bookEntity);

        assertEquals(2, bookEntity.getMetadata().getAuthors().size());
    }

    @Test
    void addAuthorsToBook_newAuthorNotInRepo_savesAndAdds() {
        AuthorEntity saved = AuthorEntity.builder().name("Brand New").build();
        when(authorRepository.findByName("Brand New")).thenReturn(Optional.empty());
        when(authorRepository.save(any())).thenReturn(saved);

        bookCreatorService.addAuthorsToBook(Set.of("Brand New"), bookEntity);

        verify(authorRepository).save(any());
        assertEquals(1, bookEntity.getMetadata().getAuthors().size());
    }
}
