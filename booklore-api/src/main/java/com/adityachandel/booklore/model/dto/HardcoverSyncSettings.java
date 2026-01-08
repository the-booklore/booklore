package com.adityachandel.booklore.model.dto;

import lombok.Data;

@Data
public class HardcoverSyncSettings {
    private String hardcoverApiKey;
    private boolean hardcoverSyncEnabled;
}
