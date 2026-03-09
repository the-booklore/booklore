package org.booklore.model.dto.komga;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;



@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class KomgaPageableDto<T> {

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    static public class Sort {

        @Builder.Default
        private Boolean sorted = false;

        @Builder.Default
        private Boolean unsorted = true;

        @Builder.Default
        private Boolean empty = true;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    static public class Pageable {

        @Builder.Default
        private Sort sort = new Sort();

        @Builder.Default
        private Integer pageNumber = 0;

        @Builder.Default
        private Integer pageSize = 0;

        @Builder.Default
        private Integer offset = 0;

        @Builder.Default
        private Boolean paged = false;

        @Builder.Default
        private Boolean unpaged = true;
    }


    private List<T> content;
    private Integer number;
    private Integer size;
    private Integer numberOfElements;
    private Integer totalElements;
    private Integer totalPages;
    private Boolean first;
    private Boolean last;
    private Boolean empty;

    private Pageable pageable;
    private Sort sort;
}
