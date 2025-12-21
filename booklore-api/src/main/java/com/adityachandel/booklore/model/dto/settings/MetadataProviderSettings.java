package com.adityachandel.booklore.model.dto.settings;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class MetadataProviderSettings {
    private Amazon amazon;
    private Google google;
    private Goodreads goodReads;
    private Hardcover hardcover;
    private Comicvine comicvine;
    private Douban douban;
    private List<CustomProvider> customProviders = new ArrayList<>();

    @Data
    public static class Amazon {
        private boolean enabled;
        private String cookie;
        private String domain;
    }

    @Data
    public static class Google {
        private boolean enabled;
        private String language;
    }

    @Data
    public static class Goodreads {
        private boolean enabled;
    }

    @Data
    public static class Hardcover {
        private boolean enabled;
        private String apiKey;
    }

    @Data
    public static class Comicvine {
        private boolean enabled;
        private String apiKey;
    }

    @Data
    public static class Douban {
        private boolean enabled;
    }

    @Data
    public static class CustomProvider {
        private String id;
        private String providerName;
        private boolean enabled;
        private String baseUrl;
        private String bearerToken;
    }
}
