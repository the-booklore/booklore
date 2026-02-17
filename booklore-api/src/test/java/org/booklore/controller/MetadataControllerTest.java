package org.booklore.controller;

import org.booklore.config.security.service.AuthenticationService;
import org.booklore.model.MetadataUpdateWrapper;
import org.booklore.model.dto.BookMetadata;
import org.booklore.service.metadata.BookMetadataService;
import org.booklore.service.metadata.MetadataManagementService;
import org.booklore.service.audit.AuditService;
import org.booklore.service.metadata.MetadataMatchService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MetadataControllerTest {

    @Mock
    private BookMetadataService bookMetadataService;
    @Mock
    private AuthenticationService authenticationService;
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

        when(bookMetadataService.updateMetadata(eq(bookId), eq(wrapper), eq(true))).thenReturn(bookMetadata);

        ResponseEntity<BookMetadata> response = metadataController.updateMetadata(wrapper, bookId, true);

        assertEquals(200, response.getStatusCode().value());
        assertEquals(bookMetadata, response.getBody());
        verify(bookMetadataService).updateMetadata(bookId, wrapper, true);
    }
}
