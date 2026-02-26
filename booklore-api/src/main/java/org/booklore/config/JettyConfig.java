package org.booklore.config;

import org.eclipse.jetty.http.UriCompliance;
import org.eclipse.jetty.server.HttpConnectionFactory;
import org.eclipse.jetty.server.ServerConnector;
import org.springframework.boot.jetty.JettyWebServerFactory;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class JettyConfig {

    /**
     * Allows the same query-string characters that Tomcat permitted via
     * {@code server.tomcat.relaxed-query-chars: '[,],%,{,},|'}.
     * <p>
     * {@link UriCompliance#LEGACY} enables Jetty's pre-RFC-strict behaviour,
     * permitting brackets, braces, pipes, and percent-encoded sequences that
     * are technically outside RFC 3986 but widely used in practice (e.g.,
     * {@code filter[title]=foo} style query parameters).
     */
    @Bean
    public WebServerFactoryCustomizer<JettyWebServerFactory> jettyUriComplianceCustomizer() {
        return factory -> factory.addServerCustomizers(server -> {
            for (var connector : server.getConnectors()) {
                if (connector instanceof ServerConnector serverConnector) {
                    var httpFactory = serverConnector.getConnectionFactory(HttpConnectionFactory.class);
                    if (httpFactory != null) {
                        httpFactory.getHttpConfiguration().setUriCompliance(UriCompliance.LEGACY);
                    }
                }
            }
        });
    }
}
