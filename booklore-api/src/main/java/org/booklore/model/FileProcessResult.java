package org.booklore.model;

import org.booklore.model.dto.Book;
import org.booklore.model.enums.FileProcessStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.ToString;

@Builder
@Getter
@ToString
@AllArgsConstructor
public class FileProcessResult {
    private final Book book;
    private final FileProcessStatus status;
}
