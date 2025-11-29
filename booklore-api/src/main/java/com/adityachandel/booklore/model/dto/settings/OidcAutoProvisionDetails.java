package com.adityachandel.booklore.model.dto.settings;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class OidcAutoProvisionDetails {
    private boolean enableAutoProvisioning;
    private List<String> defaultPermissions;
    private List<Long> defaultLibraryIds;
}
