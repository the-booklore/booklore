package org.booklore.config;

import org.eclipse.jetty.http.UriCompliance;
import org.eclipse.jetty.server.Connector;
import org.eclipse.jetty.server.HttpConfiguration;
import org.eclipse.jetty.server.HttpConnectionFactory;
import org.eclipse.jetty.server.ServerConnector;
import org.springframework.boot.jetty.JettyWebServerFactory;
import org.springframework.boot.jetty.JettyServerCustomizer;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import org.springframework.core.env.Environment;

@Configuration
public class JettyConfig {

    /**
     * Configures Jetty's {@link UriCompliance} mode.
     * <p>
     * By default, this uses {@link UriCompliance#RFC3986} for strict, standards-
     * compliant URI parsing. To relax the rules (for example, to allow the same
     * query-string characters that Tomcat permitted via
     * {@code server.tomcat.relaxed-query-chars: '[,],%,{,},|'}), set the
     * property {@code booklore.jetty.uri-compliance} to {@code LEGACY}.
     * <p>
     * WARNING: {@link UriCompliance#LEGACY} relaxes URI parsing beyond just
     * query strings and can widen the accepted character/encoding set for the
     * whole request target. This can have security implications (for example,
     * ambiguous encodings or inconsistent normalization between components).
     * Use {@code LEGACY} only if you fully understand and accept these risks.
     */
    @Bean
    public WebServerFactoryCustomizer<JettyWebServerFactory> jettyUriComplianceCustomizer(Environment environment) {
        return factory -> factory.addServerCustomizers(server -> {
            // Determine desired URI compliance mode from configuration, defaulting to RFC3986.
            String complianceProperty = environment.getProperty("booklore.jetty.uri-compliance", "RFC3986");
            UriCompliance compliance;
            if ("LEGACY".equalsIgnoreCase(complianceProperty)) {
                compliance = UriCompliance.LEGACY;
            } else {
                // Fall back to strict RFC 3986 compliance for safety.
                compliance = UriCompliance.RFC3986;
            }

            for (var connector : server.getConnectors()) {
                if (connector instanceof ServerConnector serverConnector) {
                    var httpFactory = serverConnector.getConnectionFactory(HttpConnectionFactory.class);
                    if (httpFactory != null) {
                        httpFactory.getHttpConfiguration().setUriCompliance(compliance);
                    }
                }
            }
        });
    }

    @Bean
    public JettyServerCustomizer jettyMemoryCustomizer() {
        return server -> {
            for (Connector connector : server.getConnectors()) {
                if (connector instanceof ServerConnector sc) {
                    // Reduce per-connection buffer allocation
                    HttpConnectionFactory factory = sc.getConnectionFactory(HttpConnectionFactory.class);
                    if (factory != null) {
                        HttpConfiguration config = factory.getHttpConfiguration();
                        config.setOutputBufferSize(16 * 1024);      // default 32KB
                        config.setRequestHeaderSize(8 * 1024);       // default 8KB
                        config.setResponseHeaderSize(4 * 1024);      // default 8KB
                    }
                }
            }
        };
    }
}
