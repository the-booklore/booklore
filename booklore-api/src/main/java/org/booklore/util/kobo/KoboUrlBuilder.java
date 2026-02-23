package org.booklore.util.kobo;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;
import org.springframework.web.util.UriComponentsBuilder;

@Component
@Slf4j
@RequiredArgsConstructor
public class KoboUrlBuilder {

    public UriComponentsBuilder baseBuilder() {
        UriComponentsBuilder builder = ServletUriComponentsBuilder
                .fromCurrentContextPath()
                .replacePath("")
                .replaceQuery(null);

        log.debug("Final base URL: {}", builder.build().toUriString());
        return builder;
    }

    public String downloadUrl(String token, Long bookId) {
        return baseBuilder()
                .pathSegment("api", "kobo", token, "v1", "books", "{bookId}", "download")
                .buildAndExpand(bookId)
                .toUriString();
    }

    public String imageUrlTemplate(String token) {
        return baseBuilder()
                .pathSegment("api", "kobo", token, "v1", "books", "{ImageId}", "thumbnail", "{Width}", "{Height}", "false", "image.jpg")
                .build()
                .toUriString();
    }

    public String imageUrlQualityTemplate(String token) {
        return baseBuilder()
                .pathSegment("api", "kobo", token, "v1", "books", "{ImageId}", "thumbnail", "{Width}", "{Height}", "{Quality}", "{IsGreyscale}", "image.jpg")
                .build()
                .toUriString();
    }

    public String librarySyncUrl(String token) {
        return baseBuilder()
                .pathSegment("api", "kobo", token, "v1", "library", "sync")
                .build()
                .toUriString();
    }
}