package org.booklore.service.bookdrop;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.booklore.exception.ApiError;
import org.booklore.model.dto.Book;
import org.booklore.model.dto.BookFile;
import org.booklore.model.dto.BookMetadata;
import org.booklore.model.dto.request.MetadataRefreshOptions;
import org.booklore.model.dto.settings.AppSettings;
import org.booklore.model.entity.BookdropFileEntity;
import org.booklore.model.enums.BookFileExtension;
import org.booklore.model.enums.MetadataProvider;
import org.booklore.repository.BookdropFileRepository;
import org.booklore.service.appsettings.AppSettingService;
import org.booklore.service.metadata.MetadataRefreshService;
import org.booklore.service.metadata.extractor.MetadataExtractorFactory;
import org.booklore.util.FileService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.io.File;
import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

import static org.booklore.model.entity.BookdropFileEntity.Status.PENDING_REVIEW;

@Slf4j
@AllArgsConstructor
@Service
public class BookdropMetadataService {

    private final BookdropFileRepository bookdropFileRepository;
    private final AppSettingService appSettingService;
    private final ObjectMapper objectMapper;
    private final MetadataExtractorFactory metadataExtractorFactory;
    private final MetadataRefreshService metadataRefreshService;
    private final FileService fileService;

    @Transactional
    public BookdropFileEntity attachInitialMetadata(Long bookdropFileId) throws JacksonException {
        BookdropFileEntity entity = getOrThrow(bookdropFileId);
        BookMetadata initial = extractInitialMetadata(entity);
        extractAndSaveCover(entity);
        String initialJson = objectMapper.writeValueAsString(initial);
        entity.setOriginalMetadata(initialJson);
        entity.setUpdatedAt(Instant.now());
        return bookdropFileRepository.save(entity);
    }

    @Transactional
    public BookdropFileEntity attachFetchedMetadata(Long bookdropFileId) throws JacksonException {
        BookdropFileEntity entity = getOrThrow(bookdropFileId);

        AppSettings appSettings = appSettingService.getAppSettings();

        MetadataRefreshOptions refreshOptions = appSettings.getDefaultMetadataRefreshOptions();

        BookMetadata initial = objectMapper.readValue(entity.getOriginalMetadata(), BookMetadata.class);

        List<MetadataProvider> providers = metadataRefreshService.prepareProviders(refreshOptions);
        Book book = Book.builder()
                .primaryFile(BookFile.builder().fileName(entity.getFileName()).build())
                .metadata(initial)
                .build();

        if (providers.contains(MetadataProvider.GoodReads)) {
            try {
                Thread.sleep(ThreadLocalRandom.current().nextLong(250, 1250));
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }

        Map<MetadataProvider, BookMetadata> metadataMap = metadataRefreshService.fetchMetadataForBook(providers, book);
        BookMetadata fetchedMetadata = metadataRefreshService.buildFetchMetadata(initial, book.getId(), refreshOptions, metadataMap);
        String fetchedJson = objectMapper.writeValueAsString(fetchedMetadata);

        entity.setFetchedMetadata(fetchedJson);
        entity.setStatus(PENDING_REVIEW);
        entity.setUpdatedAt(Instant.now());

        return bookdropFileRepository.save(entity);
    }

    private BookdropFileEntity getOrThrow(Long id) {
        return bookdropFileRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("Bookdrop file not found: " + id));
    }

    private BookMetadata extractInitialMetadata(BookdropFileEntity entity) {
        File file = new File(entity.getFilePath());
        BookFileExtension fileExt = BookFileExtension.fromFileName(file.getName())
            .orElseThrow(() -> ApiError.INVALID_FILE_FORMAT.createException("Unsupported file extension"));
        return metadataExtractorFactory.extractMetadata(fileExt, file);
    }

    private void extractAndSaveCover(BookdropFileEntity entity) {
        File file = new File(entity.getFilePath());
        BookFileExtension fileExt = BookFileExtension.fromFileName(file.getName())
            .orElseThrow(() -> ApiError.INVALID_FILE_FORMAT.createException("Unsupported file extension"));
        byte[] coverBytes = metadataExtractorFactory.extractCover(fileExt, file);
        if (coverBytes != null) {
            try {
                FileService.saveImage(coverBytes, fileService.getTempBookdropCoverImagePath(entity.getId()));
            } catch (IOException e) {
                log.warn("Failed to save extracted cover for file: {}", entity.getFilePath(), e);
            }
        }
    }
}
