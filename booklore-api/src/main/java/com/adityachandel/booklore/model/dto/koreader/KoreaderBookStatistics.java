package com.adityachandel.booklore.model.dto.koreader;

import lombok.Data;

/**
 * Represents book metadata and aggregate statistics from KOReader's 'book' table
 */
@Data
public class KoreaderBookStatistics {
    private String title;
    private String authors;
    private String md5;
    private String series;
    private String language;
    private Integer pages;
    private Integer highlights;
    private Integer notes;
    private Long lastOpen;           // Unix timestamp
    private Integer totalReadTime;   // Total reading time in seconds
    private Integer totalReadPages;  // Total pages read
}
