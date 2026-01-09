package com.adityachandel.booklore.service.metadata.writer;

import com.adityachandel.booklore.model.MetadataClearFlags;
import com.adityachandel.booklore.model.entity.BookEntity;
import com.adityachandel.booklore.model.entity.BookMetadataEntity;
import com.adityachandel.booklore.model.enums.BookFileType;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;

public interface MetadataWriter {

    void saveMetadataToFile(File file, BookMetadataEntity metadata, String thumbnailUrl, MetadataClearFlags clearFlags);

    boolean shouldSaveMetadataToFile(File file);

    default void replaceCoverImageFromUpload(BookEntity bookEntity, MultipartFile file) {
    }

    default void replaceCoverImageFromBytes(BookEntity bookEntity, byte[] file) {
    }

    default void replaceCoverImageFromUrl(BookEntity bookEntity, String url) {
    }

    BookFileType getSupportedBookType();
}
