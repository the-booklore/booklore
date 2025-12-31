package com.adityachandel.booklore.crons;

import com.adityachandel.booklore.config.AppProperties;
import com.adityachandel.booklore.model.dto.BookloreTelemetry;
import com.adityachandel.booklore.model.dto.InstallationPing;
import com.adityachandel.booklore.service.TelemetryService;
import com.adityachandel.booklore.service.appsettings.AppSettingService;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.concurrent.TimeUnit;

@Service
@AllArgsConstructor
@Slf4j
public class CronService {

    private final AppProperties appProperties;
    private final TelemetryService telemetryService;
    private final RestClient restClient;
    private final AppSettingService appSettingService;

    @Scheduled(fixedDelay = 24, timeUnit = TimeUnit.HOURS, initialDelay = 24)
    public void sendTelemetryData() {
        if (appSettingService.getAppSettings().isTelemetryEnabled()) {
            try {
                String url = appProperties.getTelemetry().getBaseUrl() + "/api/v1/ingest";
                BookloreTelemetry telemetry = telemetryService.collectTelemetry();
                postData(url, telemetry);
            } catch (Exception e) {
                log.warn("Failed to send telemetry data: {}", e.getMessage());
            }
        }
    }

    @Scheduled(fixedDelay = 24, timeUnit = TimeUnit.HOURS, initialDelay = 10)
    public void sendPing() {
        try {
            String url = appProperties.getTelemetry().getBaseUrl() + "/api/v1/heartbeat";
            InstallationPing ping = telemetryService.getInstallationPing();
            postData(url, ping);
        } catch (Exception e) {
            log.warn("Failed to send installation ping: {}", e.getMessage());
        }
    }

    private void postData(String url, Object body) {
        restClient.post()
                .uri(url)
                .body(body)
                .retrieve()
                .body(String.class);
    }
}
