package org.booklore.model.dto.kobo;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.util.List;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class KoboAnnotationPatchRequest {
    private List<String> deletedAnnotationIds;
    private List<KoboAnnotation> updatedAnnotations;
}
