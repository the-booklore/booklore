package org.booklore.model.dto.kobo;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class KoboAnnotation {
    private String id;
    private String type;
    private String highlightedText;
    private String noteText;
    private String highlightColor;
    private String clientLastModifiedUtc;
    private KoboAnnotationLocation location;

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class KoboAnnotationLocation {
        private KoboAnnotationSpan span;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class KoboAnnotationSpan {
        private String chapterFilename;
        private Double chapterProgress;
        private String chapterTitle;
    }
}
