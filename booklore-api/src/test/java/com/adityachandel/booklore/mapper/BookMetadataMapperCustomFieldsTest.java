package com.adityachandel.booklore.mapper;

import com.adityachandel.booklore.model.dto.BookMetadata;
import com.adityachandel.booklore.model.entity.BookCustomFieldValueEntity;
import com.adityachandel.booklore.model.entity.BookEntity;
import com.adityachandel.booklore.model.entity.BookMetadataEntity;
import com.adityachandel.booklore.model.entity.LibraryCustomFieldEntity;
import com.adityachandel.booklore.model.entity.LibraryEntity;
import com.adityachandel.booklore.model.enums.CustomFieldType;
import com.adityachandel.booklore.repository.BookCustomFieldValueRepository;
import com.adityachandel.booklore.repository.LibraryCustomFieldRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BookMetadataMapperCustomFieldsTest {

    @Mock private LibraryCustomFieldRepository libraryCustomFieldRepository;
    @Mock private BookCustomFieldValueRepository bookCustomFieldValueRepository;

    private TestableBookMetadataMapper mapper;

    @BeforeEach
    void setUp() throws Exception {
        mapper = new TestableBookMetadataMapper();
        inject(mapper, "libraryCustomFieldRepository", libraryCustomFieldRepository);
        inject(mapper, "bookCustomFieldValueRepository", bookCustomFieldValueRepository);
    }

    @Test
    void mapCustomFields_whenNoDefinitions_doesNotSetCustomFields() {
        BookMetadataEntity entity = metadataEntity(1L, 10L);
        BookMetadata dto = new BookMetadata();

        when(libraryCustomFieldRepository.findAllByLibrary_IdOrderByNameAsc(10L)).thenReturn(List.of());

        mapper.invokeMapCustomFields(entity, dto);

        assertNull(dto.getCustomFields());
        assertNull(dto.getCustomFieldLocks());
    }

    @Test
    void mapCustomFields_defaultsUsedWhenNoStoredValue_andNullsFiltered() {
        BookMetadataEntity entity = metadataEntity(1L, 10L);
        BookMetadata dto = new BookMetadata();

        // defs already come from an OrderByNameAsc query; keep list deterministic here.
        LibraryCustomFieldEntity defA = def(100L, "a", CustomFieldType.STRING, "DefaultA");
        LibraryCustomFieldEntity defB = def(200L, "b", CustomFieldType.STRING, null);

        when(libraryCustomFieldRepository.findAllByLibrary_IdOrderByNameAsc(10L)).thenReturn(List.of(defA, defB));
        when(bookCustomFieldValueRepository.findAllByBook_Id(1L)).thenReturn(List.of());

        mapper.invokeMapCustomFields(entity, dto);

        assertNotNull(dto.getCustomFields());
        assertEquals(Map.of("a", "DefaultA"), dto.getCustomFields(), "Null-valued defaults should be filtered out");

        assertNotNull(dto.getCustomFieldLocks());
        assertEquals(Map.of("a", false, "b", false), dto.getCustomFieldLocks());
    }

    @Test
    void mapCustomFields_storedValueOverridesDefault_andTypesFormatted() {
        BookMetadataEntity entity = metadataEntity(1L, 10L);
        BookMetadata dto = new BookMetadata();

        LibraryCustomFieldEntity shelf = def(1L, "shelf", CustomFieldType.STRING, "Unread");
        LibraryCustomFieldEntity rating = def(2L, "rating", CustomFieldType.NUMBER, "0");
        LibraryCustomFieldEntity started = def(3L, "started", CustomFieldType.DATE, null);

        when(libraryCustomFieldRepository.findAllByLibrary_IdOrderByNameAsc(10L)).thenReturn(List.of(rating, shelf, started));

        BookCustomFieldValueEntity shelfValue = BookCustomFieldValueEntity.builder()
                .customField(shelf)
                .valueString("Read")
            .locked(true)
                .build();
        BookCustomFieldValueEntity ratingValue = BookCustomFieldValueEntity.builder()
                .customField(rating)
                .valueNumber(4.5)
            .locked(false)
                .build();
        BookCustomFieldValueEntity startedValue = BookCustomFieldValueEntity.builder()
                .customField(started)
                .valueDate(LocalDate.of(2025, 1, 3))
            .locked(false)
                .build();

        when(bookCustomFieldValueRepository.findAllByBook_Id(1L)).thenReturn(List.of(shelfValue, ratingValue, startedValue));

        mapper.invokeMapCustomFields(entity, dto);

        assertNotNull(dto.getCustomFields());
        assertEquals("Read", dto.getCustomFields().get("shelf"));
        assertEquals("4.5", dto.getCustomFields().get("rating"));
        assertEquals("2025-01-03", dto.getCustomFields().get("started"));

        assertNotNull(dto.getCustomFieldLocks());
        assertEquals(true, dto.getCustomFieldLocks().get("shelf"));
        assertEquals(false, dto.getCustomFieldLocks().get("rating"));
        assertEquals(false, dto.getCustomFieldLocks().get("started"));

        // Default should only be used when stored value is missing
        assertNotEquals("Unread", dto.getCustomFields().get("shelf"));
    }

    @Test
    void mapCustomFields_whenStoredLockIsNull_defaultsLockToFalse() {
        BookMetadataEntity entity = metadataEntity(1L, 10L);
        BookMetadata dto = new BookMetadata();

        LibraryCustomFieldEntity shelf = def(1L, "shelf", CustomFieldType.STRING, "Unread");
        when(libraryCustomFieldRepository.findAllByLibrary_IdOrderByNameAsc(10L)).thenReturn(List.of(shelf));

        BookCustomFieldValueEntity shelfValue = BookCustomFieldValueEntity.builder()
                .customField(shelf)
                .valueString("Read")
                .locked(null)
                .build();
        when(bookCustomFieldValueRepository.findAllByBook_Id(1L)).thenReturn(List.of(shelfValue));

        mapper.invokeMapCustomFields(entity, dto);

        assertNotNull(dto.getCustomFields());
        assertEquals("Read", dto.getCustomFields().get("shelf"));

        assertNotNull(dto.getCustomFieldLocks());
        assertEquals(false, dto.getCustomFieldLocks().get("shelf"));
    }

    private static BookMetadataEntity metadataEntity(long bookId, long libraryId) {
        LibraryEntity library = new LibraryEntity();
        library.setId(libraryId);

        BookEntity book = new BookEntity();
        book.setId(bookId);
        book.setLibrary(library);

        BookMetadataEntity metadataEntity = new BookMetadataEntity();
        metadataEntity.setBookId(bookId);
        metadataEntity.setBook(book);
        return metadataEntity;
    }

    private static LibraryCustomFieldEntity def(long id, String name, CustomFieldType type, String defaultValue) {
        LibraryCustomFieldEntity def = new LibraryCustomFieldEntity();
        def.setId(id);
        def.setName(name);
        def.setFieldType(type);
        def.setDefaultValue(defaultValue);
        return def;
    }

    private static void inject(BookMetadataMapper target, String fieldName, Object value) throws Exception {
        Field field = BookMetadataMapper.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }

    /**
     * Minimal concrete mapper so we can call the protected @AfterMapping hook directly.
     */
    private static final class TestableBookMetadataMapper extends BookMetadataMapper {

        void invokeMapCustomFields(BookMetadataEntity entity, BookMetadata dto) {
            mapCustomFields(entity, dto);
        }

        @Override
        public BookMetadata toBookMetadata(BookMetadataEntity bookMetadataEntity, boolean includeDescription) {
            throw new UnsupportedOperationException("Not needed for unit test");
        }

        @Override
        public BookMetadata toBookMetadataWithoutRelations(BookMetadataEntity bookMetadataEntity, boolean includeDescription) {
            throw new UnsupportedOperationException("Not needed for unit test");
        }
    }
}
