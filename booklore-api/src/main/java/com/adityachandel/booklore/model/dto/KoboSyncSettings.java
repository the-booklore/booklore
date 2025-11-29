package com.adityachandel.booklore.model.dto;


import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class KoboSyncSettings {
    private Long id;
    private String userId;
    private String token;
    private boolean syncEnabled;
    private Float progressMarkAsReadingThreshold;
    private Float progressMarkAsFinishedThreshold;
}
