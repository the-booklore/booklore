package com.adityachandel.booklore.service.metadata.parser.custom;

import lombok.Data;

@Data
public class CustomCoverImage {
    private String url;
    private String size;
    private Integer width;
    private Integer height;
    private String format;
    private String providerId;
}
