package com.adityachandel.booklore.model.enums;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

@JsonNaming(PropertyNamingStrategies.UpperCamelCaseStrategy.class)
public enum KoboReadStatus {
    @JsonProperty("ReadyToRead")
    READY_TO_READ,

    @JsonProperty("Finished")
    FINISHED,

    @JsonProperty("Reading")
    READING,
}
