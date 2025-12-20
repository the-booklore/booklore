package com.adityachandel.booklore.model.dto.request;

import com.adityachandel.booklore.model.enums.OpdsSortOrder;
import lombok.Data;

@Data
public class OpdsUserV2CreateRequest {
    private String username;
    private String password;
    private OpdsSortOrder sortOrder;
}
