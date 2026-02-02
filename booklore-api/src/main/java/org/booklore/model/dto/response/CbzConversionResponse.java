package org.booklore.model.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Response DTO for CBZ to EPUB conversion result.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CbzConversionResponse {
    
    /**
     * ID of the newly created EPUB book
     */
    private Long newBookId;
    
    /**
     * Filename of the converted EPUB
     */
    private String fileName;
    
    /**
     * File size in kilobytes
     */
    private Long fileSizeKb;
    
    /**
     * Target width used for conversion
     */
    private Integer targetWidth;
    
    /**
     * Target height used for conversion
     */
    private Integer targetHeight;
    
    /**
     * Number of pages converted
     */
    private Integer pageCount;
    
    /**
     * Conversion success message
     */
    private String message;
}
