package com.adityachandel.booklore.service.recommender;

import com.adityachandel.booklore.service.book.BookQueryService;
import com.adityachandel.booklore.service.appsettings.AppSettingService;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class BookRecommendationScheduler {

    private final BookQueryService bookQueryService;
    private final AppSettingService appSettingService;
    private final BookVectorService vectorService;

    private static final int RECOMMENDATION_LIMIT = 25;

    @Scheduled(cron = "0 0 2 * * *")
    @Transactional
    public void updateAllSimilarBooks() {

    }
}