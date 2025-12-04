package com.adityachandel.booklore.model.dto.koreader;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * Represents a reading session on a specific page from KOReader's 'page_stat_data' table
 * Matches the format sent by KOInsight plugin
 */
@Data
public class KoreaderPageStatistic {
    @JsonProperty("book_md5")
    private String bookMd5;          // MD5 hash of the book to match with BookLore's current_hash

    private Integer page;            // Page number

    @JsonProperty("start_time")
    private Long startTime;          // Unix timestamp when reading started

    private Integer duration;        // Duration spent on this page in seconds

    @JsonProperty("total_pages")
    private Integer totalPages;      // Total pages in the book at that time

    @JsonProperty("device_id")
    private String deviceId;         // Device ID (optional, from KOReader plugin)
}
