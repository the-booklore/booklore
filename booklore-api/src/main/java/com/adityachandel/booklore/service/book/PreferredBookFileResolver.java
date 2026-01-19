package com.adityachandel.booklore.service.book;

import com.adityachandel.booklore.exception.ApiError;
import com.adityachandel.booklore.model.entity.BookEntity;
import com.adityachandel.booklore.model.entity.BookFileEntity;
import com.adityachandel.booklore.model.entity.LibraryEntity;
import com.adityachandel.booklore.model.enums.BookFileType;
import com.adityachandel.booklore.service.appsettings.AppSettingService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class PreferredBookFileResolver {

    private static final List<BookFileType> HARDCODED_DEFAULT_ORDER = List.of(
            BookFileType.EPUB,
            BookFileType.PDF,
            BookFileType.CBX,
            BookFileType.FB2,
            BookFileType.MOBI,
            BookFileType.AZW3
    );

    private final AppSettingService appSettingService;

    public BookFileEntity resolvePrimaryBookFile(BookEntity bookEntity) {
        return resolvePrimaryBookFile(bookEntity, null);
    }

    public BookFileEntity resolvePrimaryBookFile(BookEntity bookEntity, BookFileType targetFormat) {
        if (bookEntity == null) {
            throw new IllegalStateException("Book not found");
        }

        List<BookFileEntity> bookFiles = bookEntity.getBookFiles();
        if (bookFiles == null || bookFiles.isEmpty()) {
            throw new IllegalStateException("Book file not found");
        }

        List<BookFileEntity> availableBookFiles = bookFiles.stream()
                .filter(BookFileEntity::isBook)
                .toList();
        if (availableBookFiles.isEmpty()) {
            throw new IllegalStateException("Book file not found");
        }

        if (targetFormat != null) {
            return availableBookFiles.stream()
                    .filter(file -> file.getBookType() == targetFormat)
                    .findFirst()
                    .orElseThrow(() -> ApiError.GENERIC_BAD_REQUEST
                            .createException("Book does not have requested format: " + targetFormat));
        }

        List<BookFileType> preferredOrder = resolvePreferredOrder(bookEntity.getLibrary());
        for (BookFileType type : preferredOrder) {
            for (BookFileEntity file : availableBookFiles) {
                if (file.getBookType() == type) {
                    return file;
                }
            }
        }

        return availableBookFiles.stream()
                .min(Comparator.comparing(BookFileEntity::getId, Comparator.nullsLast(Long::compareTo)))
                .orElse(availableBookFiles.getFirst());
    }

    private List<BookFileType> resolvePreferredOrder(LibraryEntity libraryEntity) {
        // 1. Try library-specific order
        if (libraryEntity != null) {
            List<BookFileType> libraryOrder = libraryEntity.getPreferredBookFormatOrder();
            if (libraryOrder != null && !libraryOrder.isEmpty()) {
                List<BookFileType> normalized = libraryOrder.stream()
                        .filter(Objects::nonNull)
                        .distinct()
                        .toList();
                if (!normalized.isEmpty()) {
                    return normalized;
                }
            }
        }

        // 2. Try global setting
        List<BookFileType> globalOrder = appSettingService.getAppSettings().getPreferredBookFormatOrder();
        if (globalOrder != null && !globalOrder.isEmpty()) {
            List<BookFileType> normalized = globalOrder.stream()
                    .filter(Objects::nonNull)
                    .distinct()
                    .toList();
            if (!normalized.isEmpty()) {
                return normalized;
            }
        }

        // 3. Fall back to hardcoded default
        return HARDCODED_DEFAULT_ORDER;
    }
}