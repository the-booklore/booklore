package com.adityachandel.booklore.model.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class MetadataBatchProgressNotification {
    private String taskId;
    private int completed;
    private int total;
    private String message;
    private String status;
    private boolean isReview;
}
