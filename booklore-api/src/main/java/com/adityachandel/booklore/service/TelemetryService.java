package com.adityachandel.booklore.service;

import com.adityachandel.booklore.model.dto.BookloreTelemetry;
import com.adityachandel.booklore.model.dto.Installation;
import com.adityachandel.booklore.model.dto.InstallationPing;
import com.adityachandel.booklore.model.dto.settings.AppSettings;
import com.adityachandel.booklore.model.dto.settings.MetadataProviderSettings;
import com.adityachandel.booklore.model.dto.settings.MetadataPublicReviewsSettings;
import com.adityachandel.booklore.model.dto.settings.UserSettingKey;
import com.adityachandel.booklore.model.entity.LibraryEntity;
import com.adityachandel.booklore.model.enums.BookFileType;
import com.adityachandel.booklore.model.enums.MetadataProvider;
import com.adityachandel.booklore.model.enums.ProvisioningMethod;
import com.adityachandel.booklore.repository.*;
import com.adityachandel.booklore.service.appsettings.AppSettingService;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@AllArgsConstructor
public class TelemetryService {

    private final VersionService versionService;
    private final LibraryRepository libraryRepository;
    private final BookRepository bookRepository;
    private final BookMarkRepository bookMarkRepository;
    private final BookNoteRepository bookNoteRepository;
    private final BookAdditionalFileRepository bookAdditionalFileRepository;
    private final AuthorRepository authorRepository;
    private final ShelfRepository shelfRepository;
    private final MagicShelfRepository magicShelfRepository;
    private final CategoryRepository categoryRepository;
    private final TagRepository tagRepository;
    private final MoodRepository moodRepository;
    private final UserRepository userRepository;
    private final EmailProviderV2Repository emailProviderV2Repository;
    private final EmailRecipientV2Repository emailRecipientV2Repository;
    private final AppSettingService appSettingService;
    private final KoboUserSettingsRepository koboUserSettingsRepository;
    private final UserSettingRepository userSettingRepository;
    private final KoreaderUserRepository koreaderUserRepository;
    private final OpdsUserV2Repository opdsUserV2Repository;
    private final InstallationService installationService;

    public InstallationPing getInstallationPing() {
        Installation installation = installationService.getOrCreateInstallation();

        return InstallationPing.builder()
                .pingVersion(1)
                .appVersion(versionService.appVersion)
                .installationId(installation.getId())
                .installationDate(installation.getDate())
                .build();
    }

    public BookloreTelemetry collectTelemetry() {
        long totalUsers = userRepository.count();
        long localUsers = userRepository.countByProvisioningMethod(ProvisioningMethod.LOCAL);
        long oidcUsers = userRepository.countByProvisioningMethod(ProvisioningMethod.OIDC);

        AppSettings settings = appSettingService.getAppSettings();

        BookloreTelemetry.BookStatistics bookStatistics = BookloreTelemetry.BookStatistics.builder()
                .totalBooks(bookRepository.count())
                .bookCountByType(getBookFileTypeCounts())
                .build();

        List<BookloreTelemetry.LibraryStatistics> libraryStatisticsList = libraryRepository.findAll().stream()
                .map(this::mapLibraryStatistics)
                .collect(Collectors.toList());

        String[] enabledMetadataProviders = getEnabledMetadataProviders(settings.getMetadataProviderSettings());
        String[] enabledReviewMetadataProviders = getEnabledReviewMetadataProviders(settings.getMetadataPublicReviewsSettings());

        Installation installation = installationService.getOrCreateInstallation();

        return BookloreTelemetry.builder()
                .telemetryVersion(2)
                .installationId(installation.getId())
                .installationDate(installation.getDate() != null ? installation.getDate().toString() : null)
                .appVersion(versionService.appVersion)
                .totalLibraries((int) libraryRepository.count())
                .totalBooks(bookRepository.count())
                .totalAdditionalBookFiles(bookAdditionalFileRepository.count())
                .totalAuthors(authorRepository.count())
                .totalBookmarks(bookMarkRepository.count())
                .totalBookNotes(bookNoteRepository.count())
                .totalShelves((int) shelfRepository.count())
                .totalMagicShelves((int) magicShelfRepository.count())
                .totalCategories((int) categoryRepository.count())
                .totalTags((int) tagRepository.count())
                .totalMoods((int) moodRepository.count())
                .totalKoreaderUsers((int) koreaderUserRepository.count())
                .userStatistics(BookloreTelemetry.UserStatistics.builder()
                        .totalUsers((int) totalUsers)
                        .totalLocalUsers((int) localUsers)
                        .totalOidcUsers((int) oidcUsers)
                        .oidcEnabled(oidcUsers > 0)
                        .build())
                .metadataStatistics(BookloreTelemetry.MetadataStatistics.builder()
                        .enabledMetadataProviders(enabledMetadataProviders)
                        .enabledReviewMetadataProviders(enabledReviewMetadataProviders)
                        .saveMetadataToFile(settings.getMetadataPersistenceSettings().getSaveToOriginalFile().isAnyFormatEnabled())
                        .moveFileViaPattern(settings.getMetadataPersistenceSettings().isMoveFilesToLibraryPattern())
                        .autoBookSearchEnabled(settings.isAutoBookSearch())
                        .similarBookRecommendationsEnabled(settings.isSimilarBookRecommendation())
                        .metadataDownloadOnBookdropEnabled(settings.isMetadataDownloadOnBookdrop())
                        .build())
                .opdsStatistics(BookloreTelemetry.OpdsStatistics.builder()
                        .opdsEnabled(settings.isOpdsServerEnabled())
                        .totalOpdsUsers((int) opdsUserV2Repository.count())
                        .build())
                .emailStatistics(BookloreTelemetry.EmailStatistics.builder()
                        .totalEmailProviders((int) emailProviderV2Repository.count())
                        .totalEmailRecipients((int) emailRecipientV2Repository.count())
                        .build())
                .koboStatistics(BookloreTelemetry.KoboStatistics.builder()
                        .convertToKepubEnabled(settings.getKoboSettings().isConvertToKepub())
                        .totalKoboUsers((int) koboUserSettingsRepository.count())
                        .totalHardcoverSyncEnabled((int) userSettingRepository.countBySettingKeyAndSettingValue(
                                UserSettingKey.HARDCOVER_SYNC_ENABLED.getDbKey(), "true"))
                        .totalAutoAddToShelf((int) koboUserSettingsRepository.countByAutoAddToShelfTrue())
                        .build())
                .bookStatistics(bookStatistics)
                .libraryStatisticsList(libraryStatisticsList)
                .build();
    }

    private Map<String, Long> getBookFileTypeCounts() {
        Map<String, Long> countByType = new HashMap<>();
        for (BookFileType type : BookFileType.values()) {
            countByType.put(type.name(), bookRepository.countByBookType(type));
        }
        return countByType;
    }

    private BookloreTelemetry.LibraryStatistics mapLibraryStatistics(LibraryEntity lib) {
        return BookloreTelemetry.LibraryStatistics.builder()
                .totalLibraryPaths(lib.getLibraryPaths() != null ? lib.getLibraryPaths().size() : 0)
                .bookCount(bookRepository.countByLibraryId(lib.getId()))
                .watchEnabled(lib.isWatch())
                .iconType(lib.getIconType() != null ? lib.getIconType().name() : null)
                .build();
    }

    private String[] getEnabledMetadataProviders(MetadataProviderSettings providers) {
        List<String> enabled = new ArrayList<>();
        if (providers.getAmazon() != null && providers.getAmazon().isEnabled())
            enabled.add(MetadataProvider.Amazon.name());
        if (providers.getGoogle() != null && providers.getGoogle().isEnabled())
            enabled.add(MetadataProvider.Google.name());
        if (providers.getGoodReads() != null && providers.getGoodReads().isEnabled())
            enabled.add(MetadataProvider.GoodReads.name());
        if (providers.getHardcover() != null && providers.getHardcover().isEnabled())
            enabled.add(MetadataProvider.Hardcover.name());
        if (providers.getComicvine() != null && providers.getComicvine().isEnabled())
            enabled.add(MetadataProvider.Comicvine.name());
        if (providers.getRanobedb() != null && providers.getRanobedb().isEnabled())
            enabled.add(MetadataProvider.Ranobedb.name());
        if (providers.getDouban() != null && providers.getDouban().isEnabled())
            enabled.add(MetadataProvider.Douban.name());
        if (providers.getLubimyczytac() != null && providers.getLubimyczytac().isEnabled())
            enabled.add(MetadataProvider.Lubimyczytac.name());
        return enabled.toArray(new String[0]);
    }

    private String[] getEnabledReviewMetadataProviders(MetadataPublicReviewsSettings reviewSettings) {
        List<String> enabled = new ArrayList<>();
        if (reviewSettings.getProviders() != null) {
            reviewSettings.getProviders().stream()
                    .filter(MetadataPublicReviewsSettings.ReviewProviderConfig::isEnabled)
                    .forEach(cfg -> enabled.add(cfg.getProvider().name()));
        }
        return enabled.toArray(new String[0]);
    }
}
