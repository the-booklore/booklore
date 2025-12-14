package com.adityachandel.booklore.model.dto.request;

import java.util.List;

public record PersonalRatingUpdateRequest(List<Long> ids, Integer rating) {
}
