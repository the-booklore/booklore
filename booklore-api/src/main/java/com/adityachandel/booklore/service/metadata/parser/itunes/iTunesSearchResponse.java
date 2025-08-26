package com.adityachandel.booklore.service.metadata.parser.itunes;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class iTunesSearchResponse {
    
    @JsonProperty("resultCount")
    private int resultCount;
    
    @JsonProperty("results")
    private List<iTunesItem> results;
    
    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class iTunesItem {
        @JsonProperty("trackId")
        private Long trackId;
        
        @JsonProperty("trackName")
        private String trackName;
        
        @JsonProperty("artistName")
        private String artistName;
        
        @JsonProperty("artworkUrl100")
        private String artworkUrl100;
        
        @JsonProperty("kind")
        private String kind;
    }
}
