package org.booklore.task.tasks;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.booklore.exception.ApiError;
import org.booklore.model.dto.BookLoreUser;
import org.booklore.model.dto.request.TaskCreateRequest;
import org.booklore.model.dto.response.TaskCreateResponse;
import org.booklore.model.enums.TaskType;
import org.booklore.model.enums.UserPermission;
import org.booklore.repository.SeriesCompletenessRepository;
import org.booklore.service.SeriesCompletenessService;
import org.booklore.task.TaskStatus;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Scheduled task to calculate series completeness for all libraries.
 * This task analyzes all book series and determines which are complete or incomplete.
 * The results are stored in the series_completeness table for fast OPDS filtering.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class CalculateSeriesCompletenessTask implements Task {

    private final SeriesCompletenessService seriesCompletenessService;
    private final SeriesCompletenessRepository seriesCompletenessRepository;

    @Override
    public void validatePermissions(BookLoreUser user, TaskCreateRequest request) {
        if (!UserPermission.CAN_ACCESS_TASK_MANAGER.isGranted(user.getPermissions())) {
            throw ApiError.PERMISSION_DENIED.createException(UserPermission.CAN_ACCESS_TASK_MANAGER);
        }
    }

    @Override
    public TaskCreateResponse execute(TaskCreateRequest request) {
        TaskCreateResponse.TaskCreateResponseBuilder builder = TaskCreateResponse.builder()
                .taskId(UUID.randomUUID().toString())
                .taskType(getTaskType());

        long startTime = System.currentTimeMillis();
        log.info("{}: Task started", getTaskType());

        try {
            // Calculate series completeness for all libraries
            seriesCompletenessService.calculateAllSeries();

            // Get final statistics
            long totalSeries = seriesCompletenessRepository.count();
            long incompleteSeries = seriesCompletenessRepository.findAllByIsIncompleteTrue().size();
            long completeSeries = totalSeries - incompleteSeries;

            log.info("{}: Successfully calculated {} series ({} complete, {} incomplete)", 
                    getTaskType(), totalSeries, completeSeries, incompleteSeries);

            builder.status(TaskStatus.COMPLETED);
        } catch (Exception e) {
            log.error("{}: Error calculating series completeness", getTaskType(), e);
            builder.status(TaskStatus.FAILED);
        }

        long endTime = System.currentTimeMillis();
        log.info("{}: Task completed. Duration: {} ms", getTaskType(), endTime - startTime);

        return builder.build();
    }

    @Override
    public TaskType getTaskType() {
        return TaskType.CALCULATE_SERIES_COMPLETENESS;
    }

    @Override
    public String getMetadata() {
        try {
            long totalSeries = seriesCompletenessRepository.count();
            long incompleteSeries = seriesCompletenessRepository.findAllByIsIncompleteTrue().size();
            
            if (totalSeries == 0) {
                return "No series data available. Run this task to calculate.";
            }
            
            return String.format("Series tracked: %d (Incomplete: %d, Complete: %d)", 
                    totalSeries, incompleteSeries, totalSeries - incompleteSeries);
        } catch (Exception e) {
            log.error("Error getting series completeness metadata", e);
            return "Series data unavailable";
        }
    }
}
