package org.booklore.model.dto.request;

import java.time.Instant;
import java.util.List;

public record PurchaseDateUpdateRequest(List<Long> bookIds, Instant purchaseDate) {
}
