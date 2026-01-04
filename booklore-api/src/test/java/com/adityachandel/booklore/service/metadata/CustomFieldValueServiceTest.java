package com.adityachandel.booklore.service.metadata;

import com.adityachandel.booklore.exception.APIException;
import com.adityachandel.booklore.model.dto.BookMetadata;
import com.adityachandel.booklore.model.entity.BookCustomFieldValueEntity;
import com.adityachandel.booklore.model.entity.BookEntity;
import com.adityachandel.booklore.model.entity.LibraryCustomFieldEntity;
import com.adityachandel.booklore.model.entity.LibraryEntity;
import com.adityachandel.booklore.model.enums.CustomFieldType;
import com.adityachandel.booklore.repository.BookCustomFieldValueRepository;
import com.adityachandel.booklore.repository.LibraryCustomFieldRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CustomFieldValueServiceTest {

    @Mock private LibraryCustomFieldRepository libraryCustomFieldRepository;
    @Mock private BookCustomFieldValueRepository bookCustomFieldValueRepository;

    @InjectMocks
    private CustomFieldValueService service;

    private static BookEntity book(long bookId, long libraryId) {
        LibraryEntity library = new LibraryEntity();
        library.setId(libraryId);

        BookEntity book = new BookEntity();
        book.setId(bookId);
        book.setLibrary(library);
        return book;
    }

    private static LibraryCustomFieldEntity def(long id, String name, CustomFieldType type, String defaultValue) {
        LibraryCustomFieldEntity e = new LibraryCustomFieldEntity();
        e.setId(id);
        e.setName(name);
        e.setFieldType(type);
        e.setDefaultValue(defaultValue);
        return e;
    }

    @Test
    void applyCustomFields_blankIncomingValue_deletesExisting() {
        BookEntity book = book(1L, 10L);

        LibraryCustomFieldEntity ratingDef = def(100L, "rating", CustomFieldType.NUMBER, null);
        when(libraryCustomFieldRepository.findAllByLibrary_IdOrderByNameAsc(10L)).thenReturn(List.of(ratingDef));

        BookCustomFieldValueEntity existing = BookCustomFieldValueEntity.builder()
                .id(5L)
                .book(book)
                .customField(ratingDef)
                .valueNumber(4.0)
            .locked(false)
                .build();
        when(bookCustomFieldValueRepository.findAllByBook_Id(1L)).thenReturn(List.of(existing));

        BookMetadata incoming = BookMetadata.builder()
                .customFields(Map.of("rating", "   "))
                .build();

        boolean changed = service.applyCustomFields(book, incoming);

        assertTrue(changed);
        verify(bookCustomFieldValueRepository).delete(existing);
        verify(bookCustomFieldValueRepository, never()).save(any());
    }

    @Test
    void applyCustomFields_valueEqualsDefault_deletesExisting() {
        BookEntity book = book(1L, 10L);

        LibraryCustomFieldEntity shelfDef = def(200L, "shelf", CustomFieldType.STRING, "Unread");
        when(libraryCustomFieldRepository.findAllByLibrary_IdOrderByNameAsc(10L)).thenReturn(List.of(shelfDef));

        BookCustomFieldValueEntity existing = BookCustomFieldValueEntity.builder()
                .id(6L)
                .book(book)
                .customField(shelfDef)
                .valueString("Read")
            .locked(false)
                .build();
        when(bookCustomFieldValueRepository.findAllByBook_Id(1L)).thenReturn(List.of(existing));

        BookMetadata incoming = BookMetadata.builder()
                .customFields(Map.of("shelf", "Unread"))
                .build();

        boolean changed = service.applyCustomFields(book, incoming);

        assertTrue(changed);
        verify(bookCustomFieldValueRepository).delete(existing);
        verify(bookCustomFieldValueRepository, never()).save(any());
    }

    @Test
    void applyCustomFields_number_parsesAndSaves() {
        BookEntity book = book(1L, 10L);

        LibraryCustomFieldEntity ratingDef = def(100L, "rating", CustomFieldType.NUMBER, null);
        when(libraryCustomFieldRepository.findAllByLibrary_IdOrderByNameAsc(10L)).thenReturn(List.of(ratingDef));
        when(bookCustomFieldValueRepository.findAllByBook_Id(1L)).thenReturn(List.of());

        BookMetadata incoming = BookMetadata.builder()
                .customFields(Map.of("rating", "4.5"))
                .build();

        boolean changed = service.applyCustomFields(book, incoming);

        assertTrue(changed);

        ArgumentCaptor<BookCustomFieldValueEntity> captor = ArgumentCaptor.forClass(BookCustomFieldValueEntity.class);
        verify(bookCustomFieldValueRepository).save(captor.capture());
        BookCustomFieldValueEntity saved = captor.getValue();

        assertSame(book, saved.getBook());
        assertSame(ratingDef, saved.getCustomField());
        assertNull(saved.getValueString());
        assertEquals(4.5, saved.getValueNumber());
        assertNull(saved.getValueDate());
        assertFalse(Boolean.TRUE.equals(saved.getLocked()));
    }

    @Test
    void applyCustomFields_invalidNumber_throwsBadRequest() {
        BookEntity book = book(1L, 10L);

        LibraryCustomFieldEntity ratingDef = def(100L, "rating", CustomFieldType.NUMBER, null);
        when(libraryCustomFieldRepository.findAllByLibrary_IdOrderByNameAsc(10L)).thenReturn(List.of(ratingDef));
        when(bookCustomFieldValueRepository.findAllByBook_Id(1L)).thenReturn(List.of());

        BookMetadata incoming = BookMetadata.builder()
                .customFields(Map.of("rating", "not-a-number"))
                .build();

        APIException ex = assertThrows(APIException.class, () -> service.applyCustomFields(book, incoming));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
        assertTrue(ex.getMessage().contains("Invalid number"));

        verify(bookCustomFieldValueRepository, never()).save(any());
    }

    @Test
    void applyCustomFields_date_parsesAndSaves() {
        BookEntity book = book(1L, 10L);

        LibraryCustomFieldEntity startedDef = def(300L, "started", CustomFieldType.DATE, null);
        when(libraryCustomFieldRepository.findAllByLibrary_IdOrderByNameAsc(10L)).thenReturn(List.of(startedDef));
        when(bookCustomFieldValueRepository.findAllByBook_Id(1L)).thenReturn(List.of());

        BookMetadata incoming = BookMetadata.builder()
                .customFields(Map.of("started", "2025-01-03"))
                .build();

        boolean changed = service.applyCustomFields(book, incoming);

        assertTrue(changed);

        ArgumentCaptor<BookCustomFieldValueEntity> captor = ArgumentCaptor.forClass(BookCustomFieldValueEntity.class);
        verify(bookCustomFieldValueRepository).save(captor.capture());
        BookCustomFieldValueEntity saved = captor.getValue();

        assertNull(saved.getValueString());
        assertNull(saved.getValueNumber());
        assertEquals(LocalDate.of(2025, 1, 3), saved.getValueDate());
        assertFalse(Boolean.TRUE.equals(saved.getLocked()));
    }

    @Test
    void applyCustomFields_whenExistingLocked_valueUpdatesAreIgnored() {
        BookEntity book = book(1L, 10L);

        LibraryCustomFieldEntity shelfDef = def(200L, "shelf", CustomFieldType.STRING, "Unread");
        when(libraryCustomFieldRepository.findAllByLibrary_IdOrderByNameAsc(10L)).thenReturn(List.of(shelfDef));

        BookCustomFieldValueEntity existing = BookCustomFieldValueEntity.builder()
            .id(6L)
            .book(book)
            .customField(shelfDef)
            .valueString("Read")
            .locked(true)
            .build();
        when(bookCustomFieldValueRepository.findAllByBook_Id(1L)).thenReturn(List.of(existing));

        BookMetadata incoming = BookMetadata.builder()
            .customFields(Map.of("shelf", "Unread"))
            .build();

        boolean changed = service.applyCustomFields(book, incoming);

        assertFalse(changed);
        verify(bookCustomFieldValueRepository, never()).delete(any());
        verify(bookCustomFieldValueRepository, never()).save(any());
    }

    @Test
    void applyCustomFields_whenExistingLocked_blankIncomingValue_doesNotDeleteExisting() {
        BookEntity book = book(1L, 10L);

        LibraryCustomFieldEntity shelfDef = def(200L, "shelf", CustomFieldType.STRING, "Unread");
        when(libraryCustomFieldRepository.findAllByLibrary_IdOrderByNameAsc(10L)).thenReturn(List.of(shelfDef));

        BookCustomFieldValueEntity existing = BookCustomFieldValueEntity.builder()
                .id(6L)
                .book(book)
                .customField(shelfDef)
                .valueString("Read")
                .locked(true)
                .build();
        when(bookCustomFieldValueRepository.findAllByBook_Id(1L)).thenReturn(List.of(existing));

        BookMetadata incoming = BookMetadata.builder()
                .customFields(Map.of("shelf", "   "))
                .build();

        boolean changed = service.applyCustomFields(book, incoming);

        assertFalse(changed);
        verify(bookCustomFieldValueRepository, never()).delete(any());
        verify(bookCustomFieldValueRepository, never()).save(any());
    }

    @Test
    void applyCustomFields_canToggleLockState() {
        BookEntity book = book(1L, 10L);

        LibraryCustomFieldEntity shelfDef = def(200L, "shelf", CustomFieldType.STRING, "Unread");
        when(libraryCustomFieldRepository.findAllByLibrary_IdOrderByNameAsc(10L)).thenReturn(List.of(shelfDef));

        BookCustomFieldValueEntity existing = BookCustomFieldValueEntity.builder()
            .id(6L)
            .book(book)
            .customField(shelfDef)
            .valueString("Read")
            .locked(false)
            .build();
        when(bookCustomFieldValueRepository.findAllByBook_Id(1L)).thenReturn(List.of(existing));

        BookMetadata incoming = BookMetadata.builder()
            .customFieldLocks(Map.of("shelf", true))
            .build();

        boolean changed = service.applyCustomFields(book, incoming);
        assertTrue(changed);

        ArgumentCaptor<BookCustomFieldValueEntity> captor = ArgumentCaptor.forClass(BookCustomFieldValueEntity.class);
        verify(bookCustomFieldValueRepository).save(captor.capture());
        assertTrue(Boolean.TRUE.equals(captor.getValue().getLocked()));
    }

    @Test
    void applyCustomFields_whenLockProvidedAndNoExisting_createsValueRowWithLockState() {
        BookEntity book = book(1L, 10L);

        LibraryCustomFieldEntity shelfDef = def(200L, "shelf", CustomFieldType.STRING, "Unread");
        when(libraryCustomFieldRepository.findAllByLibrary_IdOrderByNameAsc(10L)).thenReturn(List.of(shelfDef));
        when(bookCustomFieldValueRepository.findAllByBook_Id(1L)).thenReturn(List.of());

        BookMetadata incoming = BookMetadata.builder()
                .customFieldLocks(Map.of("shelf", true))
                .build();

        boolean changed = service.applyCustomFields(book, incoming);
        assertTrue(changed);

        ArgumentCaptor<BookCustomFieldValueEntity> captor = ArgumentCaptor.forClass(BookCustomFieldValueEntity.class);
        verify(bookCustomFieldValueRepository).save(captor.capture());
        BookCustomFieldValueEntity saved = captor.getValue();

        assertSame(book, saved.getBook());
        assertSame(shelfDef, saved.getCustomField());
        assertTrue(Boolean.TRUE.equals(saved.getLocked()));
        assertNull(saved.getValueString());
        assertNull(saved.getValueNumber());
        assertNull(saved.getValueDate());
    }

    @Test
    void applyCustomFields_whenLockProvidedMatchesExisting_doesNothing() {
        BookEntity book = book(1L, 10L);

        LibraryCustomFieldEntity shelfDef = def(200L, "shelf", CustomFieldType.STRING, "Unread");
        when(libraryCustomFieldRepository.findAllByLibrary_IdOrderByNameAsc(10L)).thenReturn(List.of(shelfDef));

        BookCustomFieldValueEntity existing = BookCustomFieldValueEntity.builder()
                .id(6L)
                .book(book)
                .customField(shelfDef)
                .valueString("Read")
                .locked(true)
                .build();
        when(bookCustomFieldValueRepository.findAllByBook_Id(1L)).thenReturn(List.of(existing));

        BookMetadata incoming = BookMetadata.builder()
                .customFieldLocks(Map.of("shelf", true))
                .build();

        boolean changed = service.applyCustomFields(book, incoming);

        assertFalse(changed);
        verify(bookCustomFieldValueRepository, never()).delete(any());
        verify(bookCustomFieldValueRepository, never()).save(any());
    }

    @Test
    void hasCustomFieldChanges_whenExistingLocked_valueDifferencesIgnored() {
        BookEntity book = book(1L, 10L);

        LibraryCustomFieldEntity shelfDef = def(200L, "shelf", CustomFieldType.STRING, "Unread");
        when(libraryCustomFieldRepository.findAllByLibrary_IdOrderByNameAsc(10L)).thenReturn(List.of(shelfDef));

        BookCustomFieldValueEntity existing = BookCustomFieldValueEntity.builder()
            .id(6L)
            .book(book)
            .customField(shelfDef)
            .valueString("Read")
            .locked(true)
            .build();
        when(bookCustomFieldValueRepository.findAllByBook_Id(1L)).thenReturn(List.of(existing));

        BookMetadata incoming = BookMetadata.builder()
            .customFields(Map.of("shelf", "Unread"))
            .build();

        assertFalse(service.hasCustomFieldChanges(book, incoming));
    }

    @Test
    void hasCustomFieldChanges_whenExistingLocked_blankIncomingValueIgnored() {
        BookEntity book = book(1L, 10L);

        LibraryCustomFieldEntity shelfDef = def(200L, "shelf", CustomFieldType.STRING, "Unread");
        when(libraryCustomFieldRepository.findAllByLibrary_IdOrderByNameAsc(10L)).thenReturn(List.of(shelfDef));

        BookCustomFieldValueEntity existing = BookCustomFieldValueEntity.builder()
                .id(6L)
                .book(book)
                .customField(shelfDef)
                .valueString("Read")
                .locked(true)
                .build();
        when(bookCustomFieldValueRepository.findAllByBook_Id(1L)).thenReturn(List.of(existing));

        BookMetadata incoming = BookMetadata.builder()
                .customFields(Map.of("shelf", "   "))
                .build();

        assertFalse(service.hasCustomFieldChanges(book, incoming));
    }

    @Test
    void hasCustomFieldChanges_whenLockDiffers_returnsTrue() {
        BookEntity book = book(1L, 10L);

        LibraryCustomFieldEntity shelfDef = def(200L, "shelf", CustomFieldType.STRING, "Unread");
        when(libraryCustomFieldRepository.findAllByLibrary_IdOrderByNameAsc(10L)).thenReturn(List.of(shelfDef));

        BookCustomFieldValueEntity existing = BookCustomFieldValueEntity.builder()
            .id(6L)
            .book(book)
            .customField(shelfDef)
            .valueString("Read")
            .locked(false)
            .build();
        when(bookCustomFieldValueRepository.findAllByBook_Id(1L)).thenReturn(List.of(existing));

        BookMetadata incoming = BookMetadata.builder()
            .customFieldLocks(Map.of("shelf", true))
            .build();

        assertTrue(service.hasCustomFieldChanges(book, incoming));

    }

    @Test
    void hasCustomFieldChanges_whenLockProvidedTrueAndNoExisting_returnsTrue() {
        BookEntity book = book(1L, 10L);

        LibraryCustomFieldEntity shelfDef = def(200L, "shelf", CustomFieldType.STRING, "Unread");
        when(libraryCustomFieldRepository.findAllByLibrary_IdOrderByNameAsc(10L)).thenReturn(List.of(shelfDef));
        when(bookCustomFieldValueRepository.findAllByBook_Id(1L)).thenReturn(List.of());

        BookMetadata incoming = BookMetadata.builder()
                .customFieldLocks(Map.of("shelf", true))
                .build();

        assertTrue(service.hasCustomFieldChanges(book, incoming));
    }

    @Test
    void hasCustomFieldChanges_whenLockProvidedFalseAndNoExisting_returnsFalse() {
        BookEntity book = book(1L, 10L);

        LibraryCustomFieldEntity shelfDef = def(200L, "shelf", CustomFieldType.STRING, "Unread");
        when(libraryCustomFieldRepository.findAllByLibrary_IdOrderByNameAsc(10L)).thenReturn(List.of(shelfDef));
        when(bookCustomFieldValueRepository.findAllByBook_Id(1L)).thenReturn(List.of());

        BookMetadata incoming = BookMetadata.builder()
                .customFieldLocks(Map.of("shelf", false))
                .build();

        assertFalse(service.hasCustomFieldChanges(book, incoming));
    }

    @Test
    void hasCustomFieldChanges_invalidNumber_returnsTrue() {
        BookEntity book = book(1L, 10L);

        LibraryCustomFieldEntity ratingDef = def(100L, "rating", CustomFieldType.NUMBER, null);
        when(libraryCustomFieldRepository.findAllByLibrary_IdOrderByNameAsc(10L)).thenReturn(List.of(ratingDef));
        when(bookCustomFieldValueRepository.findAllByBook_Id(1L)).thenReturn(List.of());

        BookMetadata incoming = BookMetadata.builder()
                .customFields(Map.of("rating", "not-a-number"))
                .build();

        assertTrue(service.hasCustomFieldChanges(book, incoming));
    }
}
