package com.adityachandel.booklore.model.dto.export;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

/**
 * DTO for exporting user configuration (shelves, magic shelves, settings) as JSON
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConfigurationExportDTO {
    private String version;
    private Instant exportedAt;
    private String username;
    private List<ShelfExportDTO> shelves;
    private List<MagicShelfExportDTO> magicShelves;
    private List<SettingExportDTO> settings;

    /**
     * DTO for exporting a single shelf
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ShelfExportDTO {
        private String name;
        private String icon;
        private String iconType;
        private SortDTO sort;
        private List<String> books; // Book identifiers (id:filename)
    }

    /**
     * DTO for exporting a single magic shelf
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MagicShelfExportDTO {
        private String name;
        private String icon;
        private String iconType;
        private boolean isPublic;
        private String filterJson;
    }

    /**
     * DTO for exporting a single user setting
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SettingExportDTO {
        private String key;
        private String value;
    }

    /**
     * DTO for sort configuration
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SortDTO {
        private String field;
        private String direction; // ASCENDING or DESCENDING
    }
}
