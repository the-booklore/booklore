package org.booklore.service.metadata;

import org.booklore.model.dto.BookMetadata;

import java.util.Set;

@FunctionalInterface
interface FieldValueExtractorList {
    Set<String> extract(BookMetadata metadata);
}
