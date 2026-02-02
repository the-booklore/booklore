package org.booklore.model.dto.request;

import org.booklore.model.enums.EReaderProfile;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * Request DTO for converting CBZ files to EPUB with custom resolution settings.
 */
@Data
public class CbzConversionRequest {
    
    @NotNull(message = "Book ID is required")
    private Long bookId;
    
    @NotNull(message = "Device profile is required")
    private EReaderProfile deviceProfile;
    
    /**
     * Custom width in pixels (optional, only used when deviceProfile is OTHER)
     */
    @Min(value = 100, message = "Custom width must be at least 100 pixels")
    private Integer customWidth;
    
    /**
     * Custom height in pixels (optional, only used when deviceProfile is OTHER)
     */
    @Min(value = 100, message = "Custom height must be at least 100 pixels")
    private Integer customHeight;
    
    /**
     * Image compression percentage (1-100)
     * Default is 85% to match existing Kobo conversion setting
     */
    @Min(value = 1, message = "Compression percentage must be between 1 and 100")
    private Integer compressionPercentage = 85;
}
