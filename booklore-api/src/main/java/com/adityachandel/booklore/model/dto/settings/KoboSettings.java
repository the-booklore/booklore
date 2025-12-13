package com.adityachandel.booklore.model.dto.settings;

import lombok.Builder;
import lombok.Data;

@Builder
@Data
public class KoboSettings {
    private boolean convertToKepub;
    private int conversionLimitInMb;
    private boolean convertCbxToEpub;
    private int conversionLimitInMbForCbx;
    private boolean forceEnableHyphenation;
    private int conversionImageCompressionPercentage;
}
