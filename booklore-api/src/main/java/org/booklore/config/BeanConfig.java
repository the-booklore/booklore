package org.booklore.config;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.socket.config.WebSocketMessageBrokerStats;

import java.time.Duration;

@Configuration
public class BeanConfig {

    @Autowired
    private WebSocketMessageBrokerStats webSocketMessageBrokerStats;

    @Bean
    public RestTemplate restTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout((int) Duration.ofSeconds(10).toMillis());
        factory.setReadTimeout((int) Duration.ofSeconds(15).toMillis());
        return new RestTemplate(factory);
    }

    @PostConstruct
    public void init() {
        webSocketMessageBrokerStats.setLoggingPeriod(30 * 24 * 60 * 60 * 1000L); // 30 days
    }

    @Bean(initMethod = "migrate")
    public org.flywaydb.core.Flyway flyway(javax.sql.DataSource dataSource) {
        return org.flywaydb.core.Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .baselineOnMigrate(true)
                .load();
    }
}
