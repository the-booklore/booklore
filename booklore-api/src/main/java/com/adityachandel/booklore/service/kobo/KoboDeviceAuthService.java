package com.adityachandel.booklore.service.kobo;

import com.adityachandel.booklore.model.dto.kobo.KoboAuthentication;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.RandomStringUtils;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class KoboDeviceAuthService {

    private final ObjectMapper objectMapper;

    public ResponseEntity<KoboAuthentication> authenticateDevice(JsonNode requestBody) {
        if (requestBody == null || requestBody.get("UserKey") == null) {
            throw new IllegalArgumentException("UserKey is required");
        }

        log.info("Kobo device authentication request received: {}", requestBody);

        KoboAuthentication auth = new KoboAuthentication();
        auth.setAccessToken(RandomStringUtils.secure().nextAlphanumeric(24));
        auth.setRefreshToken(RandomStringUtils.secure().nextAlphanumeric(24));
        auth.setTrackingId(UUID.randomUUID().toString());
        auth.setUserKey(requestBody.get("UserKey").asText());

        return ResponseEntity.ok()
                .header("Content-Type", "application/json; charset=utf-8")
                .header("Content-Length", String.valueOf(toJsonBytes(auth).length))
                .body(auth);
    }

    public byte[] toJsonBytes(KoboAuthentication KoboAuthentication) {
        try {
            return objectMapper.writeValueAsString(KoboAuthentication).getBytes(StandardCharsets.UTF_8);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize AuthDto to JSON", e);
            throw new RuntimeException("Failed to serialize AuthDto", e);
        }
    }
}