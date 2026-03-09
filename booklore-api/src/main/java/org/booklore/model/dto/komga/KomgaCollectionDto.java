package org.booklore.model.dto.komga;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KomgaCollectionDto {
    private String id;
    private String name;
    private Boolean ordered;
    private Boolean filtered;
    private List<String> seriesIds;
    private String createdDate;
    private String lastModifiedDate;
}
