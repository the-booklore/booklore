package com.adityachandel.booklore.model.dto.response.kobo;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class KoboReadingStateResponse {
    private String requestResult;
    private List<UpdateResult> updateResults;

    @Data
    @Builder
    public static class UpdateResult {
        private String entitlementId;
        private Result currentBookmarkResult;
        private Result statisticsResult;
        private Result statusInfoResult;
    }

    @Data
    @Builder
    public static class Result {
        private String result;

        public static Result success() {
            return Result.builder().result("Success").build();
        }
    }
}
