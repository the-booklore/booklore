package com.adityachandel.booklore.model.dto.response.comicvineapi;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class ComicvineIssueResponse {
    private String error;
    private int limit;
    private int offset;
    private int number_of_page_results;
    private int number_of_total_results;
    private int status_code;
    private IssueResults results;
    private String version;

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class IssueResults {
        @JsonProperty("person_credits")
        private List<Comic.PersonCredit> personCredits;

        private String description;

        private String name;

        @JsonProperty("issue_number")
        private String issueNumber;

        @JsonProperty("cover_date")
        private String coverDate;

        private Comic.Image image;

        private Comic.Volume volume;

        @JsonProperty("api_detail_url")
        private String apiDetailUrl;

        @JsonProperty("site_detail_url")
        private String siteDetailUrl;
    }
}