package org.booklore.model.dto.settings;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Builder
@Data
@AllArgsConstructor
@NoArgsConstructor
public class KoboSettings {
    private boolean convertToKepub;
    private int conversionLimitInMb;
    private boolean convertCbxToEpub;
    private int conversionLimitInMbForCbx;
    private boolean forceEnableHyphenation;
    private int conversionImageCompressionPercentage;
}
