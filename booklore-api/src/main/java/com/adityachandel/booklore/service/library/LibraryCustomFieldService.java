package com.adityachandel.booklore.service.library;

import com.adityachandel.booklore.exception.ApiError;
import com.adityachandel.booklore.model.dto.LibraryCustomField;
import com.adityachandel.booklore.model.dto.request.CreateLibraryCustomFieldRequest;
import com.adityachandel.booklore.model.entity.LibraryCustomFieldEntity;
import com.adityachandel.booklore.repository.LibraryCustomFieldRepository;
import com.adityachandel.booklore.repository.LibraryRepository;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.regex.Pattern;

@Service
@AllArgsConstructor
public class LibraryCustomFieldService {

    private static final Pattern NAME_PATTERN = Pattern.compile("^[A-Za-z0-9_-]+$");

    private final LibraryRepository libraryRepository;
    private final LibraryCustomFieldRepository libraryCustomFieldRepository;

    public List<LibraryCustomField> getCustomFields(long libraryId) {
        return libraryCustomFieldRepository.findAllByLibrary_IdOrderByNameAsc(libraryId)
                .stream()
                .map(this::toDto)
                .toList();
    }

    @Transactional
    public LibraryCustomField createCustomField(long libraryId, CreateLibraryCustomFieldRequest request) {
        if (request == null || request.getName() == null || request.getName().isBlank()) {
            throw ApiError.INVALID_INPUT.createException("Custom field name is required");
        }
        if (!NAME_PATTERN.matcher(request.getName()).matches()) {
            throw ApiError.INVALID_INPUT.createException("Custom field name must match ^[A-Za-z0-9_-]+$ (used in placeholders)");
        }
        if (request.getFieldType() == null) {
            throw ApiError.INVALID_INPUT.createException("Custom field type is required");
        }

        var library = libraryRepository.findById(libraryId)
                .orElseThrow(() -> ApiError.LIBRARY_NOT_FOUND.createException(libraryId));

        if (libraryCustomFieldRepository.existsByLibrary_IdAndName(libraryId, request.getName())) {
            throw ApiError.CONFLICT.createException("Custom field already exists: " + request.getName());
        }

        LibraryCustomFieldEntity saved = libraryCustomFieldRepository.save(LibraryCustomFieldEntity.builder()
                .library(library)
                .name(request.getName())
                .fieldType(request.getFieldType())
                .defaultValue(request.getDefaultValue())
                .build());

        return toDto(saved);
    }

    @Transactional
    public void deleteCustomField(long libraryId, long customFieldId) {
        var entity = libraryCustomFieldRepository.findById(customFieldId)
                .orElseThrow(() -> ApiError.GENERIC_NOT_FOUND.createException("Custom field not found: " + customFieldId));

        if (entity.getLibrary() == null || entity.getLibrary().getId() == null || entity.getLibrary().getId() != libraryId) {
            throw ApiError.FORBIDDEN.createException("Custom field does not belong to library");
        }

        libraryCustomFieldRepository.delete(entity);
    }

    private LibraryCustomField toDto(LibraryCustomFieldEntity entity) {
        return LibraryCustomField.builder()
                .id(entity.getId())
                .libraryId(entity.getLibrary() != null ? entity.getLibrary().getId() : null)
                .name(entity.getName())
                .fieldType(entity.getFieldType())
                .defaultValue(entity.getDefaultValue())
                .build();
    }
}
