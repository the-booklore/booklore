package com.adityachandel.booklore.service.metadata;

import com.adityachandel.booklore.mapper.BookMapper;
import com.adityachandel.booklore.mapper.BookMetadataMapper;
import com.adityachandel.booklore.mapper.MetadataClearFlagsMapper;
import com.adityachandel.booklore.model.dto.request.BulkMetadataUpdateRequest;
import com.adityachandel.booklore.model.entity.BookEntity;
import com.adityachandel.booklore.model.entity.BookMetadataEntity;
import com.adityachandel.booklore.model.enums.MetadataProvider;
import com.adityachandel.booklore.repository.BookMetadataRepository;
import com.adityachandel.booklore.repository.BookRepository;
import com.adityachandel.booklore.service.NotificationService;
import com.adityachandel.booklore.service.book.BookQueryService;
import com.adityachandel.booklore.service.metadata.extractor.CbxMetadataExtractor;
import com.adityachandel.booklore.service.metadata.parser.BookParser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;

import java.util.Map;
import java.util.Set;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BookMetadataServiceConcurrencyTest {

    @Mock
    private BookRepository bookRepository;
    @Mock
    private BookMapper bookMapper;
    @Mock
    private BookMetadataMapper bookMetadataMapper;
    @Mock
    private BookMetadataUpdater bookMetadataUpdater;
    @Mock
    private NotificationService notificationService;
    @Mock
    private BookMetadataRepository bookMetadataRepository;
    @Mock
    private BookQueryService bookQueryService;
    @Mock
    private Map<MetadataProvider, BookParser> parserMap;
    @Mock
    private CbxMetadataExtractor cbxMetadataExtractor;
    @Mock
    private MetadataClearFlagsMapper metadataClearFlagsMapper;
    @Mock
    private PlatformTransactionManager transactionManager;

    @InjectMocks
    private BookMetadataService bookMetadataService;

    @BeforeEach
    void setUp() {
    }

    @Test
    void bulkUpdateMetadata_FetchingStrategyVerification() {

        BulkMetadataUpdateRequest request = new BulkMetadataUpdateRequest();
        request.setBookIds(Set.of(1L, 2L));

        BookEntity book1 = new BookEntity();
        book1.setId(1L);
        book1.setMetadata(new BookMetadataEntity());

        BookEntity book2 = new BookEntity();
        book2.setId(2L);
        book2.setMetadata(new BookMetadataEntity());

        when(transactionManager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        when(bookRepository.findByIdWithBookFiles(1L)).thenReturn(getUserBook(1L));
        when(bookRepository.findByIdWithBookFiles(2L)).thenReturn(getUserBook(2L));

        bookMetadataService.bulkUpdateMetadata(request, false, false, false);

        verify(bookRepository, never()).findAllWithMetadataByIds(anySet());

        verify(bookRepository, times(1)).findByIdWithBookFiles(1L);
        verify(bookRepository, times(1)).findByIdWithBookFiles(2L);
    }
    
    private java.util.Optional<BookEntity> getUserBook(Long id) {
        BookEntity book = new BookEntity();
        book.setId(id);
        book.setMetadata(new BookMetadataEntity());
        return java.util.Optional.of(book);
    }
}
