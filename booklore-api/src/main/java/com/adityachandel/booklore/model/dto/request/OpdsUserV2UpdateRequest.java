package com.adityachandel.booklore.model.dto.request;

import com.adityachandel.booklore.model.enums.OpdsSortOption;
import lombok.Data;

@Data
public class OpdsUserV2UpdateRequest {
    private OpdsSortOption sortOption;
}
