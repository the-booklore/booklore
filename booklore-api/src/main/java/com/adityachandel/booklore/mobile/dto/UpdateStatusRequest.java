package com.adityachandel.booklore.mobile.dto;

import com.adityachandel.booklore.model.enums.ReadStatus;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class UpdateStatusRequest {
    @NotNull(message = "Status is required")
    private ReadStatus status;
}
