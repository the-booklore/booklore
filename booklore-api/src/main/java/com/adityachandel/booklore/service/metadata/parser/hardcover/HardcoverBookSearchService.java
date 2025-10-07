package com.adityachandel.booklore.service.metadata.parser.hardcover;

import com.adityachandel.booklore.service.appsettings.AppSettingService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.Collections;
import java.util.List;

@Slf4j
@Service
public class HardcoverBookSearchService {

    private final RestClient restClient;
    private final AppSettingService appSettingService;

    @Autowired
    public HardcoverBookSearchService(AppSettingService appSettingService) {
        this.appSettingService = appSettingService;
        String apiUrl = "https://api.hardcover.app/v1/graphql";
        this.restClient = RestClient.builder()
                .baseUrl(apiUrl)
                .build();
    }

    public List<GraphQLResponse.Hit> searchBooks(String query) {
        String apiToken = appSettingService.getAppSettings().getMetadataProviderSettings().getHardcover().getApiKey();
        if (apiToken == null || apiToken.isEmpty()) {
            log.warn("Hardcover API token not set");
            return Collections.emptyList();
        }

        String graphqlQuery = String.format(
                "query SearchBooks { search(query: \"%s\", query_type: \"Book\", per_page: 5, page: 1) { results } }",
                query.replace("\"", "\\\"")
        );

        GraphQLRequest body = new GraphQLRequest();
        body.setQuery(graphqlQuery);
        body.setVariables(Collections.emptyMap());

        try {
            GraphQLResponse response = restClient.post()
                    .uri("")
                    .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiToken)
                    .body(body)
                    .retrieve()
                    .body(GraphQLResponse.class);

            if (response == null || response.getData() == null || response.getData().getSearch() == null || response.getData().getSearch().getResults() == null) {
                log.warn("Empty or malformed response from Hardcover API");
                return Collections.emptyList();
            }

            return response.getData().getSearch().getResults().getHits();

        } catch (RestClientException e) {
            log.error("Failed to fetch data from Hardcover API, Error: {}", e.getMessage());
            return Collections.emptyList();
        }
    }
}