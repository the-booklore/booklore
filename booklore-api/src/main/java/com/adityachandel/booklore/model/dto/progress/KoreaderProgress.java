package com.adityachandel.booklore.model.dto.progress;

import lombok.Builder;
import lombok.Data;
import lombok.ToString;

@Data
@Builder
@ToString
public class KoreaderProgress {
    private Long timestamp;
    private String document;
    private Float percentage;
    private String progress;
    private String device;
    private String device_id;
}
