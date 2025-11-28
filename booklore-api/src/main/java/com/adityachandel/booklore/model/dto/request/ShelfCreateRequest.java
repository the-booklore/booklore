package com.adityachandel.booklore.model.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Null;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Builder
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ShelfCreateRequest {

    @Null(message = "Id should be null for creation.")
    private Long id;

    @NotBlank(message = "Shelf name must not be empty.")
    private String name;

    @NotBlank(message = "Shelf icon must not be empty.")
    private String icon;
}
