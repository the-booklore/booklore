package org.booklore.controller;

import org.booklore.mapper.BookMetadataMapper;
import org.booklore.model.MetadataUpdateContext;
import org.booklore.model.MetadataUpdateWrapper;
import org.booklore.model.dto.BookMetadata;
import org.booklore.model.entity.BookEntity;
import org.booklore.model.entity.BookMetadataEntity;
import org.booklore.repository.BookRepository;
import org.booklore.service.audit.AuditService;
import org.booklore.service.metadata.BookMetadataService;
import org.booklore.service.metadata.BookMetadataUpdater;
import org.booklore.service.metadata.MetadataManagementService;
import org.booklore.service.metadata.MetadataMatchService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MetadataControllerTest {

    @Mock
    private BookMetadataService bookMetadataService;
    @Mock
    private BookMetadataUpdater bookMetadataUpdater;
    @Mock
    private BookRepository bookRepository;
    @Mock
    private BookMetadataMapper bookMetadataMapper;
    @Mock
    private MetadataMatchService metadataMatchService;
    @Mock
    private MetadataManagementService metadataManagementService;
    @Mock
    private AuditService auditService;

    @InjectMocks
    private MetadataController metadataController;

    @Test
    void updateMetadata_shouldDelegateToService() {
        long bookId = 1L;
        MetadataUpdateWrapper wrapper = MetadataUpdateWrapper.builder().build();
        BookMetadata bookMetadata = new BookMetadata();

        // Setup book entity with metadata
        BookEntity bookEntity = new BookEntity();
        bookEntity.setId(bookId);
        BookMetadataEntity metadata = new BookMetadataEntity();
        metadata.setTitle("Test Book");
        bookEntity.setMetadata(metadata);

        when(bookRepository.findAllWithMetadataByIds(Collections.singleton(bookId)))
                .thenReturn(Collections.singletonList(bookEntity));
        doNothing().when(bookMetadataUpdater).setBookMetadata(any(MetadataUpdateContext.class));
        when(bookRepository.save(any(BookEntity.class))).thenReturn(bookEntity);
        when(bookMetadataMapper.toBookMetadata(metadata, true)).thenReturn(bookMetadata);

        ResponseEntity<BookMetadata> response = metadataController.updateMetadata(wrapper, bookId, true);

        assertEquals(200, response.getStatusCode().value());
        assertEquals(bookMetadata, response.getBody());
        verify(bookMetadataUpdater).setBookMetadata(any(MetadataUpdateContext.class));
        verify(bookRepository).save(bookEntity);
        verify(auditService).log(any(), anyString(), anyLong(), anyString());
    }
}
