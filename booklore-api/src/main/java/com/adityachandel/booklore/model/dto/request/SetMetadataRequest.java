package com.adityachandel.booklore.model.dto.request;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class SetMetadataRequest {
    private String googleBookId;
}
