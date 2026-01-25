package com.adityachandel.booklore.service.user;

import com.adityachandel.booklore.model.entity.UserBookFileProgressEntity;
import com.adityachandel.booklore.model.entity.UserBookProgressEntity;
import com.adityachandel.booklore.repository.UserBookFileProgressRepository;
import com.adityachandel.booklore.repository.UserBookProgressRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@RequiredArgsConstructor
@Service
public class UserProgressService {

    private final UserBookProgressRepository userBookProgressRepository;
    private final UserBookFileProgressRepository userBookFileProgressRepository;

    public Map<Long, UserBookProgressEntity> fetchUserProgress(Long userId, Set<Long> bookIds) {
        return userBookProgressRepository.findByUserIdAndBookIdIn(userId, bookIds).stream()
                .collect(Collectors.toMap(p -> p.getBook().getId(), p -> p));
    }

    /**
     * Fetches file-level progress for the given user and book IDs.
     * Returns a map of book ID -> UserBookFileProgressEntity (most recent file progress for each book)
     */
    public Map<Long, UserBookFileProgressEntity> fetchUserFileProgress(Long userId, Set<Long> bookIds) {
        if (bookIds.isEmpty()) {
            return Collections.emptyMap();
        }

        List<UserBookFileProgressEntity> fileProgressList =
                userBookFileProgressRepository.findByUserIdAndBookFileBookIdIn(userId, bookIds);

        // Group by book ID and pick the most recent progress for each book
        return fileProgressList.stream()
                .collect(Collectors.toMap(
                        p -> p.getBookFile().getBook().getId(),
                        p -> p,
                        (existing, replacement) -> {
                            // Keep the most recently read one
                            if (existing.getLastReadTime() == null) return replacement;
                            if (replacement.getLastReadTime() == null) return existing;
                            return replacement.getLastReadTime().isAfter(existing.getLastReadTime())
                                    ? replacement : existing;
                        }
                ));
    }

    /**
     * Fetches file-level progress for a specific book file.
     */
    public Optional<UserBookFileProgressEntity> fetchUserFileProgressForFile(Long userId, Long bookFileId) {
        return userBookFileProgressRepository.findByUserIdAndBookFileId(userId, bookFileId);
    }
}
