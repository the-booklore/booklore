package com.adityachandel.booklore.model.dto.settings;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class MetadataProviderSettings {
    private Amazon amazon;
    private Google google;
    private Goodreads goodReads;
    private Hardcover hardcover;
    private Comicvine comicvine;
    private Douban douban;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Amazon {
        private boolean enabled;
        private String cookie;
        private String domain;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Google {
        private boolean enabled;
        private String language;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Goodreads {
        private boolean enabled;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Hardcover {
        private boolean enabled;
        private String apiKey;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Comicvine {
        private boolean enabled;
        private String apiKey;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Douban {
        private boolean enabled;
    }
}
