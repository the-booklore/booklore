package com.adityachandel.booklore.model.dto.request;

import com.adityachandel.booklore.model.enums.Lock;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Set;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ToggleAllLockRequest {
    private Set<Long> bookIds;
    private Lock lock;
}
