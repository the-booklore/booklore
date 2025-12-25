package com.adityachandel.booklore.config;

import com.adityachandel.booklore.config.properties.WebSocketProperties;
import com.adityachandel.booklore.config.security.interceptor.WebSocketAuthInterceptor;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

@Slf4j
@Configuration
@EnableWebSocketMessageBroker
@AllArgsConstructor
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final WebSocketAuthInterceptor webSocketAuthInterceptor;
    private final WebSocketProperties webSocketProperties;

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        ThreadPoolTaskScheduler brokerTaskScheduler = new ThreadPoolTaskScheduler();
        brokerTaskScheduler.setPoolSize(1);
        brokerTaskScheduler.setThreadNamePrefix("websocket-broker-");
        brokerTaskScheduler.initialize();

        registry.enableSimpleBroker("/queue", "/topic")
                .setHeartbeatValue(new long[]{10000, 10000}) // 10 seconds
                .setTaskScheduler(brokerTaskScheduler);
        registry.setApplicationDestinationPrefixes("/app");
        registry.setUserDestinationPrefix("/user");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns("*"); // Allow all origins for WebSocket
        log.info("WebSocket endpoint registered at /ws");
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(webSocketAuthInterceptor);
    }

    @Override
    public void configureClientOutboundChannel(ChannelRegistration registration) {
        // Set up thread pool for outbound messages to prevent blocking using configurable values
        registration.taskExecutor()
                .corePoolSize(webSocketProperties.getOutboundChannel().getCorePoolSize())
                .maxPoolSize(webSocketProperties.getOutboundChannel().getMaxPoolSize())
                .keepAliveSeconds(webSocketProperties.getOutboundChannel().getKeepAliveSeconds());
    }
}