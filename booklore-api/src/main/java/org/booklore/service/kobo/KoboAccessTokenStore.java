package org.booklore.service.kobo;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
public class KoboAccessTokenStore {

    private final ConcurrentHashMap<String, String> deviceIdToSyncToken = new ConcurrentHashMap<>();

    public void store(String deviceId, String syncToken) {
        String existing = deviceIdToSyncToken.put(deviceId, syncToken);
        if (existing == null) {
            log.info("Stored Kobo device mapping: {}... -> {} (store size: {})",
                    deviceId.substring(0, Math.min(deviceId.length(), 12)),
                    syncToken,
                    deviceIdToSyncToken.size());
        }
    }

    public String getSyncToken(String deviceId) {
        return deviceIdToSyncToken.get(deviceId);
    }

    public int size() {
        return deviceIdToSyncToken.size();
    }
}
