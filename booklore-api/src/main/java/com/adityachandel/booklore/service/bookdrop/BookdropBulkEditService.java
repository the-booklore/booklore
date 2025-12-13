package com.adityachandel.booklore.service.bookdrop;

import com.adityachandel.booklore.model.dto.BookMetadata;
import com.adityachandel.booklore.model.dto.request.BookdropBulkEditRequest;
import com.adityachandel.booklore.model.dto.response.BookdropBulkEditResult;
import com.adityachandel.booklore.model.entity.BookdropFileEntity;
import com.adityachandel.booklore.repository.BookdropFileRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class BookdropBulkEditService {

    private final BookdropFileRepository bookdropFileRepository;
    private final BookdropMetadataHelper metadataHelper;

    @Transactional
    public BookdropBulkEditResult bulkEdit(BookdropBulkEditRequest request) {
        List<Long> fileIds = metadataHelper.resolveFileIds(
                request.isSelectAll(),
                request.getExcludedIds(),
                request.getSelectedIds()
        );
        List<BookdropFileEntity> files = bookdropFileRepository.findAllById(fileIds);
        
        int successCount = 0;
        int failedCount = 0;

        for (BookdropFileEntity file : files) {
            try {
                updateFileMetadata(file, request);
                successCount++;
            } catch (Exception e) {
                log.error("Failed to update metadata for file {}: {}", file.getId(), e.getMessage());
                failedCount++;
            }
        }

        bookdropFileRepository.saveAll(files);

        return BookdropBulkEditResult.builder()
                .totalFiles(files.size())
                .successfullyUpdated(successCount)
                .failed(failedCount)
                .build();
    }

    private void updateFileMetadata(BookdropFileEntity file, BookdropBulkEditRequest request) {
        BookMetadata currentMetadata = metadataHelper.getCurrentMetadata(file);
        BookMetadata updates = request.getFields();
        Set<String> enabledFields = request.getEnabledFields();
        boolean mergeArrays = request.isMergeArrays();

        if (enabledFields.contains("seriesName") && updates.getSeriesName() != null) {
            currentMetadata.setSeriesName(updates.getSeriesName());
        }
        if (enabledFields.contains("seriesTotal") && updates.getSeriesTotal() != null) {
            currentMetadata.setSeriesTotal(updates.getSeriesTotal());
        }
        if (enabledFields.contains("publisher") && updates.getPublisher() != null) {
            currentMetadata.setPublisher(updates.getPublisher());
        }
        if (enabledFields.contains("language") && updates.getLanguage() != null) {
            currentMetadata.setLanguage(updates.getLanguage());
        }

        updateArrayField("authors", enabledFields, currentMetadata.getAuthors(), updates.getAuthors(), 
                currentMetadata::setAuthors, mergeArrays);
        updateArrayField("categories", enabledFields, currentMetadata.getCategories(), updates.getCategories(), 
                currentMetadata::setCategories, mergeArrays);
        updateArrayField("moods", enabledFields, currentMetadata.getMoods(), updates.getMoods(), 
                currentMetadata::setMoods, mergeArrays);
        updateArrayField("tags", enabledFields, currentMetadata.getTags(), updates.getTags(), 
                currentMetadata::setTags, mergeArrays);

        metadataHelper.updateFetchedMetadata(file, currentMetadata);
    }

    private void updateArrayField(String fieldName, Set<String> enabledFields, 
                                  Set<String> currentValue, Set<String> newValue,
                                  java.util.function.Consumer<Set<String>> setter, boolean mergeArrays) {
        if (enabledFields.contains(fieldName) && newValue != null) {
            if (mergeArrays && currentValue != null) {
                Set<String> merged = new LinkedHashSet<>(currentValue);
                merged.addAll(newValue);
                setter.accept(merged);
            } else {
                setter.accept(newValue);
            }
        }
    }
}