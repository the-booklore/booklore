package org.booklore.model.dto.request;

import jakarta.validation.constraints.NotEmpty;
import java.time.Instant;
import java.util.List;

public record PurchaseDateUpdateRequest(@NotEmpty List<Long> bookIds, Instant purchaseDate) {
}
