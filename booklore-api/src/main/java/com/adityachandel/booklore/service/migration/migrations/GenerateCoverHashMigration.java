package com.adityachandel.booklore.service.migration.migrations;

import com.adityachandel.booklore.model.entity.BookEntity;
import com.adityachandel.booklore.repository.BookRepository;
import com.adityachandel.booklore.service.migration.Migration;
import com.adityachandel.booklore.util.BookCoverUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class GenerateCoverHashMigration implements Migration {

    private final BookRepository bookRepository;

    @Override
    public String getKey() {
        return "generateCoverHash";
    }

    @Override
    public String getDescription() {
        return "Generate unique cover hash for all books using BookCoverUtils";
    }

    @Override
    public void execute() {
        log.info("Starting migration: {}", getKey());

        int batchSize = 1000;
        int processedCount = 0;
        int offset = 0;

        while (true) {
            List<BookEntity> bookBatch = bookRepository.findBooksForMigrationBatch(offset, batchSize);
            if (bookBatch.isEmpty()) break;

            for (BookEntity book : bookBatch) {
                if (book.getBookCoverHash() == null) {
                    book.setBookCoverHash(BookCoverUtils.generateCoverHash());
                }
            }

            bookRepository.saveAll(bookBatch);
            processedCount += bookBatch.size();
            offset += batchSize;

            log.info("Migration progress: {} books processed", processedCount);

            if (bookBatch.size() < batchSize) break;
        }

        log.info("Completed migration '{}'. Total books processed: {}", getKey(), processedCount);
    }
}

