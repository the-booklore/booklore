package org.booklore.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;

import static org.assertj.core.api.Assertions.assertThat;

import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = "booklore.jetty.uri-compliance=LEGACY")
@ActiveProfiles("test")
public class JettyUriComplianceIntegrationTest {

    @LocalServerPort
    private int port;

    @Test
    void shouldAcceptSpecialCharactersInQueryStringWhenLegacyComplianceEnabled() throws Exception {
        try (Socket socket = new Socket("localhost", port)) {
            PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
            BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));

            out.print("GET /api/v1/healthcheck?filter[title]={foo}|bar% HTTP/1.1\r\n");
            out.print("Host: localhost\r\n");
            out.print("Connection: close\r\n");
            out.print("\r\n");
            out.flush();

            String statusLine = in.readLine();

            assertThat(statusLine).isNotNull();
            assertThat(statusLine).doesNotContain("400 Bad Request");
            assertThat(statusLine).matches("^HTTP/1\\.1 (200|401|403|404).*");
        }
    }
}
