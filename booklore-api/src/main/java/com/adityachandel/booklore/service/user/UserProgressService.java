package com.adityachandel.booklore.service.user;

import com.adityachandel.booklore.model.entity.UserBookProgressEntity;
import com.adityachandel.booklore.repository.UserBookProgressRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@RequiredArgsConstructor
@Service
public class UserProgressService {

    private final UserBookProgressRepository userBookProgressRepository;

    public Map<Long, UserBookProgressEntity> fetchUserProgress(Long userId, Set<Long> bookIds) {
        return userBookProgressRepository.findByUserIdAndBookIdIn(userId, bookIds).stream()
                .collect(Collectors.toMap(p -> p.getBook().getId(), p -> p));
    }
}
