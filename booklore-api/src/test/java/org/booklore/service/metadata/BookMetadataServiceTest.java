package org.booklore.service.metadata;

import org.booklore.mapper.BookMetadataMapper;
import org.booklore.model.MetadataUpdateContext;
import org.booklore.model.MetadataUpdateWrapper;
import org.booklore.model.entity.BookEntity;
import org.booklore.model.entity.BookMetadataEntity;
import org.booklore.model.enums.MetadataReplaceMode;
import org.booklore.repository.BookRepository;
import org.booklore.service.audit.AuditService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BookMetadataServiceTest {

    @Mock
    private BookRepository bookRepository;
    @Mock
    private BookMetadataUpdater bookMetadataUpdater;
    @Mock
    private BookMetadataMapper bookMetadataMapper;
    @Mock
    private AuditService auditService;

    @InjectMocks
    private BookMetadataService bookMetadataService;

    @Test
    void updateMetadata_shouldSetCorrectContext() {
        long bookId = 1L;
        MetadataUpdateWrapper wrapper = MetadataUpdateWrapper.builder().build();
        BookEntity bookEntity = new BookEntity();
        bookEntity.setId(bookId);
        bookEntity.setMetadata(new BookMetadataEntity());

        when(bookRepository.findByIdFull(bookId)).thenReturn(Optional.of(bookEntity));

        bookMetadataService.updateMetadata(bookId, wrapper, true);

        ArgumentCaptor<MetadataUpdateContext> captor = ArgumentCaptor.forClass(MetadataUpdateContext.class);
        verify(bookMetadataUpdater).setBookMetadata(captor.capture());
        MetadataUpdateContext context = captor.getValue();

        assertEquals(bookEntity, context.getBookEntity());
        assertEquals(wrapper, context.getMetadataUpdateWrapper());
        assertTrue(context.isMergeCategories());
        assertEquals(MetadataReplaceMode.REPLACE_ALL, context.getReplaceMode());
        assertFalse(context.isMergeTags(), "mergeTags should be false to allow deletion of tags");
        assertFalse(context.isMergeMoods(), "mergeMoods should be false to allow deletion of moods");
    }
}
