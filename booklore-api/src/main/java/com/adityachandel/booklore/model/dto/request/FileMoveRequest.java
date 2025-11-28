package com.adityachandel.booklore.model.dto.request;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Set;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class FileMoveRequest {
    private Set<Long> bookIds;
    private List<Move> moves;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Move {
        private Long bookId;
        private Long targetLibraryId;
        private Long targetLibraryPathId;
    }
}
