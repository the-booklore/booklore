package com.adityachandel.booklore.service.metadata;

import com.adityachandel.booklore.exception.ApiError;
import com.adityachandel.booklore.model.dto.BookMetadata;
import com.adityachandel.booklore.model.entity.BookCustomFieldValueEntity;
import com.adityachandel.booklore.model.entity.BookEntity;
import com.adityachandel.booklore.model.entity.LibraryCustomFieldEntity;
import com.adityachandel.booklore.repository.BookCustomFieldValueRepository;
import com.adityachandel.booklore.repository.LibraryCustomFieldRepository;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@AllArgsConstructor
public class CustomFieldValueService {

    private final LibraryCustomFieldRepository libraryCustomFieldRepository;
    private final BookCustomFieldValueRepository bookCustomFieldValueRepository;

    @Transactional
    public boolean applyCustomFields(BookEntity book, BookMetadata incomingMetadata) {
        if (book == null || book.getId() == null || book.getLibrary() == null || book.getLibrary().getId() == null) {
            return false;
        }
        if (incomingMetadata == null || incomingMetadata.getCustomFields() == null || incomingMetadata.getCustomFields().isEmpty()) {
            return false;
        }

        Long libraryId = book.getLibrary().getId();
        Long bookId = book.getId();

        List<LibraryCustomFieldEntity> defs = libraryCustomFieldRepository.findAllByLibrary_IdOrderByNameAsc(libraryId);
        if (defs.isEmpty()) {
            return false;
        }

        Map<String, String> incoming = incomingMetadata.getCustomFields();
        List<BookCustomFieldValueEntity> existing = bookCustomFieldValueRepository.findAllByBook_Id(bookId);
        Map<Long, BookCustomFieldValueEntity> existingByFieldId = new HashMap<>();
        for (BookCustomFieldValueEntity v : existing) {
            if (v.getCustomField() != null && v.getCustomField().getId() != null) {
                existingByFieldId.put(v.getCustomField().getId(), v);
            }
        }

        boolean changed = false;

        for (LibraryCustomFieldEntity def : defs) {
            if (def.getId() == null || def.getName() == null) {
                continue;
            }
            if (!incoming.containsKey(def.getName())) {
                continue;
            }

            String raw = incoming.get(def.getName());
            if (raw != null && raw.isBlank()) {
                raw = null;
            }

            // If incoming equals default, we don't need to store a value.
            if (raw != null && def.getDefaultValue() != null && raw.equals(def.getDefaultValue())) {
                raw = null;
            }

            BookCustomFieldValueEntity existingValue = existingByFieldId.get(def.getId());
            if (raw == null) {
                if (existingValue != null) {
                    bookCustomFieldValueRepository.delete(existingValue);
                    changed = true;
                }
                continue;
            }

            BookCustomFieldValueEntity toSave = existingValue;
            if (toSave == null) {
                toSave = BookCustomFieldValueEntity.builder()
                        .book(book)
                        .customField(def)
                        .build();
            }

            switch (def.getFieldType()) {
                case STRING -> {
                    if (!raw.equals(toSave.getValueString())) {
                        toSave.setValueString(raw);
                        toSave.setValueNumber(null);
                        toSave.setValueDate(null);
                        bookCustomFieldValueRepository.save(toSave);
                        changed = true;
                    }
                }
                case NUMBER -> {
                    Double parsed;
                    try {
                        parsed = Double.valueOf(raw);
                    } catch (NumberFormatException e) {
                        throw ApiError.INVALID_INPUT.createException("Invalid number for custom field '" + def.getName() + "'");
                    }
                    if (toSave.getValueNumber() == null || Double.compare(parsed, toSave.getValueNumber()) != 0) {
                        toSave.setValueString(null);
                        toSave.setValueNumber(parsed);
                        toSave.setValueDate(null);
                        bookCustomFieldValueRepository.save(toSave);
                        changed = true;
                    }
                }
                case DATE -> {
                    LocalDate parsed;
                    try {
                        parsed = LocalDate.parse(raw);
                    } catch (DateTimeParseException e) {
                        throw ApiError.INVALID_INPUT.createException("Invalid date for custom field '" + def.getName() + "' (expected YYYY-MM-DD)");
                    }
                    if (toSave.getValueDate() == null || !parsed.equals(toSave.getValueDate())) {
                        toSave.setValueString(null);
                        toSave.setValueNumber(null);
                        toSave.setValueDate(parsed);
                        bookCustomFieldValueRepository.save(toSave);
                        changed = true;
                    }
                }
            }
        }

        return changed;
    }

    public boolean hasCustomFieldChanges(BookEntity book, BookMetadata incomingMetadata) {
        if (book == null || book.getId() == null || book.getLibrary() == null || book.getLibrary().getId() == null) {
            return false;
        }
        if (incomingMetadata == null || incomingMetadata.getCustomFields() == null || incomingMetadata.getCustomFields().isEmpty()) {
            return false;
        }

        Long libraryId = book.getLibrary().getId();
        Long bookId = book.getId();

        List<LibraryCustomFieldEntity> defs = libraryCustomFieldRepository.findAllByLibrary_IdOrderByNameAsc(libraryId);
        if (defs.isEmpty()) {
            return false;
        }

        Map<String, String> incoming = incomingMetadata.getCustomFields();
        List<BookCustomFieldValueEntity> existing = bookCustomFieldValueRepository.findAllByBook_Id(bookId);
        Map<Long, BookCustomFieldValueEntity> existingByFieldId = new HashMap<>();
        for (BookCustomFieldValueEntity v : existing) {
            if (v.getCustomField() != null && v.getCustomField().getId() != null) {
                existingByFieldId.put(v.getCustomField().getId(), v);
            }
        }

        for (LibraryCustomFieldEntity def : defs) {
            if (def.getId() == null || def.getName() == null) {
                continue;
            }
            if (!incoming.containsKey(def.getName())) {
                continue;
            }

            String raw = incoming.get(def.getName());
            if (raw != null && raw.isBlank()) {
                raw = null;
            }
            if (raw != null && def.getDefaultValue() != null && raw.equals(def.getDefaultValue())) {
                raw = null;
            }

            BookCustomFieldValueEntity existingValue = existingByFieldId.get(def.getId());

            if (raw == null) {
                if (existingValue != null) {
                    return true;
                }
                continue;
            }

            if (existingValue == null) {
                return true;
            }

            return switch (def.getFieldType()) {
                case STRING -> !raw.equals(existingValue.getValueString());
                case NUMBER -> {
                    try {
                        Double parsed = Double.valueOf(raw);
                        yield existingValue.getValueNumber() == null || Double.compare(parsed, existingValue.getValueNumber()) != 0;
                    } catch (NumberFormatException e) {
                        yield true;
                    }
                }
                case DATE -> {
                    try {
                        LocalDate parsed = LocalDate.parse(raw);
                        yield existingValue.getValueDate() == null || !parsed.equals(existingValue.getValueDate());
                    } catch (DateTimeParseException e) {
                        yield true;
                    }
                }
            };
        }

        return false;
    }
}
