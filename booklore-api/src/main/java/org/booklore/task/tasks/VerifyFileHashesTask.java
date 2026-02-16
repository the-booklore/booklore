package org.booklore.task.tasks;

import org.booklore.model.dto.BookLoreUser;
import org.booklore.model.dto.request.FileHashVerificationRequest;
import org.booklore.model.dto.request.TaskCreateRequest;
import org.booklore.model.dto.response.TaskCreateResponse;
import org.booklore.model.enums.TaskType;
import org.booklore.model.websocket.TaskProgressPayload;
import org.booklore.model.websocket.Topic;
import org.booklore.service.NotificationService;
import org.booklore.service.file.FileHashVerificationService;
import org.booklore.task.TaskStatus;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import static org.booklore.model.enums.UserPermission.IS_ADMIN;
import static org.booklore.exception.ApiError.PERMISSION_DENIED;

@AllArgsConstructor
@Component
@Slf4j
public class VerifyFileHashesTask implements Task {

    private final FileHashVerificationService fileHashVerificationService;
    private final NotificationService notificationService;

    @Override
    public void validatePermissions(BookLoreUser user, TaskCreateRequest request) {
        // Only admins can run file hash verification
        if (!IS_ADMIN.isGranted(user.getPermissions())) {
            throw PERMISSION_DENIED.createException(IS_ADMIN);
        }
    }

    @Override
    public TaskCreateResponse execute(TaskCreateRequest request) {
        FileHashVerificationRequest verificationRequest = request.getOptionsAs(FileHashVerificationRequest.class);
        String taskId = request.getTaskId();

        if (verificationRequest == null) {
            log.error("{}: Task failed. TaskId: {}. FileHashVerificationRequest is null. " +
                    "The task must be called with proper verification options (verificationType and either libraryId or bookIds).",
                    getTaskType(), taskId);
            throw new IllegalArgumentException(
                    "FileHashVerificationRequest cannot be null. Please provide verification options " +
                    "(verificationType=LIBRARY with libraryId, or verificationType=BOOKS with bookIds).");
        }

        long startTime = System.currentTimeMillis();
        log.info("{}: Task started. TaskId: {}, Options: {}", getTaskType(), taskId, verificationRequest);

        FileHashVerificationService.VerificationSummary summary = 
                fileHashVerificationService.verifyFileHashes(verificationRequest, taskId);

        long endTime = System.currentTimeMillis();
        long duration = endTime - startTime;
        log.info("{}: Task completed. Duration: {} ms", getTaskType(), duration);

        // Send completion notification with results
        String resultMessage = buildResultMessage(summary, duration);
        sendCompletionNotification(taskId, resultMessage);

        return TaskCreateResponse
                .builder()
                .taskType(TaskType.VERIFY_FILE_HASHES)
                .taskId(taskId)
                .status(TaskStatus.COMPLETED)
                .build();
    }

    private String buildResultMessage(FileHashVerificationService.VerificationSummary summary, long durationMs) {
        StringBuilder message = new StringBuilder();
        
        if (summary.isDryRun()) {
            message.append("[DRY RUN] ");
        }
        
        message.append(String.format("Scanned %d %s in %.1f seconds. ",
                summary.getTotalBooks(),
                summary.getTotalBooks() == 1 ? "book" : "books",
                durationMs / 1000.0));
        
        if (summary.getMismatchCount() > 0) {
            message.append(String.format("Found %d hash %s. ",
                    summary.getMismatchCount(),
                    summary.getMismatchCount() == 1 ? "mismatch" : "mismatches"));
            
            if (summary.isDryRun()) {
                message.append("No changes made (dry run mode). ");
            } else {
                message.append("Hashes updated. ");
            }
        } else {
            message.append("All hashes verified successfully. ");
        }
        
        if (summary.getErrorCount() > 0) {
            message.append(String.format("%d %s encountered.",
                    summary.getErrorCount(),
                    summary.getErrorCount() == 1 ? "error" : "errors"));
        }
        
        return message.toString().trim();
    }
    
    private void sendCompletionNotification(String taskId, String message) {
        try {
            TaskProgressPayload payload = TaskProgressPayload.builder()
                    .taskId(taskId)
                    .taskType(TaskType.VERIFY_FILE_HASHES)
                    .message(message)
                    .progress(100)
                    .taskStatus(TaskStatus.COMPLETED)
                    .build();
            
            notificationService.sendMessage(Topic.TASK_PROGRESS, payload);
        } catch (Exception e) {
            log.error("Failed to send completion notification for taskId={}: {}", taskId, e.getMessage(), e);
        }
    }

    @Override
    public TaskType getTaskType() {
        return TaskType.VERIFY_FILE_HASHES;
    }
}
