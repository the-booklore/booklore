package com.adityachandel.booklore.model.dto.request;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Set;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ShelvesAssignmentRequest {
    private Set<Long> bookIds;
    private Set<Long> shelvesToAssign;
    private Set<Long> shelvesToUnassign;
}
