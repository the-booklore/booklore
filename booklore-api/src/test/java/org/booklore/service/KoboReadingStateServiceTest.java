package org.booklore.service;

import org.booklore.config.security.service.AuthenticationService;
import org.booklore.mapper.KoboReadingStateMapper;
import org.booklore.model.dto.BookLoreUser;
import org.booklore.model.dto.KoboSyncSettings;
import org.booklore.model.dto.kobo.KoboReadingState;
import org.booklore.model.entity.BookEntity;
import org.booklore.model.entity.BookLoreUserEntity;
import org.booklore.model.entity.KoboReadingStateEntity;
import org.booklore.model.entity.UserBookProgressEntity;
import org.booklore.model.enums.ReadStatus;
import org.booklore.model.enums.KoboReadStatus;
import org.booklore.repository.BookRepository;
import org.booklore.repository.KoboReadingStateRepository;
import org.booklore.repository.UserBookProgressRepository;
import org.booklore.repository.UserRepository;
import org.booklore.service.hardcover.HardcoverSyncService;
import org.booklore.service.kobo.KoboReadingStateBuilder;
import org.booklore.service.kobo.KoboReadingStateService;
import org.booklore.service.kobo.KoboSettingsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class KoboReadingStateServiceTest {

    @Mock
    private KoboReadingStateRepository repository;
    
    @Mock
    private KoboReadingStateMapper mapper;
    
    @Mock
    private UserBookProgressRepository progressRepository;
    
    @Mock
    private BookRepository bookRepository;
    
    @Mock
    private UserRepository userRepository;
    
    @Mock
    private AuthenticationService authenticationService;
    
    @Mock
    private KoboSettingsService koboSettingsService;

    @Mock
    private KoboReadingStateBuilder readingStateBuilder;

    @Mock
    private HardcoverSyncService hardcoverSyncService;

    @InjectMocks
    private KoboReadingStateService service;

    private BookLoreUser testUser;
    private BookEntity testBook;
    private BookLoreUserEntity testUserEntity;
    private KoboSyncSettings testSettings;

    @BeforeEach
    void setUp() {
        testUser = BookLoreUser.builder()
                .id(1L)
                .username("testuser")
                .isDefaultPassword(true).build();

        testUserEntity = new BookLoreUserEntity();
        testUserEntity.setId(1L);
        testUserEntity.setUsername("testuser");

        testBook = new BookEntity();
        testBook.setId(100L);

        testSettings = new KoboSyncSettings();
        testSettings.setProgressMarkAsReadingThreshold(1f);
        testSettings.setProgressMarkAsFinishedThreshold(99f);

        when(authenticationService.getAuthenticatedUser()).thenReturn(testUser);
        when(koboSettingsService.getCurrentUserSettings()).thenReturn(testSettings);
        lenient().when(repository
                .findFirstByEntitlementIdAndUserIdIsNullOrderByPriorityTimestampDescLastModifiedStringDescIdDesc(
                        anyString()))
                .thenReturn(Optional.empty());
        lenient().when(mapper.toJson(any())).thenCallRealMethod();
        lenient().when(mapper.cleanString(any())).thenCallRealMethod();
    }

    @Test
    @DisplayName("Should not overwrite existing finished date when syncing completed book")
    void testSyncKoboProgressToUserBookProgress_PreserveExistingFinishedDate() {
        String entitlementId = "100";
        testSettings.setProgressMarkAsFinishedThreshold(99f);

        Instant originalFinishedDate = Instant.parse("2025-01-15T10:30:00Z");
        UserBookProgressEntity existingProgress = new UserBookProgressEntity();
        existingProgress.setUser(testUserEntity);
        existingProgress.setBook(testBook);
        existingProgress.setKoboProgressPercent(99.5f);
        existingProgress.setReadStatus(ReadStatus.READ);
        existingProgress.setDateFinished(originalFinishedDate);

        KoboReadingState.CurrentBookmark bookmark = KoboReadingState.CurrentBookmark.builder()
                .progressPercent(100)
                .build();

        KoboReadingState readingState = KoboReadingState.builder()
                .entitlementId(entitlementId)
                .currentBookmark(bookmark)
                .build();

        KoboReadingStateEntity entity = new KoboReadingStateEntity();
        when(mapper.toEntity(any())).thenReturn(entity);
        when(mapper.toDto(any(KoboReadingStateEntity.class))).thenReturn(readingState);
        when(repository.findByEntitlementIdAndUserId(entitlementId, 1L)).thenReturn(Optional.empty());
        when(repository.save(any())).thenReturn(entity);
        when(bookRepository.findById(100L)).thenReturn(Optional.of(testBook));
        when(userRepository.findById(1L)).thenReturn(Optional.of(testUserEntity));
        when(progressRepository.findByUserIdAndBookId(1L, 100L)).thenReturn(Optional.of(existingProgress));

        ArgumentCaptor<UserBookProgressEntity> progressCaptor = ArgumentCaptor.forClass(UserBookProgressEntity.class);
        when(progressRepository.save(progressCaptor.capture())).thenReturn(existingProgress);

        service.saveReadingState(List.of(readingState));

        UserBookProgressEntity savedProgress = progressCaptor.getValue();
        assertEquals(100.0f, savedProgress.getKoboProgressPercent());
        assertEquals(ReadStatus.READ, savedProgress.getReadStatus());
        assertEquals(originalFinishedDate, savedProgress.getDateFinished(), 
            "Existing finished date should not be overwritten during sync");
    }

    @Test
    @DisplayName("Should not update Hardcover.app when progress hasn't changed")
    void testSyncKoboProgressToUserBookProgress_IgnoreHardcoverUpdateWhenNoChange() {
        String entitlementId = "100";
        testSettings.setProgressMarkAsFinishedThreshold(99f);

        Instant originalFinishedDate = Instant.parse("2025-01-15T10:30:00Z");
        UserBookProgressEntity existingProgress = new UserBookProgressEntity();
        existingProgress.setUser(testUserEntity);
        existingProgress.setBook(testBook);
        existingProgress.setKoboProgressPercent(12.0f);
        existingProgress.setReadStatus(ReadStatus.READING);
        existingProgress.setDateFinished(originalFinishedDate);

        KoboReadingState.CurrentBookmark bookmark = KoboReadingState.CurrentBookmark.builder()
                .progressPercent(12)
                .build();

        KoboReadingState readingState = KoboReadingState.builder()
                .entitlementId(entitlementId)
                .currentBookmark(bookmark)
                .build();

        KoboReadingStateEntity entity = new KoboReadingStateEntity();
        when(mapper.toEntity(any())).thenReturn(entity);
        when(mapper.toDto(any(KoboReadingStateEntity.class))).thenReturn(readingState);
        when(repository.findByEntitlementIdAndUserId(entitlementId, 1L)).thenReturn(Optional.empty());
        when(repository.save(any())).thenReturn(entity);
        when(bookRepository.findById(100L)).thenReturn(Optional.of(testBook));
        when(userRepository.findById(1L)).thenReturn(Optional.of(testUserEntity));
        when(progressRepository.findByUserIdAndBookId(1L, 100L)).thenReturn(Optional.of(existingProgress));

        ArgumentCaptor<UserBookProgressEntity> progressCaptor = ArgumentCaptor.forClass(UserBookProgressEntity.class);
        when(progressRepository.save(progressCaptor.capture())).thenReturn(existingProgress);

        service.saveReadingState(List.of(readingState));

        UserBookProgressEntity savedProgress = progressCaptor.getValue();
        assertEquals(12.0f, savedProgress.getKoboProgressPercent());
        assertEquals(ReadStatus.READING, savedProgress.getReadStatus());
        assertEquals(originalFinishedDate, savedProgress.getDateFinished(), 
            "Existing finished date should not be overwritten during sync");
        verify(hardcoverSyncService, never()).syncProgressToHardcover(any(), any(), any());
    }

    @Test
    @DisplayName("Should handle invalid entitlement ID gracefully")
    void testSyncKoboProgressToUserBookProgress_InvalidEntitlementId() {
        String entitlementId = "not-a-number";
        KoboReadingState readingState = KoboReadingState.builder()
                .entitlementId(entitlementId)
                .currentBookmark(KoboReadingState.CurrentBookmark.builder().build())
                .build();

        KoboReadingStateEntity entity = new KoboReadingStateEntity();
        when(mapper.toEntity(any())).thenReturn(entity);
        when(mapper.toDto(any(KoboReadingStateEntity.class))).thenReturn(readingState);
        when(repository.findByEntitlementIdAndUserId(entitlementId, 1L)).thenReturn(Optional.empty());
        when(repository.save(any())).thenReturn(entity);

        assertDoesNotThrow(() -> service.saveReadingState(List.of(readingState)));
        verify(progressRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should handle missing book gracefully")
    void testSyncKoboProgressToUserBookProgress_BookNotFound() {
        String entitlementId = "999";
        KoboReadingState readingState = KoboReadingState.builder()
                .entitlementId(entitlementId)
                .currentBookmark(KoboReadingState.CurrentBookmark.builder()
                        .progressPercent(50)
                        .build())
                .build();

        KoboReadingStateEntity entity = new KoboReadingStateEntity();
        when(mapper.toEntity(any())).thenReturn(entity);
        when(mapper.toDto(any(KoboReadingStateEntity.class))).thenReturn(readingState);
        when(repository.findByEntitlementIdAndUserId(entitlementId, 1L)).thenReturn(Optional.empty());
        when(repository.save(any())).thenReturn(entity);
        when(bookRepository.findById(999L)).thenReturn(Optional.empty());

        assertDoesNotThrow(() -> service.saveReadingState(List.of(readingState)));
        verify(progressRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should construct reading state from UserBookProgress when no Kobo state exists")
    void testGetReadingState_ConstructFromProgress() {
        String entitlementId = "100";
        UserBookProgressEntity progress = new UserBookProgressEntity();
        progress.setKoboProgressPercent(75.5f);
        progress.setKoboLocation("epubcfi(/6/4[chap01ref]!/4/2/1:3)");
        progress.setKoboLocationType("EpubCfi");
        progress.setKoboLocationSource("Kobo");
        progress.setKoboProgressReceivedTime(Instant.now());

        KoboReadingState expectedState = KoboReadingState.builder()
                .entitlementId(entitlementId)
                .currentBookmark(KoboReadingState.CurrentBookmark.builder()
                        .progressPercent(75)
                        .location(KoboReadingState.CurrentBookmark.Location.builder()
                                .value("epubcfi(/6/4[chap01ref]!/4/2/1:3)")
                                .type("EpubCfi")
                                .source("Kobo")
                                .build())
                        .build())
                .build();

        when(repository.findByEntitlementIdAndUserId(entitlementId, 1L)).thenReturn(Optional.empty());
        when(authenticationService.getAuthenticatedUser()).thenReturn(testUser);
        when(progressRepository.findByUserIdAndBookId(1L, 100L)).thenReturn(Optional.of(progress));
        when(readingStateBuilder.buildReadingStateFromProgress(entitlementId, progress)).thenReturn(expectedState);

        List<KoboReadingState> result = service.getReadingState(entitlementId);

        assertNotNull(result);
        assertEquals(1, result.size());
        
        KoboReadingState state = result.getFirst();
        assertEquals(entitlementId, state.getEntitlementId());
        assertNotNull(state.getCurrentBookmark());
        assertEquals(75, state.getCurrentBookmark().getProgressPercent());
        assertNotNull(state.getCurrentBookmark().getLocation());
        assertEquals("epubcfi(/6/4[chap01ref]!/4/2/1:3)", state.getCurrentBookmark().getLocation().getValue());
        assertEquals("EpubCfi", state.getCurrentBookmark().getLocation().getType());
        assertEquals("Kobo", state.getCurrentBookmark().getLocation().getSource());
        
        verify(repository).findByEntitlementIdAndUserId(entitlementId, 1L);
        verify(progressRepository).findByUserIdAndBookId(1L, 100L);
        verify(readingStateBuilder).buildReadingStateFromProgress(entitlementId, progress);
    }

    @Test
    @DisplayName("Should return null when no Kobo reading state exists and UserBookProgress has no Kobo data")
    void testGetReadingState_NoKoboDataInProgress() {
        String entitlementId = "100";
        UserBookProgressEntity progress = new UserBookProgressEntity();
        progress.setKoboProgressPercent(null);
        progress.setKoboLocation(null);

        when(repository.findByEntitlementIdAndUserId(entitlementId, 1L)).thenReturn(Optional.empty());
        when(authenticationService.getAuthenticatedUser()).thenReturn(testUser);
        when(progressRepository.findByUserIdAndBookId(1L, 100L)).thenReturn(Optional.of(progress));

        List<KoboReadingState> result = service.getReadingState(entitlementId);

        assertNotNull(result);
        assertTrue(result.isEmpty());
        verify(repository).findByEntitlementIdAndUserId(entitlementId, 1L);
        verify(progressRepository).findByUserIdAndBookId(1L, 100L);
    }

    @Test
    @DisplayName("Should return null when no Kobo state and no UserBookProgress exists")
    void testGetReadingState_NoDataExists() {
        String entitlementId = "100";
        when(repository.findByEntitlementIdAndUserId(entitlementId, 1L)).thenReturn(Optional.empty());
        when(authenticationService.getAuthenticatedUser()).thenReturn(testUser);
        when(progressRepository.findByUserIdAndBookId(1L, 100L)).thenReturn(Optional.empty());

        List<KoboReadingState> result = service.getReadingState(entitlementId);

        assertNotNull(result);
        assertTrue(result.isEmpty());
        verify(progressRepository).findByUserIdAndBookId(1L, 100L);
    }

    @Test
    @DisplayName("Should return existing Kobo reading state when it exists")
    void testGetReadingState_ExistingState() {
        String entitlementId = "100";
        KoboReadingState existingState = KoboReadingState.builder()
                .entitlementId(entitlementId)
                .build();
        
        KoboReadingStateEntity entity = new KoboReadingStateEntity();
        when(repository.findByEntitlementIdAndUserId(entitlementId, 1L)).thenReturn(Optional.of(entity));
        when(mapper.toDto(entity)).thenReturn(existingState);

        List<KoboReadingState> result = service.getReadingState(entitlementId);

        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals(entitlementId, result.getFirst().getEntitlementId());
        verify(progressRepository, never()).findByUserIdAndBookId(anyLong(), anyLong());
    }

    @Test
    @DisplayName("Should handle null bookmark gracefully")
    void testSyncKoboProgressToUserBookProgress_NullBookmark() {
        String entitlementId = "100";
        KoboReadingState readingState = KoboReadingState.builder()
                .entitlementId(entitlementId)
                .currentBookmark(null)
                .build();

        KoboReadingStateEntity entity = new KoboReadingStateEntity();
        when(mapper.toEntity(any())).thenReturn(entity);
        when(mapper.toDto(any(KoboReadingStateEntity.class))).thenReturn(readingState);
        when(repository.findByEntitlementIdAndUserId(entitlementId, 1L)).thenReturn(Optional.empty());
        when(repository.save(any())).thenReturn(entity);
        when(bookRepository.findById(100L)).thenReturn(Optional.of(testBook));
        when(userRepository.findById(1L)).thenReturn(Optional.of(testUserEntity));
        when(progressRepository.findByUserIdAndBookId(1L, 100L)).thenReturn(Optional.empty());

        ArgumentCaptor<UserBookProgressEntity> progressCaptor = ArgumentCaptor.forClass(UserBookProgressEntity.class);
        when(progressRepository.save(progressCaptor.capture())).thenReturn(new UserBookProgressEntity());

        assertDoesNotThrow(() -> service.saveReadingState(List.of(readingState)));

        UserBookProgressEntity savedProgress = progressCaptor.getValue();
        assertNull(savedProgress.getKoboProgressPercent());
        assertNotNull(savedProgress.getKoboProgressReceivedTime());
    }

    @Test
    @DisplayName("Should handle null progress percent in bookmark")
    void testSyncKoboProgressToUserBookProgress_NullProgressPercent() {
        String entitlementId = "100";
        KoboReadingState.CurrentBookmark bookmark = KoboReadingState.CurrentBookmark.builder()
                .progressPercent(null)
                .build();

        KoboReadingState readingState = KoboReadingState.builder()
                .entitlementId(entitlementId)
                .currentBookmark(bookmark)
                .build();

        KoboReadingStateEntity entity = new KoboReadingStateEntity();
        when(mapper.toEntity(any())).thenReturn(entity);
        when(mapper.toDto(any(KoboReadingStateEntity.class))).thenReturn(readingState);
        when(repository.findByEntitlementIdAndUserId(entitlementId, 1L)).thenReturn(Optional.empty());
        when(repository.save(any())).thenReturn(entity);
        when(bookRepository.findById(100L)).thenReturn(Optional.of(testBook));
        when(userRepository.findById(1L)).thenReturn(Optional.of(testUserEntity));
        when(progressRepository.findByUserIdAndBookId(1L, 100L)).thenReturn(Optional.empty());

        ArgumentCaptor<UserBookProgressEntity> progressCaptor = ArgumentCaptor.forClass(UserBookProgressEntity.class);
        when(progressRepository.save(progressCaptor.capture())).thenReturn(new UserBookProgressEntity());

        assertDoesNotThrow(() -> service.saveReadingState(List.of(readingState)));

        UserBookProgressEntity savedProgress = progressCaptor.getValue();
        assertNull(savedProgress.getKoboProgressPercent());
    }

    @Test
    @DisplayName("Should merge per-field updates based on lastModified")
    void testSaveReadingState_PerFieldMerge() throws Exception {
        String entitlementId = "100";
        String existingTimestamp = "2025-01-01T00:00:00.0000000Z";
        String newerTimestamp = "2025-01-04T00:00:00.0000000Z";
        String midTimestamp = "2025-01-03T00:00:00.0000000Z";
        String olderTimestamp = "2025-01-02T00:00:00.0000000Z";

        KoboReadingState.StatusInfo existingStatus = KoboReadingState.StatusInfo.builder()
                .lastModified(existingTimestamp)
                .status(KoboReadStatus.READING)
                .timesStartedReading(1)
                .build();
        KoboReadingState.Statistics existingStats = KoboReadingState.Statistics.builder()
                .lastModified(existingTimestamp)
                .spentReadingMinutes(5)
                .remainingTimeMinutes(20)
                .build();
        KoboReadingState.CurrentBookmark existingBookmark = KoboReadingState.CurrentBookmark.builder()
                .lastModified(midTimestamp)
                .progressPercent(25)
                .build();

        KoboReadingState existingState = KoboReadingState.builder()
                .entitlementId(entitlementId)
                .created(existingTimestamp)
                .lastModified(midTimestamp)
                .statusInfo(existingStatus)
                .statistics(existingStats)
                .currentBookmark(existingBookmark)
                .priorityTimestamp(midTimestamp)
                .build();

        KoboReadingState.StatusInfo incomingStatus = KoboReadingState.StatusInfo.builder()
                .lastModified(newerTimestamp)
                .status(KoboReadStatus.FINISHED)
                .timesStartedReading(2)
                .build();
        KoboReadingState.CurrentBookmark incomingBookmark = KoboReadingState.CurrentBookmark.builder()
                .lastModified(olderTimestamp)
                .progressPercent(10)
                .build();

        KoboReadingState incomingState = KoboReadingState.builder()
                .entitlementId(entitlementId)
                .lastModified(newerTimestamp)
                .statusInfo(incomingStatus)
                .currentBookmark(incomingBookmark)
                .build();

        KoboReadingStateEntity existingEntity = new KoboReadingStateEntity();
        existingEntity.setEntitlementId(entitlementId);
        existingEntity.setUserId(1L);

        when(mapper.toDto(existingEntity)).thenReturn(existingState);
        when(repository.findByEntitlementIdAndUserId(entitlementId, 1L)).thenReturn(Optional.of(existingEntity));
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        ArgumentCaptor<KoboReadingStateEntity> entityCaptor = ArgumentCaptor.forClass(KoboReadingStateEntity.class);
        service.saveReadingState(List.of(incomingState));
        verify(repository).save(entityCaptor.capture());

        KoboReadingStateEntity saved = entityCaptor.getValue();
        ObjectMapper objectMapper = new ObjectMapper();
        KoboReadingState.StatusInfo savedStatus = objectMapper.readValue(saved.getStatusInfoJson(), KoboReadingState.StatusInfo.class);
        KoboReadingState.CurrentBookmark savedBookmark = objectMapper.readValue(saved.getCurrentBookmarkJson(), KoboReadingState.CurrentBookmark.class);
        KoboReadingState.Statistics savedStatistics = objectMapper.readValue(saved.getStatisticsJson(), KoboReadingState.Statistics.class);

        assertEquals(incomingStatus.getStatus(), savedStatus.getStatus());
        assertEquals(existingBookmark.getProgressPercent(), savedBookmark.getProgressPercent());
        assertEquals(existingStats.getSpentReadingMinutes(), savedStatistics.getSpentReadingMinutes());
        assertEquals(newerTimestamp, saved.getLastModifiedString());
        assertEquals(newerTimestamp, saved.getPriorityTimestamp());
    }

    @Test
    @DisplayName("Should not update fields when timestamps are equal")
    void testSaveReadingState_EqualTimestampNoUpdate() throws Exception {
        String entitlementId = "100";
        String timestamp = "2025-01-01T00:00:00.0000000Z";

        KoboReadingState.StatusInfo existingStatus = KoboReadingState.StatusInfo.builder()
                .lastModified(timestamp)
                .status(KoboReadStatus.READING)
                .timesStartedReading(1)
                .build();

        KoboReadingState existingState = KoboReadingState.builder()
                .entitlementId(entitlementId)
                .created(timestamp)
                .lastModified(timestamp)
                .statusInfo(existingStatus)
                .priorityTimestamp(timestamp)
                .build();

        KoboReadingState.StatusInfo incomingStatus = KoboReadingState.StatusInfo.builder()
                .lastModified(timestamp)
                .status(KoboReadStatus.FINISHED)
                .timesStartedReading(2)
                .build();

        KoboReadingState incomingState = KoboReadingState.builder()
                .entitlementId(entitlementId)
                .lastModified(timestamp)
                .statusInfo(incomingStatus)
                .build();

        KoboReadingStateEntity existingEntity = new KoboReadingStateEntity();
        existingEntity.setEntitlementId(entitlementId);
        existingEntity.setUserId(1L);

        when(mapper.toDto(existingEntity)).thenReturn(existingState);
        when(repository.findByEntitlementIdAndUserId(entitlementId, 1L)).thenReturn(Optional.of(existingEntity));
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        ArgumentCaptor<KoboReadingStateEntity> entityCaptor = ArgumentCaptor.forClass(KoboReadingStateEntity.class);
        service.saveReadingState(List.of(incomingState));
        verify(repository).save(entityCaptor.capture());

        KoboReadingStateEntity saved = entityCaptor.getValue();
        ObjectMapper objectMapper = new ObjectMapper();
        KoboReadingState.StatusInfo savedStatus = objectMapper.readValue(saved.getStatusInfoJson(), KoboReadingState.StatusInfo.class);

        assertEquals(existingStatus.getStatus(), savedStatus.getStatus());
        assertEquals(timestamp, saved.getLastModifiedString());
    }
}
