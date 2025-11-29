package com.adityachandel.booklore.model.dto.progress;

import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ToString
public class KoreaderProgress {
    private Long timestamp;
    private String document;
    private Float percentage;
    private String progress;
    private String device;
    private String device_id;
}
