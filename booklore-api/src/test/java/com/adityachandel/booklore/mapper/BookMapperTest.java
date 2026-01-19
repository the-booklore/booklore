package com.adityachandel.booklore.mapper;

import com.adityachandel.booklore.model.dto.Book;
import com.adityachandel.booklore.model.dto.settings.AppSettings;
import com.adityachandel.booklore.model.entity.BookFileEntity;
import com.adityachandel.booklore.model.entity.BookEntity;
import com.adityachandel.booklore.model.entity.LibraryEntity;
import com.adityachandel.booklore.model.entity.LibraryPathEntity;
import com.adityachandel.booklore.model.enums.BookFileType;
import com.adityachandel.booklore.service.appsettings.AppSettingService;
import com.adityachandel.booklore.service.book.PreferredBookFileResolver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BookMapperTest {

    @Mock
    private AppSettingService appSettingService;

    private PreferredBookFileResolver preferredBookFileResolver;

    private BookMapperImpl mapper;

    @BeforeEach
    void setUp() {
        // Setup mock AppSettingService
        AppSettings appSettings = AppSettings.builder()
                .preferredBookFormatOrder(List.of(
                        BookFileType.EPUB,
                        BookFileType.PDF,
                        BookFileType.CBX,
                        BookFileType.FB2,
                        BookFileType.MOBI,
                        BookFileType.AZW3
                ))
                .build();
        when(appSettingService.getAppSettings()).thenReturn(appSettings);
        
        // Create resolver with mocked AppSettingService
        preferredBookFileResolver = new PreferredBookFileResolver(appSettingService);
        
        // Create mapper and inject the resolver
        mapper = new BookMapperImpl();
        try {
            java.lang.reflect.Field field = BookMapper.class.getDeclaredField("preferredBookFileResolver");
            field.setAccessible(true);
            field.set(mapper, preferredBookFileResolver);
        } catch (Exception e) {
            throw new RuntimeException("Failed to inject PreferredBookFileResolver", e);
        }
    }

    @Test
    void shouldMapExistingFieldsCorrectly() {
        LibraryEntity library = new LibraryEntity();
        library.setId(123L);
        library.setName("Test Library");

        LibraryPathEntity libraryPath = new LibraryPathEntity();
        libraryPath.setId(1L);
        libraryPath.setPath("/tmp");

        BookEntity entity = new BookEntity();
        entity.setId(1L);

        BookFileEntity primaryFile = new BookFileEntity();
        primaryFile.setBook(entity);
        primaryFile.setFileName("Test Book");
        primaryFile.setFileSubPath(".");
        primaryFile.setBookFormat(true);
        primaryFile.setBookType(BookFileType.EPUB);
        entity.setBookFiles(List.of(primaryFile));
        entity.setLibrary(library);
        entity.setLibraryPath(libraryPath);

        Book dto = mapper.toBook(entity);

        assertThat(dto).isNotNull();
        assertThat(dto.getId()).isEqualTo(1L);
        assertThat(dto.getLibraryId()).isEqualTo(123L);
        assertThat(dto.getLibraryName()).isEqualTo("Test Library");
        assertThat(dto.getLibraryPath()).isNotNull();
        assertThat(dto.getLibraryPath().getId()).isEqualTo(1L);
        assertThat(dto.getBookType()).isEqualTo(BookFileType.EPUB);
        assertThat(dto.getAlternativeFormats()).isEmpty();
        assertThat(dto.getSupplementaryFiles()).isEmpty();

    }
}