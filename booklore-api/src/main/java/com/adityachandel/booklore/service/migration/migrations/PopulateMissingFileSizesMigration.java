package com.adityachandel.booklore.service.migration.migrations;

import com.adityachandel.booklore.model.entity.BookEntity;
import com.adityachandel.booklore.repository.BookRepository;
import com.adityachandel.booklore.service.migration.Migration;
import com.adityachandel.booklore.util.FileUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class PopulateMissingFileSizesMigration implements Migration {

    private final BookRepository bookRepository;

    @Override
    public String getKey() {
        return "populateFileSizes";
    }

    @Override
    public String getDescription() {
        return "Populate file size for existing books";
    }

    @Override
    public void execute() {
        log.info("Starting migration: {} for books.", getKey());

        List<BookEntity> books = bookRepository.findAllWithMetadataByFileSizeKbIsNull();

        for (BookEntity book : books) {
            Long sizeInKb = FileUtils.getFileSizeInKb(book);
            if (sizeInKb != null) {
                book.setFileSizeKb(sizeInKb);
            }
        }

        bookRepository.saveAll(books);

        log.info("Migration '{}' executed successfully for {} books.", getKey(), books.size());
    }
}

