package com.adityachandel.booklore.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class IncompleteSeriesDto {
    private String seriesName;
    private Float maxSeriesNumber;
    private Integer totalBooks;
    private List<Float> presentNumbers;
    private List<Float> missingNumbers;
}
