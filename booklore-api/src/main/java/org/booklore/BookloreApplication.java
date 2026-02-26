package org.booklore;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

import org.booklore.config.BookmarkProperties;

@EnableScheduling
@EnableAsync
@EnableConfigurationProperties(BookmarkProperties.class)
@SpringBootApplication
public class BookloreApplication {

    static {
        System.setProperty("sun.net.http.allowRestrictedHeaders", "true");
    }

    public static void main(String[] args) {
        SpringApplication.run(BookloreApplication.class, args);
    }
}
