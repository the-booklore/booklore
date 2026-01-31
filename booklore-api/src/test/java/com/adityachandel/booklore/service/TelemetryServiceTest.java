package com.adityachandel.booklore.service;

import com.adityachandel.booklore.model.dto.BookloreTelemetry;
import com.adityachandel.booklore.model.dto.Installation;
import com.adityachandel.booklore.model.dto.InstallationPing;
import com.adityachandel.booklore.model.dto.settings.*;
import com.adityachandel.booklore.model.entity.LibraryEntity;
import com.adityachandel.booklore.model.entity.LibraryPathEntity;
import com.adityachandel.booklore.model.enums.BookFileType;
import com.adityachandel.booklore.model.enums.IconType;
import com.adityachandel.booklore.model.enums.MetadataProvider;
import com.adityachandel.booklore.model.enums.ProvisioningMethod;
import com.adityachandel.booklore.repository.*;
import com.adityachandel.booklore.service.appsettings.AppSettingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.time.Instant;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TelemetryServiceTest {

    @Mock
    private VersionService versionService;
    @Mock
    private LibraryRepository libraryRepository;
    @Mock
    private BookRepository bookRepository;
    @Mock
    private BookMarkRepository bookMarkRepository;
    @Mock
    private BookNoteRepository bookNoteRepository;
    @Mock
    private BookAdditionalFileRepository bookAdditionalFileRepository;
    @Mock
    private AuthorRepository authorRepository;
    @Mock
    private ShelfRepository shelfRepository;
    @Mock
    private MagicShelfRepository magicShelfRepository;
    @Mock
    private CategoryRepository categoryRepository;
    @Mock
    private TagRepository tagRepository;
    @Mock
    private MoodRepository moodRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private EmailProviderV2Repository emailProviderV2Repository;
    @Mock
    private EmailRecipientV2Repository emailRecipientV2Repository;
    @Mock
    private AppSettingService appSettingService;
    @Mock
    private KoboUserSettingsRepository koboUserSettingsRepository;
    @Mock
    private UserSettingRepository userSettingRepository;
    @Mock
    private KoreaderUserRepository koreaderUserRepository;
    @Mock
    private OpdsUserV2Repository opdsUserV2Repository;
    @Mock
    private InstallationService installationService;

    @InjectMocks
    private TelemetryService telemetryService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        versionService.appVersion = "1.0.0";
    }

    @Test
    void getInstallationPing_returnsPing() {
        Installation installation = new Installation("test-id", Instant.now());
        when(installationService.getOrCreateInstallation()).thenReturn(installation);

        InstallationPing ping = telemetryService.getInstallationPing();

        assertNotNull(ping);
        assertEquals("test-id", ping.getInstallationId());
        assertEquals(installation.getDate(), ping.getInstallationDate());
        assertEquals("1.0.0", ping.getAppVersion());
        assertEquals(1, ping.getPingVersion());
    }

    @Test
    void collectTelemetry_fullData_returnsTelemetry() {
        when(userRepository.count()).thenReturn(10L);
        when(userRepository.countByProvisioningMethod(ProvisioningMethod.LOCAL)).thenReturn(8L);
        when(userRepository.countByProvisioningMethod(ProvisioningMethod.OIDC)).thenReturn(2L);
        when(bookRepository.count()).thenReturn(100L);
        when(libraryRepository.count()).thenReturn(2L);
        when(bookAdditionalFileRepository.count()).thenReturn(5L);
        when(authorRepository.count()).thenReturn(50L);
        when(bookMarkRepository.count()).thenReturn(20L);
        when(bookNoteRepository.count()).thenReturn(15L);
        when(shelfRepository.count()).thenReturn(3L);
        when(magicShelfRepository.count()).thenReturn(4L);
        when(categoryRepository.count()).thenReturn(6L);
        when(tagRepository.count()).thenReturn(7L);
        when(moodRepository.count()).thenReturn(8L);
        when(koreaderUserRepository.count()).thenReturn(9L);
        when(opdsUserV2Repository.count()).thenReturn(11L);
        when(emailProviderV2Repository.count()).thenReturn(12L);
        when(emailRecipientV2Repository.count()).thenReturn(13L);
        when(koboUserSettingsRepository.count()).thenReturn(14L);
        
        AppSettings settings = mock(AppSettings.class);
        when(appSettingService.getAppSettings()).thenReturn(settings);
        
        MetadataProviderSettings providerSettings = mock(MetadataProviderSettings.class);
        when(settings.getMetadataProviderSettings()).thenReturn(providerSettings);
        
        MetadataProviderSettings.Amazon amazon = new MetadataProviderSettings.Amazon();
        amazon.setEnabled(true);
        when(providerSettings.getAmazon()).thenReturn(amazon);
        
        MetadataProviderSettings.Google google = new MetadataProviderSettings.Google();
        google.setEnabled(false);
        when(providerSettings.getGoogle()).thenReturn(google);

        MetadataPublicReviewsSettings reviewSettings = mock(MetadataPublicReviewsSettings.class);
        when(settings.getMetadataPublicReviewsSettings()).thenReturn(reviewSettings);
        when(reviewSettings.getProviders()).thenReturn(Collections.singleton(
                MetadataPublicReviewsSettings.ReviewProviderConfig.builder()
                        .provider(MetadataProvider.GoodReads)
                        .enabled(true)
                        .build()
        ));

        MetadataPersistenceSettings persistenceSettings = mock(MetadataPersistenceSettings.class);
        when(settings.getMetadataPersistenceSettings()).thenReturn(persistenceSettings);
        MetadataPersistenceSettings.SaveToOriginalFile saveToOriginalFile = new MetadataPersistenceSettings.SaveToOriginalFile();
        saveToOriginalFile.setEpub(new MetadataPersistenceSettings.FormatSettings(true, 0));
        when(persistenceSettings.getSaveToOriginalFile()).thenReturn(saveToOriginalFile);
        when(persistenceSettings.isMoveFilesToLibraryPattern()).thenReturn(true);

        when(settings.isAutoBookSearch()).thenReturn(true);
        when(settings.isSimilarBookRecommendation()).thenReturn(false);
        when(settings.isMetadataDownloadOnBookdrop()).thenReturn(true);
        when(settings.isOpdsServerEnabled()).thenReturn(true);
        
        KoboSettings koboSettings = mock(KoboSettings.class);
        when(settings.getKoboSettings()).thenReturn(koboSettings);
        when(koboSettings.isConvertToKepub()).thenReturn(true);

        when(userSettingRepository.countBySettingKeyAndSettingValue(UserSettingKey.HARDCOVER_SYNC_ENABLED.getDbKey(), "true")).thenReturn(5L);
        when(koboUserSettingsRepository.countByAutoAddToShelfTrue()).thenReturn(3L);

        Installation installation = new Installation("inst-1", Instant.now());
        when(installationService.getOrCreateInstallation()).thenReturn(installation);

        LibraryEntity lib = new LibraryEntity();
        lib.setId(1L);
        LibraryPathEntity path = new LibraryPathEntity();
        path.setPath("path/to/lib");
        lib.setLibraryPaths(Collections.singletonList(path));
        lib.setWatch(true);
        lib.setIconType(IconType.PRIME_NG);
        when(libraryRepository.findAll()).thenReturn(List.of(lib));
        when(bookRepository.countByLibraryId(1L)).thenReturn(50L);

        when(bookRepository.countByBookType(BookFileType.EPUB)).thenReturn(60L);

        BookloreTelemetry telemetry = telemetryService.collectTelemetry();

        assertNotNull(telemetry);
        assertEquals(2, telemetry.getTelemetryVersion());
        assertEquals("inst-1", telemetry.getInstallationId());
        assertEquals("1.0.0", telemetry.getAppVersion());
        
        assertEquals(10, telemetry.getUserStatistics().getTotalUsers());
        assertEquals(2, telemetry.getUserStatistics().getTotalOidcUsers());
        assertTrue(telemetry.getUserStatistics().isOidcEnabled());

        assertEquals(100, telemetry.getBookStatistics().getTotalBooks());
        assertEquals(60L, telemetry.getBookStatistics().getBookCountByType().get(BookFileType.EPUB.name()));
        
        assertEquals(1, telemetry.getLibraryStatisticsList().size());
        assertEquals("PRIME_NG", telemetry.getLibraryStatisticsList().get(0).getIconType());

        String[] providers = telemetry.getMetadataStatistics().getEnabledMetadataProviders();
        assertTrue(List.of(providers).contains(MetadataProvider.Amazon.name()));
        assertFalse(List.of(providers).contains(MetadataProvider.Google.name()));

        assertTrue(telemetry.getMetadataStatistics().isSaveMetadataToFile());
        assertTrue(telemetry.getMetadataStatistics().isAutoBookSearchEnabled());
    }
}
