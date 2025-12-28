package com.adityachandel.booklore.service;

import com.adityachandel.booklore.model.dto.BookloreTelemetry;
import com.adityachandel.booklore.model.dto.settings.AppSettings;
import com.adityachandel.booklore.model.dto.settings.MetadataProviderSettings;
import com.adityachandel.booklore.model.dto.settings.MetadataPublicReviewsSettings;
import com.adityachandel.booklore.model.entity.AppSettingEntity;
import com.adityachandel.booklore.model.entity.LibraryEntity;
import com.adityachandel.booklore.model.enums.BookFileType;
import com.adityachandel.booklore.model.enums.IconType;
import com.adityachandel.booklore.model.enums.LibraryScanMode;
import com.adityachandel.booklore.model.enums.ProvisioningMethod;
import com.adityachandel.booklore.repository.*;
import com.adityachandel.booklore.service.appsettings.AppSettingService;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@AllArgsConstructor
public class TelemetryService {

    private static final String INSTALLATION_ID_KEY = "installation_id";
    private final AppSettingsRepository appSettingsRepository;
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
    private final KoreaderUserRepository koreaderUserRepository;
    private final OpdsUserV2Repository opdsUserV2Repository;

    public BookloreTelemetry collectTelemetry() {
        long totalUsers = userRepository.count();
        long localUsers = userRepository.countByProvisioningMethod(ProvisioningMethod.LOCAL);
        long oidcUsers = userRepository.countByProvisioningMethod(ProvisioningMethod.OIDC);

        AppSettings settings = appSettingService.getAppSettings();

        // Book counts by type
        Map<BookFileType, Long> countByType = new EnumMap<>(BookFileType.class);
        for (BookFileType type : BookFileType.values()) {
            countByType.put(type, bookRepository.countByBookType(type));
        }

        BookloreTelemetry.TBook tBook = BookloreTelemetry.TBook.builder()
                .totalCount(bookRepository.count())
                .countByType(countByType)
                .build();

        // Library details
        List<LibraryEntity> libraries = libraryRepository.findAll();
        List<BookloreTelemetry.TLibrary> tLibraries = libraries.stream().map(lib ->
                BookloreTelemetry.TLibrary.builder()
                        .name(lib.getName())
                        .bookCount(lib.getBookEntities() != null ? lib.getBookEntities().size() : 0)
                        .libraryPathsCount(lib.getLibraryPaths() != null ? lib.getLibraryPaths().size() : 0)
                        .watch(lib.isWatch())
                        .iconType(lib.getIconType())
                        .scanMode(lib.getScanMode())
                        .build()
        ).collect(Collectors.toList());

        return BookloreTelemetry.builder()
                .installationId(getInstallationId())
                .appVersion(versionService.appVersion)
                .libraryCount((int) libraryRepository.count())
                .bookCount(bookRepository.count())
                .additionalBookFilesCount(bookAdditionalFileRepository.count())
                .authorsCount(authorRepository.count())
                .bookMarksCount(bookMarkRepository.count())
                .bookNotesCount(bookNoteRepository.count())
                .shelfCount((int) shelfRepository.count())
                .magicShelfCount((int) magicShelfRepository.count())
                .categoriesCount((int) categoryRepository.count())
                .tagsCount((int) tagRepository.count())
                .moodsCount((int) moodRepository.count())
                .koreaderUsersCount((int) koreaderUserRepository.count())
                .users(BookloreTelemetry.TUsers.builder()
                        .usersCount((int) totalUsers)
                        .localUsersCount((int) localUsers)
                        .oidcUsersCount((int) oidcUsers)
                        .oidcEnabled(oidcUsers > 0)
                        .build())
                .metadata(BookloreTelemetry.TMetadata.builder()
                        .enabledMetadataProviders(getEnabledMetadataProviders(settings.getMetadataProviderSettings()))
                        .enabledReviewMetadataProviders(settings.getMetadataPublicReviewsSettings().getProviders().stream()
                                .filter(MetadataPublicReviewsSettings.ReviewProviderConfig::isEnabled)
                                .map(p -> p.getProvider().name())
                                .collect(Collectors.toList()))
                        .saveMetadataToFile(settings.getMetadataPersistenceSettings().isSaveToOriginalFile())
                        .moveFileViaPattern(settings.getMetadataPersistenceSettings().isMoveFilesToLibraryPattern())
                        .autoBookSearch(settings.isAutoBookSearch())
                        .similarBookRecommendations(settings.isSimilarBookRecommendation())
                        .metadataDownloadOnBookdrop(settings.isMetadataDownloadOnBookdrop())
                        .build())
                .opds(BookloreTelemetry.TOpds.builder()
                        .opdsEnabled(settings.isOpdsServerEnabled())
                        .opdsUsersCount((int) opdsUserV2Repository.count())
                        .build())
                .email(BookloreTelemetry.TEmail.builder()
                        .emailProvidersCount((int) emailProviderV2Repository.count())
                        .emailRecipientsCount((int) emailRecipientV2Repository.count())
                        .build())
                .kobo(BookloreTelemetry.TKobo.builder()
                        .convertToKepub(settings.getKoboSettings().isConvertToKepub())
                        .koboUsersCount((int) koboUserSettingsRepository.count())
                        .hardcoverSyncEnabledCount((int) koboUserSettingsRepository.countByHardcoverSyncEnabledTrue())
                        .autoAddToShelfCount((int) koboUserSettingsRepository.countByAutoAddToShelfTrue())
                        .build())
                .books(tBook)
                .libraries(tLibraries)
                .build();
    }

    private List<String> getEnabledMetadataProviders(MetadataProviderSettings providers) {
        List<String> enabled = new ArrayList<>();
        if (providers.getAmazon() != null && providers.getAmazon().isEnabled()) {
            enabled.add("AMAZON");
        }
        if (providers.getGoogle() != null && providers.getGoogle().isEnabled()) {
            enabled.add("GOOGLE");
        }
        if (providers.getGoodReads() != null && providers.getGoodReads().isEnabled()) {
            enabled.add("GOODREADS");
        }
        if (providers.getHardcover() != null && providers.getHardcover().isEnabled()) {
            enabled.add("HARDCOVER");
        }
        if (providers.getComicvine() != null && providers.getComicvine().isEnabled()) {
            enabled.add("COMICVINE");
        }
        if (providers.getDouban() != null && providers.getDouban().isEnabled()) {
            enabled.add("DOUBAN");
        }
        return enabled;
    }

    private String getInstallationId() {
        AppSettingEntity setting = appSettingsRepository.findByName(INSTALLATION_ID_KEY);
        return setting != null ? setting.getVal() : "unknown";
    }
}
