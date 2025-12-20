package com.adityachandel.booklore.service;

import com.adityachandel.booklore.model.dto.kobo.KoboBookMetadata;
import com.adityachandel.booklore.model.dto.settings.KoboSettings;
import com.adityachandel.booklore.model.entity.BookEntity;
import com.adityachandel.booklore.model.entity.BookMetadataEntity;
import com.adityachandel.booklore.model.enums.BookFileType;
import com.adityachandel.booklore.model.enums.KoboBookFormat;
import com.adityachandel.booklore.service.appsettings.AppSettingService;
import com.adityachandel.booklore.service.book.BookQueryService;
import com.adityachandel.booklore.service.kobo.KoboCompatibilityService;
import com.adityachandel.booklore.service.kobo.KoboEntitlementService;
import com.adityachandel.booklore.util.kobo.KoboUrlBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class KoboEntitlementServiceTest {

    @Mock
    private KoboUrlBuilder koboUrlBuilder;

    @Mock
    private BookQueryService bookQueryService;

    @Mock
    private AppSettingService appSettingService;

    @Mock
    private KoboCompatibilityService koboCompatibilityService;

    @InjectMocks
    private KoboEntitlementService koboEntitlementService;

    @Test
    void getMetadataForBook_shouldUseCompatibilityServiceFilter() {
        long bookId = 1L;
        String token = "test-token";

        BookEntity cbxBook = createCbxBookEntity(bookId);
        when(bookQueryService.findAllWithMetadataByIds(Set.of(bookId)))
                .thenReturn(List.of(cbxBook));
        when(koboCompatibilityService.isBookSupportedForKobo(cbxBook))
                .thenReturn(true);
        when(koboUrlBuilder.downloadUrl(token, bookId))
                .thenReturn("http://test.com/download/" + bookId);
        when(appSettingService.getAppSettings())
                .thenReturn(createAppSettingsWithKoboSettings());

        KoboBookMetadata result = koboEntitlementService.getMetadataForBook(bookId, token);

        assertNotNull(result);
        assertEquals("Test CBX Book", result.getTitle());
        verify(koboCompatibilityService).isBookSupportedForKobo(cbxBook);
    }

    @Test
    void mapToKoboMetadata_cbxBookWithConversionEnabled_shouldReturnEpubFormat() {
        long bookId = 1L;
        BookEntity cbxBook = createCbxBookEntity(bookId);
        String token = "test-token";

        when(bookQueryService.findAllWithMetadataByIds(Set.of(bookId)))
                .thenReturn(List.of(cbxBook));
        when(koboCompatibilityService.isBookSupportedForKobo(cbxBook))
                .thenReturn(true);
        when(koboUrlBuilder.downloadUrl(token, cbxBook.getId()))
                .thenReturn("http://test.com/download/" + cbxBook.getId());
        when(appSettingService.getAppSettings())
                .thenReturn(createAppSettingsWithKoboSettings());

        KoboBookMetadata result = koboEntitlementService.getMetadataForBook(bookId, token);

        assertNotNull(result);
        assertEquals(1, result.getDownloadUrls().size());
        assertEquals(KoboBookFormat.EPUB3.toString(), result.getDownloadUrls().getFirst().getFormat());
    }

    private BookEntity createCbxBookEntity(Long id) {
        BookEntity book = new BookEntity();
        book.setId(id);
        book.setBookType(BookFileType.CBX);
        book.setFileSizeKb(1024L);

        BookMetadataEntity metadata = new BookMetadataEntity();
        metadata.setTitle("Test CBX Book");
        metadata.setDescription("A test CBX comic book");
        metadata.setBookId(id);
        book.setMetadata(metadata);

        return book;
    }

    private com.adityachandel.booklore.model.dto.settings.AppSettings createAppSettingsWithKoboSettings() {
        var appSettings = new com.adityachandel.booklore.model.dto.settings.AppSettings();
        KoboSettings koboSettings = KoboSettings.builder()
                .convertCbxToEpub(true)
                .conversionLimitInMbForCbx(50)
                .convertToKepub(false)
                .conversionLimitInMb(50)
                .build();
        appSettings.setKoboSettings(koboSettings);
        return appSettings;
    }
}