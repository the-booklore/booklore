package com.adityachandel.booklore.crons;

import com.adityachandel.booklore.config.AppProperties;
import com.adityachandel.booklore.model.dto.BookloreTelemetry;
import com.adityachandel.booklore.model.dto.InstallationPing;
import com.adityachandel.booklore.service.TelemetryService;
import com.adityachandel.booklore.service.appsettings.AppSettingService;
import jakarta.annotation.PostConstruct;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.TimeUnit;

@Service
@AllArgsConstructor
@Slf4j
public class CronService {

    private static final String LAST_TELEMETRY_KEY = "last_telemetry_sent";
    private static final String LAST_PING_KEY = "last_ping_sent";
    private static final long INTERVAL_HOURS = 24;

    private final AppProperties appProperties;
    private final TelemetryService telemetryService;
    private final RestClient restClient;
    private final AppSettingService appSettingService;

    @PostConstruct
    public void initScheduledTasks() {
        checkAndRunTelemetry();
        checkAndRunPing();
    }

    @Scheduled(fixedDelay = 24, timeUnit = TimeUnit.HOURS, initialDelay = 24)
    public void sendTelemetryData() {
        if (appSettingService.getAppSettings().isTelemetryEnabled()) {
            try {
                String url = appProperties.getTelemetry().getBaseUrl() + "/api/v1/ingest";
                BookloreTelemetry telemetry = telemetryService.collectTelemetry();
                postData(url, telemetry);
                appSettingService.saveSetting(LAST_TELEMETRY_KEY, Instant.now().toString());
            } catch (Exception e) {
                log.warn("Failed to up stats: {}", e.getMessage());
            }
        }
    }

    @Scheduled(fixedDelay = 24, timeUnit = TimeUnit.HOURS, initialDelay = 10)
    public void sendPing() {
        try {
            String url = appProperties.getTelemetry().getBaseUrl() + "/api/v1/heartbeat";
            InstallationPing ping = telemetryService.getInstallationPing();
            postData(url, ping);
            appSettingService.saveSetting(LAST_PING_KEY, Instant.now().toString());
        } catch (Exception e) {
            log.warn("Failed to up ping: {}", e.getMessage());
        }
    }

    private void postData(String url, Object body) {
        restClient.post()
                .uri(url)
                .body(body)
                .retrieve()
                .body(String.class);
    }


    private void checkAndRunTelemetry() {
        if (!appSettingService.getAppSettings().isTelemetryEnabled()) {
            return;
        }

        String lastRunStr = appSettingService.getSettingValue(LAST_TELEMETRY_KEY);
        if (shouldRunTask(lastRunStr)) {
            log.info("Running stats on startup (last run: {})", lastRunStr);
            sendTelemetryData();
        }
    }

    private void checkAndRunPing() {
        String lastRunStr = appSettingService.getSettingValue(LAST_PING_KEY);
        if (shouldRunTask(lastRunStr)) {
            log.info("Running ping on startup (last run: {})", lastRunStr);
            sendPing();
        }
    }

    /**
     * Determines if a task should run immediately on startup.
     * Returns false for new installations (no last run recorded) to follow normal schedule.
     * Returns true if more than INTERVAL_HOURS have passed since the last run,
     * preventing data gaps when the server restarts close to scheduled execution time.
     *
     * Example: Telemetry normally runs at 2:00 AM daily. If the server restarts at 1:55 AM,
     * the scheduled task would reset and not run until 2:00 AM the next day (48 hours later).
     * This method checks if 24+ hours have passed since the last run and executes immediately
     * on startup if needed, ensuring data is sent at 1:55 AM instead of waiting another 24 hours.
     */
    private boolean shouldRunTask(String lastRunStr) {
        if (lastRunStr == null || lastRunStr.isEmpty()) {
            return false;
        }
        try {
            Instant lastRun = Instant.parse(lastRunStr);
            Instant threshold = Instant.now().minus(INTERVAL_HOURS, ChronoUnit.HOURS);
            return lastRun.isBefore(threshold);
        } catch (Exception e) {
            log.warn("Failed to parse last run timestamp: {}", e.getMessage());
            return false;
        }
    }
}
