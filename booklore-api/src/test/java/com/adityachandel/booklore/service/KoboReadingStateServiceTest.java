package com.adityachandel.booklore.service;

import com.adityachandel.booklore.config.security.service.AuthenticationService;
import com.adityachandel.booklore.mapper.KoboReadingStateMapper;
import com.adityachandel.booklore.model.dto.BookLoreUser;
import com.adityachandel.booklore.model.dto.KoboSyncSettings;
import com.adityachandel.booklore.model.dto.kobo.KoboReadingState;
import com.adityachandel.booklore.model.dto.kobo.KoboReadingStateWrapper;
import com.adityachandel.booklore.model.dto.response.kobo.KoboReadingStateResponse;
import com.adityachandel.booklore.model.entity.BookEntity;
import com.adityachandel.booklore.model.entity.BookLoreUserEntity;
import com.adityachandel.booklore.model.entity.KoboReadingStateEntity;
import com.adityachandel.booklore.model.entity.UserBookProgressEntity;
import com.adityachandel.booklore.model.enums.ReadStatus;
import com.adityachandel.booklore.repository.BookRepository;
import com.adityachandel.booklore.repository.KoboReadingStateRepository;
import com.adityachandel.booklore.repository.UserBookProgressRepository;
import com.adityachandel.booklore.repository.UserRepository;
import com.adityachandel.booklore.service.kobo.KoboReadingStateBuilder;
import com.adityachandel.booklore.service.kobo.KoboReadingStateService;
import com.adityachandel.booklore.service.kobo.KoboSettingsService;
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
    }

    @Test
    @DisplayName("Should sync Kobo progress to UserBookProgress with valid data")
    void testSyncKoboProgressToUserBookProgress_Success() {
        String entitlementId = "100";
        KoboReadingState.CurrentBookmark.Location location = KoboReadingState.CurrentBookmark.Location.builder()
                .value("epubcfi(/6/4[chap01ref]!/4/2/1:3)")
                .type("EpubCfi")
                .source("Kobo")
                .build();

        KoboReadingState.CurrentBookmark bookmark = KoboReadingState.CurrentBookmark.builder()
                .progressPercent(25)
                .location(location)
                .build();

        KoboReadingState readingState = KoboReadingState.builder()
                .entitlementId(entitlementId)
                .currentBookmark(bookmark)
                .build();

        KoboReadingStateEntity entity = new KoboReadingStateEntity();
        when(mapper.toEntity(any())).thenReturn(entity);
        when(mapper.toDto(any(KoboReadingStateEntity.class))).thenReturn(readingState);
        when(repository.findByEntitlementId(entitlementId)).thenReturn(Optional.empty());
        when(repository.save(any())).thenReturn(entity);
        when(bookRepository.findById(100L)).thenReturn(Optional.of(testBook));
        when(userRepository.findById(1L)).thenReturn(Optional.of(testUserEntity));
        when(progressRepository.findByUserIdAndBookId(1L, 100L)).thenReturn(Optional.empty());

        ArgumentCaptor<UserBookProgressEntity> progressCaptor = ArgumentCaptor.forClass(UserBookProgressEntity.class);
        when(progressRepository.save(progressCaptor.capture())).thenReturn(new UserBookProgressEntity());

        KoboReadingStateResponse response = service.saveReadingState(List.of(readingState));

        assertNotNull(response);
        assertEquals("Success", response.getRequestResult());
        assertEquals(1, response.getUpdateResults().size());
        
        UserBookProgressEntity savedProgress = progressCaptor.getValue();
        assertNotNull(savedProgress);
        assertEquals(25.0f, savedProgress.getKoboProgressPercent());
        assertEquals("epubcfi(/6/4[chap01ref]!/4/2/1:3)", savedProgress.getKoboLocation());
        assertEquals("EpubCfi", savedProgress.getKoboLocationType());
        assertEquals("Kobo", savedProgress.getKoboLocationSource());
        assertNotNull(savedProgress.getKoboLastSyncTime());
        assertNotNull(savedProgress.getLastReadTime());
        assertEquals(ReadStatus.READING, savedProgress.getReadStatus());
    }

    @Test
    @DisplayName("Should update existing progress when syncing Kobo data")
    void testSyncKoboProgressToUserBookProgress_UpdateExisting() {
        String entitlementId = "100";
        UserBookProgressEntity existingProgress = new UserBookProgressEntity();
        existingProgress.setUser(testUserEntity);
        existingProgress.setBook(testBook);
        existingProgress.setKoboProgressPercent(10.0f);

        KoboReadingState.CurrentBookmark bookmark = KoboReadingState.CurrentBookmark.builder()
                .progressPercent(50)
                .build();

        KoboReadingState readingState = KoboReadingState.builder()
                .entitlementId(entitlementId)
                .currentBookmark(bookmark)
                .build();

        KoboReadingStateEntity entity = new KoboReadingStateEntity();
        when(mapper.toEntity(any())).thenReturn(entity);
        when(mapper.toDto(any(KoboReadingStateEntity.class))).thenReturn(readingState);
        when(repository.findByEntitlementId(entitlementId)).thenReturn(Optional.empty());
        when(repository.save(any())).thenReturn(entity);
        when(bookRepository.findById(100L)).thenReturn(Optional.of(testBook));
        when(userRepository.findById(1L)).thenReturn(Optional.of(testUserEntity));
        when(progressRepository.findByUserIdAndBookId(1L, 100L)).thenReturn(Optional.of(existingProgress));

        ArgumentCaptor<UserBookProgressEntity> progressCaptor = ArgumentCaptor.forClass(UserBookProgressEntity.class);
        when(progressRepository.save(progressCaptor.capture())).thenReturn(existingProgress);

        service.saveReadingState(List.of(readingState));

        UserBookProgressEntity savedProgress = progressCaptor.getValue();
        assertEquals(50.0f, savedProgress.getKoboProgressPercent());
        assertEquals(ReadStatus.READING, savedProgress.getReadStatus());
    }

    @Test
    @DisplayName("Should mark book as READ when progress reaches finished threshold")
    void testSyncKoboProgressToUserBookProgress_MarkAsRead() {
        String entitlementId = "100";
        testSettings.setProgressMarkAsFinishedThreshold(99f);

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
        when(repository.findByEntitlementId(entitlementId)).thenReturn(Optional.empty());
        when(repository.save(any())).thenReturn(entity);
        when(bookRepository.findById(100L)).thenReturn(Optional.of(testBook));
        when(userRepository.findById(1L)).thenReturn(Optional.of(testUserEntity));
        when(progressRepository.findByUserIdAndBookId(1L, 100L)).thenReturn(Optional.empty());

        ArgumentCaptor<UserBookProgressEntity> progressCaptor = ArgumentCaptor.forClass(UserBookProgressEntity.class);
        when(progressRepository.save(progressCaptor.capture())).thenReturn(new UserBookProgressEntity());

        service.saveReadingState(List.of(readingState));

        UserBookProgressEntity savedProgress = progressCaptor.getValue();
        assertEquals(100.0f, savedProgress.getKoboProgressPercent());
        assertEquals(ReadStatus.READ, savedProgress.getReadStatus());
        assertNotNull(savedProgress.getDateFinished());
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
        when(repository.findByEntitlementId(entitlementId)).thenReturn(Optional.empty());
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
    @DisplayName("Should mark book as READING when progress exceeds reading threshold")
    void testSyncKoboProgressToUserBookProgress_MarkAsReading() {
        String entitlementId = "100";
        testSettings.setProgressMarkAsReadingThreshold(1f);

        KoboReadingState.CurrentBookmark bookmark = KoboReadingState.CurrentBookmark.builder()
                .progressPercent(1)
                .build();

        KoboReadingState readingState = KoboReadingState.builder()
                .entitlementId(entitlementId)
                .currentBookmark(bookmark)
                .build();

        KoboReadingStateEntity entity = new KoboReadingStateEntity();
        when(mapper.toEntity(any())).thenReturn(entity);
        when(mapper.toDto(any(KoboReadingStateEntity.class))).thenReturn(readingState);
        when(repository.findByEntitlementId(entitlementId)).thenReturn(Optional.empty());
        when(repository.save(any())).thenReturn(entity);
        when(bookRepository.findById(100L)).thenReturn(Optional.of(testBook));
        when(userRepository.findById(1L)).thenReturn(Optional.of(testUserEntity));
        when(progressRepository.findByUserIdAndBookId(1L, 100L)).thenReturn(Optional.empty());

        ArgumentCaptor<UserBookProgressEntity> progressCaptor = ArgumentCaptor.forClass(UserBookProgressEntity.class);
        when(progressRepository.save(progressCaptor.capture())).thenReturn(new UserBookProgressEntity());

        service.saveReadingState(List.of(readingState));

        UserBookProgressEntity savedProgress = progressCaptor.getValue();
        assertEquals(1.0f, savedProgress.getKoboProgressPercent());
        assertEquals(ReadStatus.READING, savedProgress.getReadStatus());
        assertNull(savedProgress.getDateFinished());
    }

    @Test
    @DisplayName("Should use custom thresholds from settings")
    void testSyncKoboProgressToUserBookProgress_CustomThresholds() {
        String entitlementId = "100";
        testSettings.setProgressMarkAsReadingThreshold(5.0f);
        testSettings.setProgressMarkAsFinishedThreshold(95.0f);

        KoboReadingState.CurrentBookmark bookmark = KoboReadingState.CurrentBookmark.builder()
                .progressPercent(96)
                .build();

        KoboReadingState readingState = KoboReadingState.builder()
                .entitlementId(entitlementId)
                .currentBookmark(bookmark)
                .build();

        KoboReadingStateEntity entity = new KoboReadingStateEntity();
        when(mapper.toEntity(any())).thenReturn(entity);
        when(mapper.toDto(any(KoboReadingStateEntity.class))).thenReturn(readingState);
        when(repository.findByEntitlementId(entitlementId)).thenReturn(Optional.empty());
        when(repository.save(any())).thenReturn(entity);
        when(bookRepository.findById(100L)).thenReturn(Optional.of(testBook));
        when(userRepository.findById(1L)).thenReturn(Optional.of(testUserEntity));
        when(progressRepository.findByUserIdAndBookId(1L, 100L)).thenReturn(Optional.empty());

        ArgumentCaptor<UserBookProgressEntity> progressCaptor = ArgumentCaptor.forClass(UserBookProgressEntity.class);
        when(progressRepository.save(progressCaptor.capture())).thenReturn(new UserBookProgressEntity());

        service.saveReadingState(List.of(readingState));

        UserBookProgressEntity savedProgress = progressCaptor.getValue();
        assertEquals(ReadStatus.READ, savedProgress.getReadStatus());
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
        when(repository.findByEntitlementId(entitlementId)).thenReturn(Optional.empty());
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
        when(repository.findByEntitlementId(entitlementId)).thenReturn(Optional.empty());
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
        progress.setKoboLastSyncTime(Instant.now());

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

        when(repository.findByEntitlementId(entitlementId)).thenReturn(Optional.empty());
        when(authenticationService.getAuthenticatedUser()).thenReturn(testUser);
        when(progressRepository.findByUserIdAndBookId(1L, 100L)).thenReturn(Optional.of(progress));
        when(readingStateBuilder.buildReadingStateFromProgress(entitlementId, progress)).thenReturn(expectedState);

        KoboReadingStateWrapper result = service.getReadingState(entitlementId);

        assertNotNull(result);
        assertNotNull(result.getReadingStates());
        assertEquals(1, result.getReadingStates().size());
        
        KoboReadingState state = result.getReadingStates().get(0);
        assertEquals(entitlementId, state.getEntitlementId());
        assertNotNull(state.getCurrentBookmark());
        assertEquals(75, state.getCurrentBookmark().getProgressPercent());
        assertNotNull(state.getCurrentBookmark().getLocation());
        assertEquals("epubcfi(/6/4[chap01ref]!/4/2/1:3)", state.getCurrentBookmark().getLocation().getValue());
        assertEquals("EpubCfi", state.getCurrentBookmark().getLocation().getType());
        assertEquals("Kobo", state.getCurrentBookmark().getLocation().getSource());
        
        verify(repository).findByEntitlementId(entitlementId);
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

        when(repository.findByEntitlementId(entitlementId)).thenReturn(Optional.empty());
        when(authenticationService.getAuthenticatedUser()).thenReturn(testUser);
        when(progressRepository.findByUserIdAndBookId(1L, 100L)).thenReturn(Optional.of(progress));

        KoboReadingStateWrapper result = service.getReadingState(entitlementId);

        assertNull(result);
        verify(repository).findByEntitlementId(entitlementId);
        verify(progressRepository).findByUserIdAndBookId(1L, 100L);
    }

    @Test
    @DisplayName("Should return null when no Kobo state and no UserBookProgress exists")
    void testGetReadingState_NoDataExists() {
        String entitlementId = "100";
        when(repository.findByEntitlementId(entitlementId)).thenReturn(Optional.empty());
        when(authenticationService.getAuthenticatedUser()).thenReturn(testUser);
        when(progressRepository.findByUserIdAndBookId(1L, 100L)).thenReturn(Optional.empty());

        KoboReadingStateWrapper result = service.getReadingState(entitlementId);

        assertNull(result);
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
        when(repository.findByEntitlementId(entitlementId)).thenReturn(Optional.of(entity));
        when(mapper.toDto(entity)).thenReturn(existingState);

        KoboReadingStateWrapper result = service.getReadingState(entitlementId);

        assertNotNull(result);
        assertEquals(1, result.getReadingStates().size());
        assertEquals(entitlementId, result.getReadingStates().get(0).getEntitlementId());
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
        when(repository.findByEntitlementId(entitlementId)).thenReturn(Optional.empty());
        when(repository.save(any())).thenReturn(entity);
        when(bookRepository.findById(100L)).thenReturn(Optional.of(testBook));
        when(userRepository.findById(1L)).thenReturn(Optional.of(testUserEntity));
        when(progressRepository.findByUserIdAndBookId(1L, 100L)).thenReturn(Optional.empty());

        ArgumentCaptor<UserBookProgressEntity> progressCaptor = ArgumentCaptor.forClass(UserBookProgressEntity.class);
        when(progressRepository.save(progressCaptor.capture())).thenReturn(new UserBookProgressEntity());

        assertDoesNotThrow(() -> service.saveReadingState(List.of(readingState)));

        UserBookProgressEntity savedProgress = progressCaptor.getValue();
        assertNull(savedProgress.getKoboProgressPercent());
        assertNotNull(savedProgress.getKoboLastSyncTime());
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
        when(repository.findByEntitlementId(entitlementId)).thenReturn(Optional.empty());
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
}
