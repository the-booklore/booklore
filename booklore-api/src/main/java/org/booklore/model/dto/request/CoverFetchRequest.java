package org.booklore.model.dto.request;

import lombok.Builder;
import lombok.Data;

@Builder
@Data
public class CoverFetchRequest {
    private String isbn;
    private String title;
    private String author;
    private String coverType; // "ebook" or "audiobook"
}
