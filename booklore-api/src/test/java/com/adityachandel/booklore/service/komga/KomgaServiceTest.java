package com.adityachandel.booklore.service.komga;

import com.adityachandel.booklore.mapper.komga.KomgaMapper;
import com.adityachandel.booklore.model.dto.komga.KomgaBookDto;
import com.adityachandel.booklore.model.dto.komga.KomgaPageDto;
import com.adityachandel.booklore.model.dto.komga.KomgaPageableDto;
import com.adityachandel.booklore.model.dto.settings.AppSettings;
import com.adityachandel.booklore.model.entity.BookEntity;
import com.adityachandel.booklore.model.entity.BookMetadataEntity;
import com.adityachandel.booklore.model.entity.LibraryEntity;
import com.adityachandel.booklore.model.enums.BookFileType;
import com.adityachandel.booklore.repository.BookRepository;
import com.adityachandel.booklore.repository.LibraryRepository;
import com.adityachandel.booklore.service.MagicShelfService;
import com.adityachandel.booklore.service.appsettings.AppSettingService;
import com.adityachandel.booklore.service.reader.CbxReaderService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class KomgaServiceTest {

    @Mock
    private BookRepository bookRepository;

    @Mock
    private LibraryRepository libraryRepository;

    @Mock
    private KomgaMapper komgaMapper;
    
    @Mock
    private MagicShelfService magicShelfService;
    
    @Mock
    private CbxReaderService cbxReaderService;
    
    @Mock
    private AppSettingService appSettingService;

    @InjectMocks
    private KomgaService komgaService;

    private LibraryEntity library;
    private List<BookEntity> seriesBooks;

    @BeforeEach
    void setUp() {
        library = new LibraryEntity();
        library.setId(1L);
        
        // Mock app settings (lenient because not all tests use this)
        AppSettings appSettings = new AppSettings();
        appSettings.setKomgaGroupUnknown(true);
        lenient().when(appSettingService.getAppSettings()).thenReturn(appSettings);

        // Create multiple books for testing pagination
        seriesBooks = new ArrayList<>();
        for (int i = 1; i <= 50; i++) {
            BookMetadataEntity metadata = BookMetadataEntity.builder()
                    .title("Book " + i)
                    .seriesName("Test Series")
                    .seriesNumber((float) i)
                    .pageCount(null)  // Test null pageCount
                    .build();

            BookEntity book = new BookEntity();
            book.setId((long) i);
            book.setFileName("book-" + i + ".pdf");
            book.setLibrary(library);
            book.setMetadata(metadata);
            book.setBookType(BookFileType.PDF);
            book.setAddedOn(Instant.now());

            seriesBooks.add(book);
        }
    }

    @Test
    void shouldReturnAllBooksWhenUnpagedIsTrue() {
        // Given
        when(bookRepository.findAllWithMetadataByLibraryId(anyLong())).thenReturn(seriesBooks);
        
        // Mock mapper.getBookSeriesName to return "test-series" for all books
        for (BookEntity book : seriesBooks) {
            when(komgaMapper.getBookSeriesName(book)).thenReturn("test-series");
        }
        
        // Mock the mapper to return DTOs
        for (BookEntity book : seriesBooks) {
            KomgaBookDto dto = KomgaBookDto.builder()
                    .id(book.getId().toString())
                    .name(book.getMetadata().getTitle())
                    .build();
            when(komgaMapper.toKomgaBookDto(book)).thenReturn(dto);
        }

        // When: Request with unpaged=true
        KomgaPageableDto<KomgaBookDto> result = komgaService.getBooksBySeries("1-test-series", 0, 20, true);

        // Then: Should return all 50 books
        assertThat(result).isNotNull();
        assertThat(result.getContent()).hasSize(50);
        assertThat(result.getTotalElements()).isEqualTo(50);
        assertThat(result.getTotalPages()).isEqualTo(1);
        assertThat(result.getSize()).isEqualTo(50);
        assertThat(result.getNumber()).isEqualTo(0);
    }

    @Test
    void shouldReturnPagedBooksWhenUnpagedIsFalse() {
        // Given
        when(bookRepository.findAllWithMetadataByLibraryId(anyLong())).thenReturn(seriesBooks);
        
        // Mock mapper.getBookSeriesName to return "test-series" for all books
        for (BookEntity book : seriesBooks) {
            when(komgaMapper.getBookSeriesName(book)).thenReturn("test-series");
        }
        
        // Mock the mapper to return DTOs (only for the books that will be used)
        for (int i = 0; i < 20; i++) {
            BookEntity book = seriesBooks.get(i);
            KomgaBookDto dto = KomgaBookDto.builder()
                    .id(book.getId().toString())
                    .name(book.getMetadata().getTitle())
                    .build();
            when(komgaMapper.toKomgaBookDto(book)).thenReturn(dto);
        }

        // When: Request with unpaged=false and page size 20
        KomgaPageableDto<KomgaBookDto> result = komgaService.getBooksBySeries("1-test-series", 0, 20, false);

        // Then: Should return first page with 20 books
        assertThat(result).isNotNull();
        assertThat(result.getContent()).hasSize(20);
        assertThat(result.getTotalElements()).isEqualTo(50);
        assertThat(result.getTotalPages()).isEqualTo(3);
        assertThat(result.getSize()).isEqualTo(20);
        assertThat(result.getNumber()).isEqualTo(0);
    }

    @Test
    void shouldHandleNullPageCountInGetBookPages() {
        // Given: Book with null pageCount
        BookMetadataEntity metadata = BookMetadataEntity.builder()
                .title("Test Book")
                .pageCount(null)
                .build();

        BookEntity book = new BookEntity();
        book.setId(100L);
        book.setMetadata(metadata);

        when(bookRepository.findById(100L)).thenReturn(Optional.of(book));

        // When: Get book pages
        List<KomgaPageDto> pages = komgaService.getBookPages(100L);

        // Then: Should return empty list without throwing NPE
        assertThat(pages).isNotNull();
        assertThat(pages).isEmpty();
    }

    @Test
    void shouldReturnCorrectPagesWhenPageCountIsValid() {
        // Given: Book with valid pageCount
        BookMetadataEntity metadata = BookMetadataEntity.builder()
                .title("Test Book")
                .pageCount(5)
                .build();

        BookEntity book = new BookEntity();
        book.setId(100L);
        book.setMetadata(metadata);

        when(bookRepository.findById(100L)).thenReturn(Optional.of(book));

        // When: Get book pages
        List<KomgaPageDto> pages = komgaService.getBookPages(100L);

        // Then: Should return 5 pages
        assertThat(pages).isNotNull();
        assertThat(pages).hasSize(5);
        assertThat(pages.get(0).getNumber()).isEqualTo(1);
        assertThat(pages.get(4).getNumber()).isEqualTo(5);
    }
}