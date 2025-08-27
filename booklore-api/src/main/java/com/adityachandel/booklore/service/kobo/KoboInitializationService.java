package com.adityachandel.booklore.service.kobo;

import com.adityachandel.booklore.model.dto.kobo.KoboResources;
import com.adityachandel.booklore.util.kobo.KoboUrlBuilder;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.util.UriComponentsBuilder;

@Slf4j
@Service
@RequiredArgsConstructor
public class KoboInitializationService {

    private final KoboServerProxy koboServerProxy;
    private final KoboResourcesComponent koboResourcesComponent;
    private final KoboUrlBuilder koboUrlBuilder;

    public ResponseEntity<KoboResources> initialize(String token) throws JsonProcessingException {
        JsonNode resources;

        JsonNode body = null;
        try {
            var response = koboServerProxy.proxyCurrentRequest(null, false);
            body = response != null ? response.getBody() : null;
        } catch (Exception e) {
            log.warn("Failed to get response from Kobo /v1/initialization, fallback to noproxy", e);
        }

        resources = (body != null && body.has("Resources"))
                ? body.get("Resources")
                : koboResourcesComponent.getResources();

        if (resources instanceof ObjectNode objectNode) {
            UriComponentsBuilder baseBuilder = koboUrlBuilder.baseBuilder();

            objectNode.put("image_host", baseBuilder.build().toUriString());
            objectNode.put("image_url_template", koboUrlBuilder.imageUrlTemplate(token));
            objectNode.put("image_url_quality_template", koboUrlBuilder.imageUrlQualityTemplate(token));
        }

        return ResponseEntity.ok()
                .header("x-kobo-apitoken", "e30=")
                .body(new KoboResources(resources));
    }
}