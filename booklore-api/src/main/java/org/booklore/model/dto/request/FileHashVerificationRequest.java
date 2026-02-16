package org.booklore.model.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Data;

import java.util.Set;

@Builder
@Data
public class FileHashVerificationRequest {
    @NotNull(message = "Verification type cannot be null")
    private VerificationType verificationType;
    private Long libraryId;
    private Set<Long> bookIds;
    private FileHashVerificationOptions verificationOptions;

    public enum VerificationType {
        BOOKS, LIBRARY
    }
}
