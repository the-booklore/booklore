package com.adityachandel.booklore.model.dto.request;

import com.adityachandel.booklore.model.enums.MergeMetadataType;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class MergeMetadataRequest {
    private MergeMetadataType metadataType;
    private List<String> targetValues;
    private List<String> valuesToMerge;
}
