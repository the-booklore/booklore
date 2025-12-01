package com.adityachandel.booklore.config.security;

import com.adityachandel.booklore.BookloreApplication;
import dasniko.testcontainers.keycloak.KeycloakContainer;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MariaDBContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.MountableFile;

import java.time.Duration;

@SpringBootTest(
    classes = {BookloreApplication.class},
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
        "app.force-disable-oidc=false",
        "spring.flyway.enabled=true",
        "app.bookdrop-folder=/tmp/booklore-test-bookdrop",
        "booklore.security.oidc.jwt.enable-replay-prevention=false",
        "booklore.security.oidc.jwt.clock-skew=60s"
    }
)
@ActiveProfiles("test")
@Testcontainers
public abstract class AbstractIntegrationTest {

    static final MariaDBContainer<?> mariadb = new MariaDBContainer<>("mariadb:11.4")
            .withDatabaseName("booklore")
            .withUsername("booklore")
            .withPassword("booklore")
            .withReuse(true);

    static final KeycloakContainer keycloak = new KeycloakContainer("quay.io/keycloak/keycloak:24.0.0")
            .withCopyFileToContainer(MountableFile.forClasspathResource("test-realm.json"), "/opt/keycloak/data/import/test-realm.json")
            .withStartupTimeout(Duration.ofSeconds(300))
            .waitingFor(Wait.forHttp("/health/ready").forPort(8080))
            .withReuse(true);

    static {
        mariadb.start();
        keycloak.start();
    }

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", mariadb::getJdbcUrl);
        registry.add("spring.datasource.username", mariadb::getUsername);
        registry.add("spring.datasource.password", mariadb::getPassword);
        
        registry.add("booklore.security.oidc.issuer-uri", () -> 
            "http://localhost:" + keycloak.getHttpPort() + "/realms/test-realm");
        registry.add("booklore.security.oidc.internal-issuer-uri", () -> 
            "http://localhost:" + keycloak.getHttpPort() + "/realms/test-realm");
    }
}
