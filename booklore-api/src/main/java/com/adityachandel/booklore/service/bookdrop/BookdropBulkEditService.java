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

        if (enabledFields.contains("authors") && updates.getAuthors() != null) {
            if (mergeArrays && currentMetadata.getAuthors() != null) {
                Set<String> merged = new LinkedHashSet<>(currentMetadata.getAuthors());
                merged.addAll(updates.getAuthors());
                currentMetadata.setAuthors(merged);
            } else {
                currentMetadata.setAuthors(updates.getAuthors());
            }
        }
        if (enabledFields.contains("categories") && updates.getCategories() != null) {
            if (mergeArrays && currentMetadata.getCategories() != null) {
                Set<String> merged = new LinkedHashSet<>(currentMetadata.getCategories());
                merged.addAll(updates.getCategories());
                currentMetadata.setCategories(merged);
            } else {
                currentMetadata.setCategories(updates.getCategories());
            }
        }
        if (enabledFields.contains("moods") && updates.getMoods() != null) {
            if (mergeArrays && currentMetadata.getMoods() != null) {
                Set<String> merged = new LinkedHashSet<>(currentMetadata.getMoods());
                merged.addAll(updates.getMoods());
                currentMetadata.setMoods(merged);
            } else {
                currentMetadata.setMoods(updates.getMoods());
            }
        }
        if (enabledFields.contains("tags") && updates.getTags() != null) {
            if (mergeArrays && currentMetadata.getTags() != null) {
                Set<String> merged = new LinkedHashSet<>(currentMetadata.getTags());
                merged.addAll(updates.getTags());
                currentMetadata.setTags(merged);
            } else {
                currentMetadata.setTags(updates.getTags());
            }
        }

        metadataHelper.updateFetchedMetadata(file, currentMetadata);
    }
}