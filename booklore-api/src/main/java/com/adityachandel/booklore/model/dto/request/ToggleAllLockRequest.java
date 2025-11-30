package com.adityachandel.booklore.model.dto.request;

import com.adityachandel.booklore.model.enums.Lock;
import lombok.Data;

import java.util.Set;

@Data
public class ToggleAllLockRequest {
    private Set<Long> bookIds;
    private Lock lock;
}
