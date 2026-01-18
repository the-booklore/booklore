package com.adityachandel.booklore.model.dto.settings;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MetadataPersistenceSettings {
    private SaveToOriginalFile saveToOriginalFile;
    private boolean convertCbrCb7ToCbz;
    private boolean moveFilesToLibraryPattern;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SaveToOriginalFile {
        private FormatSettings epub;
        private FormatSettings pdf;
        private FormatSettings cbx;

        public boolean isAnyFormatEnabled() {
            return (epub != null && epub.isEnabled())
                    || (pdf != null && pdf.isEnabled())
                    || (cbx != null && cbx.isEnabled());
        }
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FormatSettings {
        private boolean enabled;
        private int maxFileSizeInMb;
    }
}
