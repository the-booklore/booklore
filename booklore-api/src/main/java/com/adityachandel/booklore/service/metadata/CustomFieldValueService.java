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

import static org.apache.commons.lang3.BooleanUtils.isTrue;

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
        if (incomingMetadata == null) {
            return false;
        }

        boolean hasIncomingValues = incomingMetadata.getCustomFields() != null && !incomingMetadata.getCustomFields().isEmpty();
        boolean hasIncomingLocks = incomingMetadata.getCustomFieldLocks() != null && !incomingMetadata.getCustomFieldLocks().isEmpty();
        if (!hasIncomingValues && !hasIncomingLocks) {
            return false;
        }

        Long libraryId = book.getLibrary().getId();
        Long bookId = book.getId();

        List<LibraryCustomFieldEntity> defs = libraryCustomFieldRepository.findAllByLibrary_IdOrderByNameAsc(libraryId);
        if (defs.isEmpty()) {
            return false;
        }

        Map<String, String> incoming = incomingMetadata.getCustomFields() != null ? incomingMetadata.getCustomFields() : Map.of();
        Map<String, Boolean> incomingLocks = incomingMetadata.getCustomFieldLocks() != null ? incomingMetadata.getCustomFieldLocks() : Map.of();
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
            boolean valueProvided = incoming.containsKey(def.getName());
            boolean lockProvided = incomingLocks.containsKey(def.getName());

            if (!valueProvided && !lockProvided) {
                continue;
            }

            BookCustomFieldValueEntity existingValue = existingByFieldId.get(def.getId());

            // Apply value first (respecting existing lock), then lock state.
            boolean isLocked = existingValue != null && isTrue(existingValue.getLocked());


            if (valueProvided && !isLocked) {
                String raw = incoming.get(def.getName());
                if (raw != null && raw.isBlank()) {
                    raw = null;
                }

                // If incoming equals default, we don't need to store a value.
                if (raw != null && def.getDefaultValue() != null && raw.equals(def.getDefaultValue())) {
                    raw = null;
                }

                if (raw == null) {
                    if (existingValue != null && !isTrue(existingValue.getLocked())) {
                        bookCustomFieldValueRepository.delete(existingValue);
                        existingValue = null;
                        changed = true;
                    }
                } else {
                    BookCustomFieldValueEntity toSave = existingValue;
                    if (toSave == null) {
                        toSave = BookCustomFieldValueEntity.builder()
                                .book(book)
                                .customField(def)
                                .locked(false)
                                .build();
                        existingValue = toSave;
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
            }

            if (lockProvided) {
                boolean desiredLocked = Boolean.TRUE.equals(incomingLocks.get(def.getName()));
                boolean currentLocked = existingValue != null && Boolean.TRUE.equals(existingValue.getLocked());
                if (desiredLocked != currentLocked) {
                    BookCustomFieldValueEntity toSave = existingValue;
                    if (toSave == null) {
                        toSave = BookCustomFieldValueEntity.builder()
                                .book(book)
                                .customField(def)
                                .build();
                    }
                    toSave.setLocked(desiredLocked);
                    bookCustomFieldValueRepository.save(toSave);
                    changed = true;
                }
            }
        }

        return changed;
    }

    public boolean hasCustomFieldChanges(BookEntity book, BookMetadata incomingMetadata) {
        if (book == null || book.getId() == null || book.getLibrary() == null || book.getLibrary().getId() == null) {
            return false;
        }
        if (incomingMetadata == null) {
            return false;
        }

        boolean hasIncomingValues = incomingMetadata.getCustomFields() != null && !incomingMetadata.getCustomFields().isEmpty();
        boolean hasIncomingLocks = incomingMetadata.getCustomFieldLocks() != null && !incomingMetadata.getCustomFieldLocks().isEmpty();
        if (!hasIncomingValues && !hasIncomingLocks) {
            return false;
        }

        Long libraryId = book.getLibrary().getId();
        Long bookId = book.getId();

        List<LibraryCustomFieldEntity> defs = libraryCustomFieldRepository.findAllByLibrary_IdOrderByNameAsc(libraryId);
        if (defs.isEmpty()) {
            return false;
        }

        Map<String, String> incoming = incomingMetadata.getCustomFields() != null ? incomingMetadata.getCustomFields() : Map.of();
        Map<String, Boolean> incomingLocks = incomingMetadata.getCustomFieldLocks() != null ? incomingMetadata.getCustomFieldLocks() : Map.of();
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
            boolean valueProvided = incoming.containsKey(def.getName());
            boolean lockProvided = incomingLocks.containsKey(def.getName());

            if (!valueProvided && !lockProvided) {
                continue;
            }
            BookCustomFieldValueEntity existingValue = existingByFieldId.get(def.getId());

            if (lockProvided) {
                boolean desiredLocked = Boolean.TRUE.equals(incomingLocks.get(def.getName()));
                boolean currentLocked = existingValue != null && Boolean.TRUE.equals(existingValue.getLocked());
                if (desiredLocked != currentLocked) {
                    return true;
                }
            }

            if (!valueProvided) {
                continue;
            }

            if (existingValue != null && isTrue(existingValue.getLocked())) {
                // Value changes are ignored while locked (locks are handled above).
                continue;
            }

            String raw = incoming.get(def.getName());
            if (raw != null && raw.isBlank()) {
                raw = null;
            }
            if (raw != null && def.getDefaultValue() != null && raw.equals(def.getDefaultValue())) {
                raw = null;
            }

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
