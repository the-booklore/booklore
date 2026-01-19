package com.adityachandel.booklore.model.dto.request;

import com.adityachandel.booklore.model.enums.BookFileType;
import lombok.Data;

import java.util.List;

@Data
public class PreferredBookFormatOrderRequest {
    private List<BookFileType> preferredBookFormatOrder;
}
