package com.adityachandel.booklore.service.library;

import com.adityachandel.booklore.exception.APIException;
import com.adityachandel.booklore.model.dto.request.CreateLibraryCustomFieldRequest;
import com.adityachandel.booklore.model.entity.LibraryCustomFieldEntity;
import com.adityachandel.booklore.model.entity.LibraryEntity;
import com.adityachandel.booklore.model.enums.CustomFieldType;
import com.adityachandel.booklore.repository.LibraryCustomFieldRepository;
import com.adityachandel.booklore.repository.LibraryRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LibraryCustomFieldServiceTest {

    @Mock private LibraryRepository libraryRepository;
    @Mock private LibraryCustomFieldRepository libraryCustomFieldRepository;

    @InjectMocks
    private LibraryCustomFieldService service;

    @Test
    void createCustomField_nullRequest_throwsInvalidInput() {
        APIException ex = assertThrows(APIException.class, () -> service.createCustomField(1L, null));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
        assertTrue(ex.getMessage().contains("Custom field name is required"));
    }

    @Test
    void createCustomField_invalidName_throwsInvalidInput() {
        CreateLibraryCustomFieldRequest request = CreateLibraryCustomFieldRequest.builder()
                .name("Bad Name")
                .fieldType(CustomFieldType.STRING)
                .build();

        APIException ex = assertThrows(APIException.class, () -> service.createCustomField(1L, request));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
        assertTrue(ex.getMessage().contains("must match"));
    }

    @Test
    void createCustomField_whenAlreadyExists_throwsConflict() {
        CreateLibraryCustomFieldRequest request = CreateLibraryCustomFieldRequest.builder()
                .name("shelf")
                .fieldType(CustomFieldType.STRING)
                .build();

        LibraryEntity library = new LibraryEntity();
        library.setId(1L);

        when(libraryRepository.findById(1L)).thenReturn(Optional.of(library));
        when(libraryCustomFieldRepository.existsByLibrary_IdAndName(1L, "shelf")).thenReturn(true);

        APIException ex = assertThrows(APIException.class, () -> service.createCustomField(1L, request));
        assertEquals(HttpStatus.CONFLICT, ex.getStatus());
        assertTrue(ex.getMessage().contains("Custom field already exists"));

        verify(libraryCustomFieldRepository, never()).save(any());
    }

    @Test
    void createCustomField_happyPath_persistsAndReturnsDto() {
        CreateLibraryCustomFieldRequest request = CreateLibraryCustomFieldRequest.builder()
                .name("shelf")
                .fieldType(CustomFieldType.STRING)
                .defaultValue("Unread")
                .build();

        LibraryEntity library = new LibraryEntity();
        library.setId(10L);

        when(libraryRepository.findById(10L)).thenReturn(Optional.of(library));
        when(libraryCustomFieldRepository.existsByLibrary_IdAndName(10L, "shelf")).thenReturn(false);
        when(libraryCustomFieldRepository.save(any(LibraryCustomFieldEntity.class))).thenAnswer(inv -> {
            LibraryCustomFieldEntity e = inv.getArgument(0);
            e.setId(99L);
            return e;
        });

        var dto = service.createCustomField(10L, request);

        assertEquals(99L, dto.getId());
        assertEquals(10L, dto.getLibraryId());
        assertEquals("shelf", dto.getName());
        assertEquals(CustomFieldType.STRING, dto.getFieldType());
        assertEquals("Unread", dto.getDefaultValue());
    }

    @Test
    void deleteCustomField_whenDifferentLibrary_throwsForbidden() {
        LibraryEntity otherLibrary = new LibraryEntity();
        otherLibrary.setId(2L);

        LibraryCustomFieldEntity field = new LibraryCustomFieldEntity();
        field.setId(5L);
        field.setLibrary(otherLibrary);

        when(libraryCustomFieldRepository.findById(5L)).thenReturn(Optional.of(field));

        APIException ex = assertThrows(APIException.class, () -> service.deleteCustomField(1L, 5L));
        assertEquals(HttpStatus.FORBIDDEN, ex.getStatus());
        assertTrue(ex.getMessage().contains("does not belong to library"));

        verify(libraryCustomFieldRepository, never()).delete(any());
    }

    @Test
    void deleteCustomField_happyPath_deletes() {
        LibraryEntity library = new LibraryEntity();
        library.setId(1L);

        LibraryCustomFieldEntity field = new LibraryCustomFieldEntity();
        field.setId(5L);
        field.setLibrary(library);

        when(libraryCustomFieldRepository.findById(5L)).thenReturn(Optional.of(field));

        assertDoesNotThrow(() -> service.deleteCustomField(1L, 5L));
        verify(libraryCustomFieldRepository).delete(field);
    }
}
