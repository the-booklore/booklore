package com.adityachandel.booklore.service.file;

import com.adityachandel.booklore.mapper.AdditionalFileMapper;
import com.adityachandel.booklore.model.dto.BookFile;
import com.adityachandel.booklore.model.entity.BookFileEntity;
import com.adityachandel.booklore.repository.BookAdditionalFileRepository;
import com.adityachandel.booklore.service.monitoring.MonitoringRegistrationService;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

@Slf4j
@AllArgsConstructor
@Service
public class AdditionalFileService {

    private static final Pattern NON_ASCII = Pattern.compile("[^\\x00-\\x7F]");

    private final BookAdditionalFileRepository additionalFileRepository;
    private final AdditionalFileMapper additionalFileMapper;
    private final MonitoringRegistrationService monitoringRegistrationService;

    public List<BookFile> getAdditionalFilesByBookId(Long bookId) {
        List<BookFileEntity> entities = additionalFileRepository.findByBookId(bookId);
        return additionalFileMapper.toAdditionalFiles(entities);
    }

    public List<BookFile> getAdditionalFilesByBookIdAndIsBook(Long bookId, boolean isBook) {
        List<BookFileEntity> entities = additionalFileRepository.findByBookIdAndIsBookFormat(bookId, isBook);
        return additionalFileMapper.toAdditionalFiles(entities);
    }

    @Transactional
    public void deleteAdditionalFile(Long fileId) {
        Optional<BookFileEntity> fileOpt = additionalFileRepository.findById(fileId);
        if (fileOpt.isEmpty()) {
            throw new IllegalArgumentException("Additional file not found with id: " + fileId);
        }

        BookFileEntity file = fileOpt.get();

        try {
            monitoringRegistrationService.unregisterSpecificPath(file.getFullFilePath().getParent());

            Files.deleteIfExists(file.getFullFilePath());
            log.info("Deleted additional file: {}", file.getFullFilePath());

            additionalFileRepository.delete(file);
        } catch (IOException e) {
            log.warn("Failed to delete physical file: {}", file.getFullFilePath(), e);
            additionalFileRepository.delete(file);
        }
    }

    public ResponseEntity<Resource> downloadAdditionalFile(Long fileId) throws IOException {
        Optional<BookFileEntity> fileOpt = additionalFileRepository.findById(fileId);
        if (fileOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        BookFileEntity file = fileOpt.get();
        Path filePath = file.getFullFilePath();

        if (!Files.exists(filePath)) {
            return ResponseEntity.notFound().build();
        }

        Resource resource = new UrlResource(filePath.toUri());

        String encodedFilename = URLEncoder.encode(file.getFileName(), StandardCharsets.UTF_8).replace("+", "%20");
        String fallbackFilename = NON_ASCII.matcher(file.getFileName()).replaceAll("_");
        String contentDisposition = String.format("attachment; filename=\"%s\"; filename*=UTF-8''%s",
                fallbackFilename, encodedFilename);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .header(HttpHeaders.CONTENT_DISPOSITION, contentDisposition)
                .body(resource);
    }
}
