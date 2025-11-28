package com.adityachandel.booklore.model.dto.request;

import com.adityachandel.booklore.model.enums.MergeMetadataType;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DeleteMetadataRequest {
    @NotNull
    private MergeMetadataType metadataType;

    @NotEmpty
    private List<String> valuesToDelete;
}

