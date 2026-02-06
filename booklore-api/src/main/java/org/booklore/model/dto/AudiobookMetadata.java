package org.booklore.model.dto;

import lombok.*;

import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AudiobookMetadata {
    private Long durationSeconds;
    private Integer bitrate;
    private Integer sampleRate;
    private Integer channels;
    private String codec;
    private Integer chapterCount;
    private List<ChapterInfo> chapters;

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ChapterInfo {
        private Integer index;
        private String title;
        private Long startTimeMs;
        private Long endTimeMs;
        private Long durationMs;
    }
}
