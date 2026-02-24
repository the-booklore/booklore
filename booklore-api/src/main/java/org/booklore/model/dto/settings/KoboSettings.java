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
    @Builder.Default
    private boolean convertCbxToEpub = true;
    private int conversionLimitInMbForCbx;
    private boolean forceEnableHyphenation;
    private int conversionImageCompressionPercentage;
}
