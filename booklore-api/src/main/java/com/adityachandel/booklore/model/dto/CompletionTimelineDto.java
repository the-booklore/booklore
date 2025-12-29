package com.adityachandel.booklore.model.dto;

import com.adityachandel.booklore.model.enums.ReadStatus;

public interface CompletionTimelineDto {
    Integer getYear();
    Integer getMonth();
    ReadStatus getReadStatus();
    Long getBookCount();
}

