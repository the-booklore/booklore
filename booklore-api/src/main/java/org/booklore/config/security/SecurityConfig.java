package org.booklore.config.security;

import org.booklore.config.security.filter.*;
import org.booklore.config.security.service.OpdsUserDetailsService;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.http.HttpServletResponse;
import lombok.AllArgsConstructor;
import org.booklore.util.FileService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.annotation.web.configurers.HeadersConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.client.SimpleClientHttpRequestFactory;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

import lombok.extern.slf4j.Slf4j;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SNIHostName;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import java.net.InetAddress;
import java.net.Socket;
import java.util.Collections;

@Slf4j
@AllArgsConstructor
@EnableMethodSecurity
@Configuration
public class SecurityConfig {

    private static final Pattern ALLOWED = Pattern.compile("\\s*,\\s*");
    private final OpdsUserDetailsService opdsUserDetailsService;
    private final DualJwtAuthenticationFilter dualJwtAuthenticationFilter;
    private final Environment env;

    private static final String[] COMMON_PUBLIC_ENDPOINTS = {
            "/ws/**",                  // WebSocket connections (auth handled in WebSocketAuthInterceptor)
            "/kobo/**",                // Kobo API requests (auth handled in KoboAuthFilter)
            "/api/v1/auth/**",         // Login and token refresh endpoints (must remain public)
            "/api/v1/public-settings", // Public endpoint for checking OIDC or other app settings
            "/api/v1/setup/**",        // Setup wizard endpoints (must remain accessible before initial setup)
            "/api/v1/healthcheck/**"   // Healthcheck endpoints (must remain accessible for Docker healthchecks)
    };

    private static final String[] COMMON_UNAUTHENTICATED_ENDPOINTS = {
            "/api/v1/opds/search.opds",
            "/api/v2/opds/search.opds"
    };

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    @Order(1)
    public SecurityFilterChain opdsBasicAuthSecurityChain(HttpSecurity http) throws Exception {
        List<String> unauthenticatedEndpoints = new ArrayList<>(Arrays.asList(COMMON_UNAUTHENTICATED_ENDPOINTS));
        http
                .securityMatcher("/api/v1/opds/**", "/api/v2/opds/**")
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(unauthenticatedEndpoints.toArray(new String[0])).permitAll()
                        .anyRequest().authenticated()
                )
                .httpBasic(basic -> basic
                        .realmName("Booklore OPDS")
                        .authenticationEntryPoint((request, response, authException) -> {
                            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                            response.setHeader("WWW-Authenticate", "Basic realm=\"Booklore OPDS\"");
                            response.getWriter().write("HTTP Status 401 - " + authException.getMessage());
                        })
                );

        return http.build();
    }

    @Bean
    @Order(2)
    public SecurityFilterChain komgaBasicAuthSecurityChain(HttpSecurity http) throws Exception {
        http
                .securityMatcher("/komga/api/v1/**", "/komga/api/v2/**")
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .anyRequest().authenticated()
                )
                .httpBasic(basic -> basic
                        .realmName("Booklore Komga API")
                        .authenticationEntryPoint((request, response, authException) -> {
                            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                            response.setHeader("WWW-Authenticate", "Basic realm=\"Booklore Komga API\"");
                            response.getWriter().write("HTTP Status 401 - " + authException.getMessage());
                        })
                );

        return http.build();
    }

    @Bean
    @Order(3)
    public SecurityFilterChain koreaderSecurityChain(HttpSecurity http, KoreaderAuthFilter koreaderAuthFilter) throws Exception {
        http
                .securityMatcher("/api/koreader/**")
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth.anyRequest().authenticated())
                .addFilterBefore(koreaderAuthFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    @Order(3)
    public SecurityFilterChain koboSecurityChain(HttpSecurity http, KoboAuthFilter koboAuthFilter) throws Exception {
        http
                .securityMatcher("/api/kobo/**")
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth.anyRequest().authenticated())
                .addFilterBefore(koboAuthFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    @Order(4)
    public SecurityFilterChain coverJwtApiSecurityChain(HttpSecurity http, CoverJwtFilter coverJwtFilter) throws Exception {
        http
                .securityMatcher("/api/v1/media/**")
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .headers(headers -> headers
                        .cacheControl(HeadersConfigurer.CacheControlConfig::disable)
                )
                .authorizeHttpRequests(auth -> auth
                        .anyRequest().permitAll()
                )
                .addFilterBefore(coverJwtFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(dualJwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    @Order(5)
    public SecurityFilterChain customFontSecurityChain(HttpSecurity http, CustomFontJwtFilter customFontJwtFilter) throws Exception {
        http
                .securityMatcher("/api/v1/custom-fonts/*/file")
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .anyRequest().permitAll()
                )
                .addFilterBefore(customFontJwtFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    @Order(6)
    public SecurityFilterChain epubStreamingSecurityChain(HttpSecurity http, EpubStreamingJwtFilter epubStreamingJwtFilter) throws Exception {
        http
                .securityMatcher("/api/v1/epub/*/file/**")
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .anyRequest().permitAll()
                )
                .addFilterBefore(epubStreamingJwtFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    @Order(7)
    public SecurityFilterChain audiobookStreamingSecurityChain(HttpSecurity http, AudiobookStreamingJwtFilter audiobookStreamingJwtFilter) throws Exception {
        http
                .securityMatcher("/api/v1/audiobook/*/stream/**", "/api/v1/audiobook/*/track/*/stream/**", "/api/v1/audiobook/*/cover")
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .anyRequest().permitAll()
                )
                .addFilterBefore(audiobookStreamingJwtFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    @Order(8)
    public SecurityFilterChain jwtApiSecurityChain(HttpSecurity http) throws Exception {
        List<String> publicEndpoints = new ArrayList<>(Arrays.asList(COMMON_PUBLIC_ENDPOINTS));
        http
                .securityMatcher("/api/**", "/komga/**", "/ws/**")
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .headers(headers -> headers
                        .referrerPolicy(referrer -> referrer.policy(
                                ReferrerPolicyHeaderWriter.ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN))
                )
                .authorizeHttpRequests(auth -> auth
                        .dispatcherTypeMatchers(DispatcherType.ASYNC).permitAll()
                        .requestMatchers(publicEndpoints.toArray(new String[0])).permitAll()
                        .anyRequest().authenticated()
                )
                .addFilterBefore(dualJwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    @Order(9)
    public SecurityFilterChain staticResourcesSecurityChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .headers(headers -> headers
                        .frameOptions(HeadersConfigurer.FrameOptionsConfig::sameOrigin)
                        .referrerPolicy(referrer -> referrer.policy(
                                ReferrerPolicyHeaderWriter.ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN))
                )
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
        return http.build();
    }

    @Bean
    public AuthenticationManager authenticationManager(HttpSecurity http) throws Exception {
        AuthenticationManagerBuilder auth = http.getSharedObject(AuthenticationManagerBuilder.class);
        auth.userDetailsService(opdsUserDetailsService).passwordEncoder(passwordEncoder());
        return auth.build();
    }

    @Bean("noRedirectRestTemplate")
    public RestTemplate noRedirectRestTemplate() {
        return new RestTemplate(
                new SimpleClientHttpRequestFactory() {
                    @Override
                    protected void prepareConnection(HttpURLConnection connection, String httpMethod) throws IOException {
                        super.prepareConnection(connection, httpMethod);
                        connection.setInstanceFollowRedirects(false);
                        if (connection instanceof HttpsURLConnection httpsConnection) {
                            String targetHost = FileService.getTargetHost();
                            if (targetHost != null) {
                                // Set original host for SNI (even if connecting to IP)
                                SSLSocketFactory defaultFactory = httpsConnection.getSSLSocketFactory();
                                httpsConnection.setSSLSocketFactory(new SniSSLSocketFactory(defaultFactory, targetHost));

                                httpsConnection.setHostnameVerifier((hostname, session) -> {
                                    String expectedHost = FileService.getTargetHost();
                                    if (expectedHost != null) {
                                        // Verify certificate against the original expected hostname, even if connecting via IP
                                        return HttpsURLConnection.getDefaultHostnameVerifier().verify(expectedHost, session);
                                    }
                                    // Fallback: use default verifier for the hostname we connected to
                                    return HttpsURLConnection.getDefaultHostnameVerifier().verify(hostname, session);
                                });
                            }
                        }
                    }
                }
        );
    }

    private static class SniSSLSocketFactory extends SSLSocketFactory {
        private final SSLSocketFactory delegate;
        private final String targetHost;

        public SniSSLSocketFactory(SSLSocketFactory delegate, String targetHost) {
            this.delegate = delegate;
            this.targetHost = targetHost;
        }

        @Override
        public String[] getDefaultCipherSuites() { return delegate.getDefaultCipherSuites(); }
        @Override
        public String[] getSupportedCipherSuites() { return delegate.getSupportedCipherSuites(); }

        @Override
        public Socket createSocket(Socket s, String host, int port, boolean autoClose) throws IOException {
            // Pass targetHost instead of host (which is the IP) so the internal SSLSession gets the correct peer host
            Socket socket = delegate.createSocket(s, targetHost, port, autoClose);
            if (socket instanceof SSLSocket sslSocket) {
                SNIHostName serverName = new SNIHostName(targetHost);
                SSLParameters params = sslSocket.getSSLParameters();
                params.setServerNames(Collections.singletonList(serverName));
                // Explicitly set EndpointIdentificationAlgorithm so Java verifies the certificate against targetHost
                params.setEndpointIdentificationAlgorithm("HTTPS");
                sslSocket.setSSLParameters(params);
            }
            return socket;
        }

        @Override public Socket createSocket(String host, int port) throws IOException { return delegate.createSocket(host, port); }
        @Override public Socket createSocket(String host, int port, InetAddress localHost, int localPort) throws IOException { return delegate.createSocket(host, port, localHost, localPort); }
        @Override public Socket createSocket(InetAddress host, int port) throws IOException { return delegate.createSocket(host, port); }
        @Override public Socket createSocket(InetAddress address, int port, InetAddress localAddress, int localPort) throws IOException { return delegate.createSocket(address, port, localAddress, localPort); }
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        
        String allowedOriginsStr = env.getProperty("app.cors.allowed-origins", "*").trim();
        if ("*".equals(allowedOriginsStr) || allowedOriginsStr.isEmpty()) {
            log.warn(
                "CORS is configured to allow all origins (*) because 'app.cors.allowed-origins' is '{}'. " +
                "This maintains backward compatibility, but it's recommended to set it to an explicit origin list.",
                allowedOriginsStr.isEmpty() ? "empty" : "*"
            );
            configuration.setAllowedOriginPatterns(List.of("*"));
        } else {
            List<String> origins = Arrays.stream(ALLOWED.split(allowedOriginsStr))
                    .filter(s -> !s.isEmpty())
                    .map(String::trim)
                    .toList();
            configuration.setAllowedOriginPatterns(origins);
        }

        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH"));
        configuration.setAllowedHeaders(List.of("Authorization", "Cache-Control", "Content-Type", "Range", "If-None-Match"));
        configuration.setExposedHeaders(List.of("Content-Disposition", "Accept-Ranges", "Content-Range", "Content-Length", "ETag", "Date"));
        configuration.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);

        return source;
    }
}
