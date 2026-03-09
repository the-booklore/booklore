package org.booklore.service.metadata;

import org.booklore.model.dto.CoverImage;
import org.booklore.model.dto.request.CoverFetchRequest;

import java.util.List;

public interface BookCoverProvider {
    List<CoverImage> getCovers(CoverFetchRequest request);
}

