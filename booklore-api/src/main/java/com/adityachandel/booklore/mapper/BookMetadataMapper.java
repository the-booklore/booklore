package com.adityachandel.booklore.mapper;

import com.adityachandel.booklore.model.entity.BookCustomFieldValueEntity;
import com.adityachandel.booklore.model.dto.BookMetadata;
import com.adityachandel.booklore.model.entity.BookMetadataEntity;
import com.adityachandel.booklore.repository.BookCustomFieldValueRepository;
import com.adityachandel.booklore.repository.LibraryCustomFieldRepository;
import org.mapstruct.*;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Mapper(componentModel = "spring", uses = {AuthorMapper.class, CategoryMapper.class, MoodMapper.class, TagMapper.class}, unmappedTargetPolicy = ReportingPolicy.IGNORE)
public abstract class BookMetadataMapper {

    @Autowired
    private LibraryCustomFieldRepository libraryCustomFieldRepository;

    @Autowired
    private BookCustomFieldValueRepository bookCustomFieldValueRepository;

    @AfterMapping
    protected void mapWithDescriptionCondition(BookMetadataEntity bookMetadataEntity, @MappingTarget BookMetadata bookMetadata, @Context boolean includeDescription) {
        if (includeDescription) {
            bookMetadata.setDescription(bookMetadataEntity.getDescription());
        } else {
            bookMetadata.setDescription(null);
        }
    }

    @AfterMapping
    protected void mapCustomFields(BookMetadataEntity bookMetadataEntity, @MappingTarget BookMetadata bookMetadata) {
        if (bookMetadataEntity == null || bookMetadataEntity.getBook() == null || bookMetadataEntity.getBook().getLibrary() == null) {
            return;
        }

        Long libraryId = bookMetadataEntity.getBook().getLibrary().getId();
        if (libraryId == null) {
            return;
        }

        var defs = libraryCustomFieldRepository.findAllByLibrary_IdOrderByNameAsc(libraryId);
        if (defs.isEmpty()) {
            return;
        }

        List<BookCustomFieldValueEntity> values = bookCustomFieldValueRepository.findAllByBook_Id(bookMetadataEntity.getBookId());
        Map<Long, BookCustomFieldValueEntity> valuesByFieldId = values.stream()
                .filter(v -> v.getCustomField() != null && v.getCustomField().getId() != null)
                .collect(Collectors.toMap(v -> v.getCustomField().getId(), v -> v, (a, b) -> a));

        Map<String, String> customFields = new LinkedHashMap<>();
        Map<String, Boolean> customFieldLocks = new LinkedHashMap<>();
        defs.forEach(def -> {
            if (def.getName() == null) {
                return;
            }
            BookCustomFieldValueEntity valueEntity = valuesByFieldId.get(def.getId());
            String value = null;
            if (valueEntity != null) {
                value = switch (def.getFieldType()) {
                    case STRING -> valueEntity.getValueString();
                    case NUMBER -> valueEntity.getValueNumber() != null ? valueEntity.getValueNumber().toString() : null;
                    case DATE -> valueEntity.getValueDate() != null ? valueEntity.getValueDate().toString() : null;
                };
            }
            if (value == null) {
                value = def.getDefaultValue();
            }
            customFields.put(def.getName(), value);

            boolean locked = valueEntity != null && Boolean.TRUE.equals(valueEntity.getLocked());
            customFieldLocks.put(def.getName(), locked);
        });

        // Remove null entries so we don't spam empty keys in payloads.
        Map<String, String> nonNullCustomFields = customFields.entrySet().stream()
                .filter(e -> e.getKey() != null && e.getValue() != null)
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (a, b) -> a, LinkedHashMap::new));

        if (!nonNullCustomFields.isEmpty()) {
            bookMetadata.setCustomFields(nonNullCustomFields);
        }

        if (!customFieldLocks.isEmpty()) {
            bookMetadata.setCustomFieldLocks(customFieldLocks);
        }
    }

    @Named("withRelations")
    @Mapping(target = "description", ignore = true)
    public abstract BookMetadata toBookMetadata(BookMetadataEntity bookMetadataEntity, @Context boolean includeDescription);

    @Named("withoutRelations")
    @Mapping(target = "description", ignore = true)
    @Mapping(target = "authors", ignore = true)
    @Mapping(target = "categories", ignore = true)
    @Mapping(target = "moods", ignore = true)
    @Mapping(target = "tags", ignore = true)
    public abstract BookMetadata toBookMetadataWithoutRelations(BookMetadataEntity bookMetadataEntity, @Context boolean includeDescription);

}