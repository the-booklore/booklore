package com.adityachandel.booklore.model.dto.settings;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KoboSettings {
    private boolean convertToKepub;
    private int conversionLimitInMb;
    private boolean convertCbxToEpub;
    private int conversionLimitInMbForCbx;
    private boolean forceEnableHyphenation;
}
