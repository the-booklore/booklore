package com.adityachandel.booklore.config.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "app.websocket")
public class WebSocketProperties {

    private int loggingPeriodDays = 30;

    private OutboundChannel outboundChannel = new OutboundChannel();

    @Data
    public static class OutboundChannel {
        private int corePoolSize = 8;
        private int maxPoolSize = 16;
        private int keepAliveSeconds = 60;
    }
}