package com.adityachandel.booklore.service;

import com.adityachandel.booklore.config.security.service.AuthenticationService;
import com.adityachandel.booklore.exception.ApiError;
import com.adityachandel.booklore.model.dto.BookLoreUser;
import com.adityachandel.booklore.model.dto.request.ReadingSessionRequest;
import com.adityachandel.booklore.model.dto.response.ReadingSessionHeatmapResponse;
import com.adityachandel.booklore.model.dto.response.ReadingSessionTimelineResponse;
import com.adityachandel.booklore.model.entity.BookEntity;
import com.adityachandel.booklore.model.entity.BookLoreUserEntity;
import com.adityachandel.booklore.model.entity.ReadingSessionEntity;
import com.adityachandel.booklore.repository.BookRepository;
import com.adityachandel.booklore.repository.ReadingSessionRepository;
import com.adityachandel.booklore.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReadingSessionService {

    private final AuthenticationService authenticationService;
    private final ReadingSessionRepository readingSessionRepository;
    private final BookRepository bookRepository;
    private final UserRepository userRepository;

    @Transactional
    public void recordSession(ReadingSessionRequest request) {
        BookLoreUser authenticatedUser = authenticationService.getAuthenticatedUser();
        Long userId = authenticatedUser.getId();

        BookLoreUserEntity userEntity = userRepository.findById(userId).orElseThrow(() -> new UsernameNotFoundException("User not found with ID: " + userId));
        BookEntity book = bookRepository.findById(request.getBookId()).orElseThrow(() -> ApiError.BOOK_NOT_FOUND.createException(request.getBookId()));

        ReadingSessionEntity session = ReadingSessionEntity.builder()
                .user(userEntity)
                .book(book)
                .bookType(request.getBookType())
                .startTime(request.getStartTime())
                .endTime(request.getEndTime())
                .durationSeconds(request.getDurationSeconds())
                .startProgress(request.getStartProgress())
                .endProgress(request.getEndProgress())
                .progressDelta(request.getProgressDelta())
                .startLocation(request.getStartLocation())
                .endLocation(request.getEndLocation())
                .build();

        readingSessionRepository.save(session);

        log.info("Reading session persisted successfully: sessionId={}, userId={}, bookId={}, duration={}s", session.getId(), userId, request.getBookId(), request.getDurationSeconds());
    }

    @Transactional(readOnly = true)
    public List<ReadingSessionHeatmapResponse> getSessionHeatmapForYear(int year) {
        BookLoreUser authenticatedUser = authenticationService.getAuthenticatedUser();
        Long userId = authenticatedUser.getId();

        return readingSessionRepository.findSessionCountsByUserAndYear(userId, year)
                .stream()
                .map(dto -> ReadingSessionHeatmapResponse.builder()
                        .date(dto.getDate())
                        .count(dto.getCount())
                        .build())
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<ReadingSessionTimelineResponse> getSessionTimelineForWeek(int year, int week) {
        BookLoreUser authenticatedUser = authenticationService.getAuthenticatedUser();
        Long userId = authenticatedUser.getId();

        return readingSessionRepository.findSessionTimelineByUserAndWeek(userId, year, week)
                .stream()
                .map(dto -> ReadingSessionTimelineResponse.builder()
                        .bookId(dto.getBookId())
                        .bookType(dto.getBookFileType())
                        .bookTitle(dto.getBookTitle())
                        .startDate(dto.getStartDate())
                        .endDate(dto.getEndDate())
                        .totalSessions(dto.getTotalSessions())
                        .totalDurationSeconds(dto.getTotalDurationSeconds())
                        .build())
                .collect(Collectors.toList());
    }
}
