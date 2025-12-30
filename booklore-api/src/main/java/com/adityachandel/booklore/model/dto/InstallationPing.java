package com.adityachandel.booklore.model.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Builder;
import lombok.Getter;

import java.time.Instant;

@Builder
@Getter
public class InstallationPing {
    private int pingVersion;
    private String appVersion;
    private String installationId;
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSXXX", timezone = "UTC")
    private Instant installationDate;
}
