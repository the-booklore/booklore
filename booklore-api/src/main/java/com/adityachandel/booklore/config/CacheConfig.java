package com.adityachandel.booklore.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

@Configuration
@EnableCaching
public class CacheConfig {

    public static final String KOREADER_STATS_SUMMARY = "koreaderStatsSummary";
    public static final String KOREADER_STATS_DAILY = "koreaderStatsDaily";
    public static final String KOREADER_STATS_CALENDAR = "koreaderStatsCalendar";
    public static final String KOREADER_STATS_DAY_OF_WEEK = "koreaderStatsDayOfWeek";

    @Bean
    public CacheManager cacheManager() {
        CaffeineCacheManager cacheManager = new CaffeineCacheManager(
                KOREADER_STATS_SUMMARY,
                KOREADER_STATS_DAILY,
                KOREADER_STATS_CALENDAR,
                KOREADER_STATS_DAY_OF_WEEK
        );
        cacheManager.setCaffeine(Caffeine.newBuilder()
                .maximumSize(100)
                .expireAfterWrite(5, TimeUnit.MINUTES));
        return cacheManager;
    }
}
