package com.adityachandel.booklore.model.dto;

import com.adityachandel.booklore.model.enums.BookFileType;
import com.adityachandel.booklore.model.enums.IconType;
import com.adityachandel.booklore.model.enums.LibraryScanMode;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.util.List;
import java.util.Map;

@Builder
@Setter
@Getter
public class BookloreTelemetry {
    private String installationId;
    private String appVersion;

    private int libraryCount;
    private long bookCount;
    private long additionalBookFilesCount;
    private long authorsCount;
    private long bookNotesCount;
    private long bookMarksCount;
    private int shelfCount;
    private int magicShelfCount;
    private int categoriesCount;
    private int tagsCount;
    private int moodsCount;
    private int koreaderUsersCount;

    private TUsers users;
    private TMetadata metadata;
    private TOpds opds;
    private TKobo kobo;
    private TEmail email;
    private TBook books;
    private List<TLibrary> libraries;

    @Builder
    @Getter
    public static class TUsers {
        private int usersCount;
        private int localUsersCount;
        private int oidcUsersCount;
        private boolean oidcEnabled;
    }

    @Builder
    @Getter
    public static class TMetadata {
        private List<String> enabledMetadataProviders;
        private List<String> enabledReviewMetadataProviders;
        private boolean saveMetadataToFile;
        private boolean moveFileViaPattern;
        private boolean autoBookSearch;
        private boolean similarBookRecommendations;
        private boolean metadataDownloadOnBookdrop;
    }

    @Builder
    @Getter
    public static class TOpds {
        private boolean opdsEnabled;
        private int opdsUsersCount;
    }

    @Builder
    @Getter
    public static class TKobo {
        private int koboUsersCount;
        private int hardcoverSyncEnabledCount;
        private int autoAddToShelfCount;
        private boolean convertToKepub;
    }

    @Builder
    @Getter
    public static class TEmail {
        private int emailProvidersCount;
        private int emailRecipientsCount;
    }

    @Builder
    @Getter
    public static class TBook {
        private long totalCount;
        private Map<BookFileType, Long> countByType;
    }

    @Builder
    @Getter
    public static class TLibrary {
        private String name;
        private long bookCount;
        private int libraryPathsCount;
        private boolean watch;
        private IconType iconType;
        private LibraryScanMode scanMode;
    }
}
