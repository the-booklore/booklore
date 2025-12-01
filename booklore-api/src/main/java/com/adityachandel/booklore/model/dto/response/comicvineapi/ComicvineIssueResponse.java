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
        private List<PersonCredit> personCredits;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class PersonCredit {
        private long id;
        private String name;
        private String role;
    }
}