package com.adityachandel.booklore.crons;

import com.adityachandel.booklore.model.dto.BookloreTelemetry;
import com.adityachandel.booklore.service.TelemetryService;
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

    private final TelemetryService telemetryService;
    private final RestClient restClient;

    @Scheduled(fixedDelay = 24, timeUnit = TimeUnit.HOURS, initialDelay = 24)
    public void sendTelemetryData() {
        try {
            log.info("Starting telemetry data collection and submission");
            BookloreTelemetry telemetry = telemetryService.collectTelemetry();
            restClient.post()
                    .uri("https://httpbin.org/post")
                    .body(telemetry)
                    .retrieve()
                    .body(String.class);
        } catch (Exception e) {
            log.error("Failed to send telemetry data", e);
        }
    }
}
