package com.adityachandel.booklore.model.dto.settings;

import lombok.Builder;
import lombok.Data;

@Builder
@Data
public class KoboSettings {
    private boolean convertToKepub;
    private int conversionLimitInMb;
}
