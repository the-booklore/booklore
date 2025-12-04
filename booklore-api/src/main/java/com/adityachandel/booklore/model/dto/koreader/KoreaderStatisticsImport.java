package com.adityachandel.booklore.model.dto.koreader;

import lombok.Data;

import java.util.List;

/**
 * Request body for importing KOReader statistics data
 * Matches the format sent by KOInsight plugin
 */
@Data
public class KoreaderStatisticsImport {
    private List<KoreaderBookStatistics> books;
    private List<KoreaderPageStatistic> stats;   // Named "stats" to match KOInsight format
    private String version;                       // Plugin version (e.g., "0.1.0")
}
