package com.adityachandel.booklore.service.metadata.parser.itunes;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class iTunesSearchRequest {
    private String term;
    private String entity;
    private String country;
    private Integer limit;
}
