package com.adityachandel.booklore.model.dto.request;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class OpdsUserV2CreateRequest {
    private String username;
    private String password;
}
