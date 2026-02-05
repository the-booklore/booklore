package org.booklore.model.dto.request;

import org.booklore.model.dto.LibraryPath;
import org.booklore.model.enums.BookFileType;
import org.booklore.model.enums.IconType;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public class CreateLibraryRequest {
    @NotBlank(message = "Library name must not be empty.")
    private String name;

    @NotBlank(message = "Library icon must not be empty.")
    private String icon;

    @NotNull(message = "Library icon type must not be null.")
    private IconType iconType;

    @NotEmpty(message = "Library paths must not be empty.")
    private List<LibraryPath> paths;

    private boolean watch;
    private List<BookFileType> formatPriority;
    private List<BookFileType> allowedFormats;
}
