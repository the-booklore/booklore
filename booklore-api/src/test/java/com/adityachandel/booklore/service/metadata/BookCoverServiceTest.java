package com.adityachandel.booklore.service.metadata;

import com.adityachandel.booklore.exception.ApiError;
import com.adityachandel.booklore.mapper.BookMetadataMapper;
import com.adityachandel.booklore.model.dto.BookMetadata;
import com.adityachandel.booklore.model.dto.settings.MetadataPersistenceSettings;
import com.adityachandel.booklore.model.entity.AuthorEntity;
import com.adityachandel.booklore.model.entity.BookEntity;
import com.adityachandel.booklore.model.entity.BookMetadataEntity;
import com.adityachandel.booklore.model.enums.BookFileType;
import com.adityachandel.booklore.model.websocket.Topic;
import com.adityachandel.booklore.repository.BookRepository;
import com.adityachandel.booklore.service.NotificationService;
import com.adityachandel.booklore.service.appsettings.AppSettingService;
import com.adityachandel.booklore.service.book.BookQueryService;
import com.adityachandel.booklore.service.file.FileFingerprint;
import com.adityachandel.booklore.service.fileprocessor.BookFileProcessor;
import com.adityachandel.booklore.service.fileprocessor.BookFileProcessorRegistry;
import com.adityachandel.booklore.service.metadata.writer.MetadataWriter;
import com.adityachandel.booklore.service.metadata.writer.MetadataWriterFactory;
import com.adityachandel.booklore.util.FileService;
import com.adityachandel.booklore.util.SecurityContextVirtualThread;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.*;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.time.Instant;
import java.util.*;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class BookCoverServiceTest {

    @Mock private BookRepository bookRepository;
    @Mock private BookMetadataMapper bookMetadataMapper;
    @Mock private NotificationService notificationService;
    @Mock private AppSettingService appSettingService;
    @Mock private FileService fileService;
    @Mock private BookFileProcessorRegistry processorRegistry;
    @Mock private BookQueryService bookQueryService;
    @Mock private CoverImageGenerator coverImageGenerator;
    @Mock private MetadataWriterFactory metadataWriterFactory;
    @Mock private TransactionTemplate transactionTemplate;

    @InjectMocks private BookCoverService bookCoverService;

    @Captor private ArgumentCaptor<Object> notificationCaptor;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    private BookEntity mockBookEntity(Long id, boolean coverLocked) {
        BookMetadataEntity metadata = new BookMetadataEntity();
        metadata.setTitle("Test Title");
        metadata.setAuthors(Set.of(new AuthorEntity(1L, "Author Name", null)));
        metadata.setCoverLocked(coverLocked);
        BookEntity book = new BookEntity();
        book.setId(id);
        book.setMetadata(metadata);
        book.setBookType(BookFileType.EPUB);
        return book;
    }

    @Test
    void generateCustomCover_success() {
        BookEntity book = mockBookEntity(1L, false);
        when(bookRepository.findById(1L)).thenReturn(Optional.of(book));
        when(coverImageGenerator.generateCover(any(), any())).thenReturn(new byte[]{1,2,3});
        doNothing().when(fileService).createThumbnailFromBytes(eq(1L), any());
        when(bookRepository.save(any())).thenReturn(book);

        bookCoverService.generateCustomCover(1L);

        verify(fileService).createThumbnailFromBytes(eq(1L), any());
        verify(bookRepository).save(book);
        verify(notificationService).sendMessage(eq(Topic.BOOKS_COVER_UPDATE), any());
    }

    @Test
    void generateCustomCover_coverLocked_throws() {
        BookEntity book = mockBookEntity(2L, true);
        when(bookRepository.findById(2L)).thenReturn(Optional.of(book));
        assertThatThrownBy(() -> bookCoverService.generateCustomCover(2L))
                .isInstanceOf(ApiError.METADATA_LOCKED.createException().getClass());
    }

    @Test
    void updateCoverImageFromFile_success() {
        MultipartFile file = mock(MultipartFile.class);
        BookEntity book = mockBookEntity(3L, false);
        when(bookRepository.findAllWithMetadataByIds(any())).thenReturn(List.of(book));
        when(bookRepository.save(any())).thenReturn(book);
        when(appSettingService.getAppSettings()).thenReturn(mockAppSettings(true, false));
        when(bookMetadataMapper.toBookMetadata(any(), eq(true))).thenReturn(new BookMetadata());

        BookMetadata result = bookCoverService.updateCoverImageFromFile(3L, file);

        verify(fileService).createThumbnailFromFile(3L, file);
        assertThat(result).isNotNull();
    }

    @Test
    void updateCoverImageFromUrl_success() {
        String url = "http://test.com/cover.jpg";
        BookEntity book = mockBookEntity(4L, false);
        when(bookRepository.findAllWithMetadataByIds(any())).thenReturn(List.of(book));
        when(bookRepository.save(any())).thenReturn(book);
        when(appSettingService.getAppSettings()).thenReturn(mockAppSettings(true, false));
        when(bookMetadataMapper.toBookMetadata(any(), eq(true))).thenReturn(new BookMetadata());

        BookMetadata result = bookCoverService.updateCoverImageFromUrl(4L, url);

        verify(fileService).createThumbnailFromUrl(4L, url);
        assertThat(result).isNotNull();
    }

    @Test
    void updateCoverImageFromFileForBooks_success() throws Exception {
        try (MockedStatic<SecurityContextVirtualThread> ignored = mockStatic(SecurityContextVirtualThread.class)) {
            MultipartFile file = mock(MultipartFile.class);
            when(file.isEmpty()).thenReturn(false);
            when(file.getContentType()).thenReturn("image/jpeg");
            doReturn(new byte[]{1,2,3}).when(file).getBytes();
            BookEntity book = mockBookEntity(5L, false);
            when(bookQueryService.findAllWithMetadataByIds(any())).thenReturn(List.of(book));
            when(bookRepository.findById(5L)).thenReturn(Optional.of(book));
            when(bookRepository.save(any())).thenReturn(book);

            doNothing().when(fileService).createThumbnailFromBytes(eq(5L), any());

            ignored.when(() -> SecurityContextVirtualThread.runWithSecurityContext(any(Runnable.class)))
                    .thenAnswer(invocation -> {
                        Runnable runnable = invocation.getArgument(0);
                        runnable.run();
                        return null;
                    });

            bookCoverService.updateCoverImageFromFileForBooks(Set.of(5L), file);

            ArgumentCaptor<Long> idCaptor = ArgumentCaptor.forClass(Long.class);
            verify(fileService).createThumbnailFromBytes(idCaptor.capture(), any());
            assertThat(idCaptor.getValue()).isEqualTo(5L);
            verify(bookRepository).save(any(BookEntity.class));
        }
    }

    @Test
    void updateCoverImageFromFileForBooks_invalidFile_throws() {
        MultipartFile file = mock(MultipartFile.class);
        when(file.isEmpty()).thenReturn(true);
        assertThatThrownBy(() -> bookCoverService.updateCoverImageFromFileForBooks(Set.of(6L), file))
                .isInstanceOf(ApiError.INVALID_INPUT.createException("dummy").getClass());
    }

    @Test
    void regenerateCover_success() {
        BookEntity book = mockBookEntity(7L, false);
        when(bookRepository.findById(7L)).thenReturn(Optional.of(book));
        BookFileProcessor processor = mock(BookFileProcessor.class);
        when(processorRegistry.getProcessorOrThrow(any())).thenReturn(processor);
        when(processor.generateCover(any())).thenReturn(true);
        when(bookRepository.save(any())).thenReturn(book);

        bookCoverService.regenerateCover(7L);

        verify(processor).generateCover(book);
        verify(bookRepository).save(book);
    }

    @Test
    void regenerateCover_coverLocked_throws() {
        BookEntity book = mockBookEntity(8L, true);
        when(bookRepository.findById(8L)).thenReturn(Optional.of(book));
        assertThatThrownBy(() -> bookCoverService.regenerateCover(8L))
                .isInstanceOf(ApiError.METADATA_LOCKED.createException().getClass());
    }

    @Test
    void regenerateCoversForBooks_success() {
        try (MockedStatic<SecurityContextVirtualThread> ignored = mockStatic(SecurityContextVirtualThread.class)) {
            BookEntity book = mockBookEntity(9L, false);
            when(bookQueryService.findAllWithMetadataByIds(any())).thenReturn(List.of(book));
            BookFileProcessor processor = mock(BookFileProcessor.class);
            when(processorRegistry.getProcessorOrThrow(any())).thenReturn(processor);
            when(processor.generateCover(any())).thenReturn(true);
            when(bookRepository.findById(9L)).thenReturn(Optional.of(book));
            when(bookRepository.save(any())).thenReturn(book);

            ignored.when(() -> SecurityContextVirtualThread.runWithSecurityContext(any(Runnable.class)))
                    .thenAnswer(invocation -> {
                        Runnable runnable = invocation.getArgument(0);
                        runnable.run();
                        return null;
                    });

            bookCoverService.regenerateCoversForBooks(Set.of(9L));

            ArgumentCaptor<BookEntity> bookCaptor = ArgumentCaptor.forClass(BookEntity.class);
            verify(processor).generateCover(bookCaptor.capture());
            assertThat(bookCaptor.getValue().getId()).isEqualTo(9L);
        }
    }

    @Test
    void updateCover_metadataPersistenceSettings_saveToOriginalFile() {
        MultipartFile file = mock(MultipartFile.class);
        BookEntity book = spy(mockBookEntity(10L, false));
        doReturn(Path.of("/dummy/path")).when(book).getFullFilePath();
        when(bookRepository.findAllWithMetadataByIds(any())).thenReturn(List.of(book));
        when(bookRepository.save(any())).thenReturn(book);
        when(appSettingService.getAppSettings()).thenReturn(mockAppSettings(true, false));
        MetadataWriter writer = mock(MetadataWriter.class);
        when(metadataWriterFactory.getWriter(any())).thenReturn(Optional.of(writer));
        when(bookMetadataMapper.toBookMetadata(any(), eq(true))).thenReturn(new BookMetadata());

        try (MockedStatic<FileFingerprint> fingerprintMock = mockStatic(FileFingerprint.class)) {
            fingerprintMock.when(() -> FileFingerprint.generateHash(any())).thenReturn("dummyhash");
            BookMetadata result = bookCoverService.updateCoverImageFromFile(10L, file);

            verify(writer).replaceCoverImageFromUpload(book, file);
            assertThat(result).isNotNull();
        }
    }

    @Test
    void updateCover_metadataPersistenceSettings_noSaveToOriginalFile() {
        MultipartFile file = mock(MultipartFile.class);
        BookEntity book = mockBookEntity(11L, false);
        when(bookRepository.findAllWithMetadataByIds(any())).thenReturn(List.of(book));
        when(bookRepository.save(any())).thenReturn(book);
        when(appSettingService.getAppSettings()).thenReturn(mockAppSettings(false, false));
        when(bookMetadataMapper.toBookMetadata(any(), eq(true))).thenReturn(new BookMetadata());

        BookMetadata result = bookCoverService.updateCoverImageFromFile(11L, file);

        verify(metadataWriterFactory, never()).getWriter(any());
        assertThat(result).isNotNull();
    }

    private com.adityachandel.booklore.model.dto.settings.AppSettings mockAppSettings(boolean saveToOriginalFile, boolean convertCbrCb7ToCbz) {
        MetadataPersistenceSettings settings = new MetadataPersistenceSettings();
        settings.setSaveToOriginalFile(saveToOriginalFile);
        settings.setConvertCbrCb7ToCbz(convertCbrCb7ToCbz);
        return new com.adityachandel.booklore.model.dto.settings.AppSettings() {
            @Override
            public MetadataPersistenceSettings getMetadataPersistenceSettings() {
                return settings;
            }
        };
    }
}

