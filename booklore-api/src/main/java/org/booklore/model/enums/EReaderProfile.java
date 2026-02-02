package org.booklore.model.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * E-reader device profiles with screen resolutions for CBZ to EPUB conversion.
 * Based on KCC (Kindle Comic Converter) device profiles.
 */
@Getter
@RequiredArgsConstructor
public enum EReaderProfile {
    // Kindle devices
    KINDLE("Kindle", 600, 800),
    KINDLE_DX("Kindle DX", 824, 1200),
    KINDLE_PAPERWHITE("Kindle Paperwhite", 1072, 1448),
    KINDLE_PAPERWHITE_5("Kindle Paperwhite 5", 1236, 1648),
    KINDLE_VOYAGE("Kindle Voyage", 1072, 1448),
    KINDLE_OASIS("Kindle Oasis", 1264, 1680),
    KINDLE_SCRIBE("Kindle Scribe", 1860, 2480),
    
    // Kobo devices
    KOBO_AURA("Kobo Aura", 1404, 1872),
    KOBO_AURA_HD("Kobo Aura HD", 1080, 1440),
    KOBO_AURA_H2O("Kobo Aura H2O", 1080, 1430),
    KOBO_AURA_ONE("Kobo Aura ONE", 1404, 1872),
    KOBO_CLARA("Kobo Clara", 1072, 1448),
    KOBO_CLARA_HD("Kobo Clara HD", 1072, 1448),
    KOBO_ELIPSA("Kobo Elipsa", 1404, 1872),
    KOBO_FORMA("Kobo Forma", 1440, 1920),
    KOBO_GLO("Kobo Glo", 1024, 768),
    KOBO_GLO_HD("Kobo Glo HD", 1072, 1448),
    KOBO_LIBRA("Kobo Libra", 1264, 1680),
    KOBO_LIBRA_H2O("Kobo Libra H2O", 1264, 1680),
    KOBO_NIA("Kobo Nia", 758, 1024),
    KOBO_SAGE("Kobo Sage", 1440, 1920),
    
    // Other devices
    NOOK_SIMPLE_TOUCH("Nook Simple Touch", 600, 800),
    NOOK_GLOWLIGHT("Nook GlowLight", 1024, 758),
    NOOK_GLOWLIGHT_PLUS("Nook GlowLight Plus", 1072, 1448),
    SONY_PRS_T3("Sony PRS-T3", 758, 1024),
    TOLINO_SHINE("Tolino Shine", 758, 1024),
    TOLINO_VISION("Tolino Vision", 1072, 1448),
    POCKETBOOK_TOUCH_HD("PocketBook Touch HD", 1072, 1448),
    POCKETBOOK_INKPAD("PocketBook InkPad", 1200, 1600),
    
    // Generic profiles
    OTHER("Other (specify custom)", 0, 0);
    
    private final String displayName;
    private final int width;
    private final int height;
    
    public boolean supportsCustomResolution() {
        return this == OTHER;
    }
}
