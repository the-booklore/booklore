package com.adityachandel.booklore.service.book;

import com.adityachandel.booklore.exception.APIException;
import com.adityachandel.booklore.model.dto.settings.AppSettings;
import com.adityachandel.booklore.model.entity.BookEntity;
import com.adityachandel.booklore.model.entity.BookFileEntity;
import com.adityachandel.booklore.model.entity.LibraryEntity;
import com.adityachandel.booklore.model.enums.BookFileType;
import com.adityachandel.booklore.service.appsettings.AppSettingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
class PreferredBookFileResolverTest {

    @Mock
    private AppSettingService appSettingService;

    private PreferredBookFileResolver resolver;

    @BeforeEach
    void setUp() {
        resolver = new PreferredBookFileResolver(appSettingService);
        
        // Default mock: return default order from app settings
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
        lenient().when(appSettingService.getAppSettings()).thenReturn(appSettings);
    }

    @Test
    void resolvePrimaryBookFile_shouldReturnEpubOverPdf_whenNoPreferenceSet() {
        BookEntity book = createBookWithFiles(BookFileType.PDF, BookFileType.EPUB);

        BookFileEntity result = resolver.resolvePrimaryBookFile(book);

        assertThat(result.getBookType()).isEqualTo(BookFileType.EPUB);
    }

    @Test
    void resolvePrimaryBookFile_shouldReturnPdf_whenOnlyPdfAvailable() {
        BookEntity book = createBookWithFiles(BookFileType.PDF);

        BookFileEntity result = resolver.resolvePrimaryBookFile(book);

        assertThat(result.getBookType()).isEqualTo(BookFileType.PDF);
    }

    @Test
    void resolvePrimaryBookFile_shouldReturnEpub_whenOnlyEpubAvailable() {
        BookEntity book = createBookWithFiles(BookFileType.EPUB);

        BookFileEntity result = resolver.resolvePrimaryBookFile(book);

        assertThat(result.getBookType()).isEqualTo(BookFileType.EPUB);
    }

    @Test
    void resolvePrimaryBookFile_shouldRespectLibraryPreference() {
        LibraryEntity library = new LibraryEntity();
        library.setPreferredBookFormatOrder(List.of(BookFileType.PDF, BookFileType.EPUB));

        BookEntity book = createBookWithFiles(BookFileType.PDF, BookFileType.EPUB);
        book.setLibrary(library);

        BookFileEntity result = resolver.resolvePrimaryBookFile(book);

        assertThat(result.getBookType()).isEqualTo(BookFileType.PDF);
    }

    @Test
    void resolvePrimaryBookFile_shouldReturnTargetFormat_whenSpecified() {
        BookEntity book = createBookWithFiles(BookFileType.PDF, BookFileType.EPUB);

        BookFileEntity result = resolver.resolvePrimaryBookFile(book, BookFileType.PDF);

        assertThat(result.getBookType()).isEqualTo(BookFileType.PDF);
    }

    @Test
    void resolvePrimaryBookFile_shouldThrowException_whenTargetFormatNotAvailable() {
        BookEntity book = createBookWithFiles(BookFileType.PDF);

        assertThatThrownBy(() -> resolver.resolvePrimaryBookFile(book, BookFileType.EPUB))
                .isInstanceOf(APIException.class)
                .hasMessageContaining("EPUB");
    }

    @Test
    void resolvePrimaryBookFile_shouldThrowException_whenBookIsNull() {
        assertThatThrownBy(() -> resolver.resolvePrimaryBookFile(null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Book not found");
    }

    @Test
    void resolvePrimaryBookFile_shouldThrowException_whenNoBookFiles() {
        BookEntity book = new BookEntity();
        book.setBookFiles(new ArrayList<>());

        assertThatThrownBy(() -> resolver.resolvePrimaryBookFile(book))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Book file not found");
    }

    @Test
    void resolvePrimaryBookFile_shouldFollowDefaultOrder_epubPdfCbx() {
        BookEntity book = createBookWithFiles(BookFileType.CBX, BookFileType.PDF, BookFileType.EPUB);

        BookFileEntity result = resolver.resolvePrimaryBookFile(book);

        assertThat(result.getBookType()).isEqualTo(BookFileType.EPUB);
    }

    @Test
    void resolvePrimaryBookFile_shouldReturnCbx_whenNoEpubOrPdf() {
        BookEntity book = createBookWithFiles(BookFileType.CBX);

        BookFileEntity result = resolver.resolvePrimaryBookFile(book);

        assertThat(result.getBookType()).isEqualTo(BookFileType.CBX);
    }

    @Test
    void resolvePrimaryBookFile_shouldHandleNewFormats_fb2MobiAzw3() {
        BookEntity book = createBookWithFiles(BookFileType.FB2, BookFileType.MOBI, BookFileType.AZW3);

        BookFileEntity result = resolver.resolvePrimaryBookFile(book);

        // FB2 comes before MOBI and AZW3 in default order
        assertThat(result.getBookType()).isEqualTo(BookFileType.FB2);
    }

    private BookEntity createBookWithFiles(BookFileType... types) {
        BookEntity book = new BookEntity();
        book.setId(1L);
        List<BookFileEntity> files = new ArrayList<>();
        
        long fileId = 1L;
        for (BookFileType type : types) {
            BookFileEntity file = new BookFileEntity();
            file.setId(fileId++);
            file.setBook(book);
            file.setBookType(type);
            file.setBookFormat(true);
            file.setFileName("test." + type.name().toLowerCase());
            file.setFileSubPath("/test");
            files.add(file);
        }
        
        book.setBookFiles(files);
        return book;
    }
}
