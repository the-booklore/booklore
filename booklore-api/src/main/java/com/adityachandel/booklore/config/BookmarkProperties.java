package com.adityachandel.booklore.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "bookmarks")
@Data
public class BookmarkProperties {
    private int defaultPriority = 3;
    private int minPriority = 1;
    private int maxPriority = 5;
    private int maxNotesLength = 2000;
    private int maxTitleLength = 255;
    private int maxCfiLength = 500;
}