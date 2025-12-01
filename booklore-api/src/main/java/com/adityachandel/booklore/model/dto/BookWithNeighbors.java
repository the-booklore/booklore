package com.adityachandel.booklore.model.dto;

import lombok.Builder;
import lombok.Data;

@Builder
@Data
public class BookWithNeighbors {
    private Book currentBook;
    private Long previousBookId;
    private Long nextBookId;
}
