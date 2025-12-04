package com.adityachandel.booklore.service.koreader;

import com.adityachandel.booklore.config.CacheConfig;
import com.adityachandel.booklore.exception.ApiError;
import com.adityachandel.booklore.model.dto.koreader.KoreaderBookStatistics;
import com.adityachandel.booklore.model.dto.koreader.KoreaderPageStatistic;
import com.adityachandel.booklore.model.dto.koreader.KoreaderStatisticsImport;
import com.adityachandel.booklore.model.entity.BookEntity;
import com.adityachandel.booklore.model.entity.BookLoreUserEntity;
import com.adityachandel.booklore.model.entity.ReadingSessionEntity;
import com.adityachandel.booklore.repository.BookRepository;
import com.adityachandel.booklore.repository.ReadingSessionRepository;
import com.adityachandel.booklore.repository.UserRepository;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Caching;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import org.springframework.web.multipart.MultipartFile;

@Slf4j
@AllArgsConstructor
@Service
public class KoreaderStatisticsImportService {

    private final ReadingSessionRepository sessionRepository;
    private final BookRepository bookRepository;
    private final UserRepository userRepository;

    /**
     * Import KOReader statistics data for a user
     *
     * @param userId The BookLore user ID
     * @param importData The statistics data from KOReader
     * @return Summary of the import results
     */
    @Transactional
    @Caching(evict = {
            @CacheEvict(value = CacheConfig.KOREADER_STATS_SUMMARY, key = "#userId"),
            @CacheEvict(value = CacheConfig.KOREADER_STATS_DAILY, key = "#userId"),
            @CacheEvict(value = CacheConfig.KOREADER_STATS_CALENDAR, allEntries = true),
            @CacheEvict(value = CacheConfig.KOREADER_STATS_DAY_OF_WEEK, key = "#userId")
    })
    public ImportResult importStatistics(Long userId, KoreaderStatisticsImport importData) {
        log.info("Starting statistics import for userId={}, received {} books and {} sessions",
                userId,
                importData.getBooks() != null ? importData.getBooks().size() : 0,
                importData.getStats() != null ? importData.getStats().size() : 0);

        // Log book data received
        if (importData.getBooks() != null) {
            for (var book : importData.getBooks()) {
                log.debug("Received book: title='{}', md5='{}'", book.getTitle(), book.getMd5());
            }
        }

        BookLoreUserEntity user = userRepository.findById(userId)
                .orElseThrow(() -> ApiError.GENERIC_NOT_FOUND.createException("User not found with id " + userId));

        ImportResult result = new ImportResult();
        List<ReadingSessionEntity> sessionsToSave = new ArrayList<>();

        // OPTIMIZATION: Pre-load all unique MD5 hashes from the sessions into a map
        // This reduces N database queries to 1 query
        var uniqueHashes = importData.getStats().stream()
                .map(KoreaderPageStatistic::getBookMd5)
                .distinct()
                .toList();

        log.info("Found {} unique book hashes in {} sessions", uniqueHashes.size(), importData.getStats().size());

        // Load all books by these hashes in one query
        var booksByHash = new java.util.HashMap<String, BookEntity>();
        for (String hash : uniqueHashes) {
            bookRepository.findByCurrentHash(hash).ifPresent(book -> booksByHash.put(hash, book));
        }

        log.info("Found {} books in BookLore matching the hashes", booksByHash.size());

        // Log which hashes don't have matching books
        for (String hash : uniqueHashes) {
            if (!booksByHash.containsKey(hash)) {
                log.warn("Book not found in BookLore for MD5 hash: '{}' (length={})", hash, hash != null ? hash.length() : 0);
                result.addSkippedBook(hash);
            }
        }

        // OPTIMIZATION 2: Pre-load all existing sessions for ALL books in ONE query
        // This reduces potentially 50+ queries to just ONE query
        var existingSessionsByBook = new java.util.HashMap<Long, java.util.Set<String>>();

        if (!booksByHash.isEmpty()) {
            var bookIds = booksByHash.values().stream()
                    .map(BookEntity::getId)
                    .toList();

            // Single query to get ALL sessions for ALL matched books
            var allSessions = sessionRepository.findByUserIdAndBookIdIn(userId, bookIds);

            // Initialize empty sets for each book
            for (BookEntity book : booksByHash.values()) {
                existingSessionsByBook.put(book.getId(), new java.util.HashSet<>());
            }

            // Group sessions by book ID
            for (var session : allSessions) {
                Long bookId = session.getBook().getId();
                String key = session.getPageNumber() + "_" + session.getStartTime().getEpochSecond();
                existingSessionsByBook.get(bookId).add(key);
            }

            log.info("Loaded {} existing sessions across {} books in single query",
                    allSessions.size(), bookIds.size());
        }

        // Process each reading session
        for (KoreaderPageStatistic pageStat : importData.getStats()) {
            try {
                // Look up book from our pre-loaded map
                BookEntity book = booksByHash.get(pageStat.getBookMd5());

                if (book == null) {
                    // Book doesn't exist in BookLore - skip this session
                    result.skippedSessions++;
                    continue;
                }

                log.debug("Processing session for book: id={}, hash='{}'", book.getId(), book.getCurrentHash());

                Instant startTime = Instant.ofEpochSecond(pageStat.getStartTime());

                // Check if this session already exists using our in-memory set
                String sessionKey = pageStat.getPage() + "_" + pageStat.getStartTime();
                if (existingSessionsByBook.get(book.getId()).contains(sessionKey)) {
                    log.debug("Session already exists - skipping (bookId={}, page={}, startTime={})",
                            book.getId(), pageStat.getPage(), startTime);
                    result.duplicateSessions++;
                    continue;
                }

                // Create new reading session entity
                ReadingSessionEntity session = ReadingSessionEntity.builder()
                        .user(user)
                        .book(book)
                        .source(pageStat.getDeviceId() != null ? "koreader:" + pageStat.getDeviceId() : "koreader")
                        .pageNumber(pageStat.getPage())
                        .startTime(startTime)
                        .durationSeconds(pageStat.getDuration())
                        .totalPages(pageStat.getTotalPages())
                        .createdAt(Instant.now())
                        .build();

                sessionsToSave.add(session);
                result.importedSessions++;

            } catch (Exception e) {
                log.error("Error processing reading session: {}", e.getMessage(), e);
                result.failedSessions++;
            }
        }

        // Batch save all sessions
        if (!sessionsToSave.isEmpty()) {
            sessionRepository.saveAll(sessionsToSave);
            log.info("Saved {} reading sessions for userId={}", sessionsToSave.size(), userId);
        }

        log.info("Import completed for userId={}: imported={}, skipped={}, duplicates={}, failed={}",
                userId, result.importedSessions, result.skippedSessions,
                result.duplicateSessions, result.failedSessions);

        return result;
    }

    /**
     * Import statistics from an uploaded SQLite database file
     * This is more efficient than JSON import as it processes the file directly
     *
     * @param userId The BookLore user ID
     * @param file The uploaded SQLite database file
     * @return Summary of the import results
     */
    @Transactional
    public ImportResult importFromSqliteFile(Long userId, MultipartFile file) throws Exception {
        log.info("Processing SQLite file upload for userId={}, filename={}, size={} bytes",
                userId, file.getOriginalFilename(), file.getSize());

        // Save file to temporary location
        Path tempFile = Files.createTempFile("koreader-stats-", ".sqlite3");
        try {
            file.transferTo(tempFile.toFile());
            log.debug("Saved uploaded file to temporary location: {}", tempFile);

            // Open SQLite database
            String jdbcUrl = "jdbc:sqlite:" + tempFile.toAbsolutePath();
            try (Connection conn = DriverManager.getConnection(jdbcUrl)) {
                log.debug("Successfully opened SQLite database");

                // Extract books and stats from the database
                // First get the book ID to MD5 mapping
                var bookIdToMd5 = extractBookIdToMd5Map(conn);

                List<KoreaderBookStatistics> books = extractBooksFromDb(conn);
                List<KoreaderPageStatistic> stats = extractPageStatsFromDb(conn, bookIdToMd5);

                log.info("Extracted {} books and {} reading sessions from SQLite file", books.size(), stats.size());

                // Create import data object and process it using existing logic
                KoreaderStatisticsImport importData = new KoreaderStatisticsImport();
                importData.setBooks(books);
                importData.setStats(stats);
                importData.setVersion("sqlite-upload");

                return importStatistics(userId, importData);

            } catch (Exception e) {
                log.error("Failed to process SQLite database: {}", e.getMessage(), e);
                throw new RuntimeException("Failed to process SQLite database: " + e.getMessage(), e);
            }

        } catch (IOException e) {
            log.error("Failed to save uploaded file: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to save uploaded file: " + e.getMessage(), e);
        } finally {
            // Clean up temporary file
            try {
                Files.deleteIfExists(tempFile);
                log.debug("Cleaned up temporary file: {}", tempFile);
            } catch (IOException e) {
                log.warn("Failed to delete temporary file: {}", tempFile, e);
            }
        }
    }

    /**
     * Import statistics from an uploaded SQLite database file asynchronously
     * Returns immediately with an accepted status, processing happens in background
     *
     * @param userId The BookLore user ID
     * @param tempFilePath Path to the temporary file (already saved by controller)
     * @param originalFilename Original filename for logging
     * @return CompletableFuture with the import results
     */
    @Async
    @Transactional
    public CompletableFuture<ImportResult> importFromSqliteFileAsync(Long userId, Path tempFilePath, String originalFilename) {
        log.info("Starting async SQLite file processing for userId={}, filename={}",
                userId, originalFilename);

        try {
            // Open SQLite database from the pre-saved file
            String jdbcUrl = "jdbc:sqlite:" + tempFilePath.toAbsolutePath();
            try (Connection conn = DriverManager.getConnection(jdbcUrl)) {
                log.debug("Successfully opened SQLite database");

                // Extract books and stats from the database
                var bookIdToMd5 = extractBookIdToMd5Map(conn);
                List<KoreaderBookStatistics> books = extractBooksFromDb(conn);
                List<KoreaderPageStatistic> stats = extractPageStatsFromDb(conn, bookIdToMd5);

                log.info("Extracted {} books and {} reading sessions from SQLite file", books.size(), stats.size());

                // Create import data object and process it using existing logic
                KoreaderStatisticsImport importData = new KoreaderStatisticsImport();
                importData.setBooks(books);
                importData.setStats(stats);
                importData.setVersion("sqlite-upload-async");

                ImportResult result = importStatistics(userId, importData);

                log.info("Async import completed successfully for userId={}: imported={}, skipped={}, duplicates={}, failed={}",
                        userId, result.importedSessions, result.skippedSessions,
                        result.duplicateSessions, result.failedSessions);

                return CompletableFuture.completedFuture(result);

            } catch (Exception e) {
                log.error("Failed to process SQLite database asynchronously: {}", e.getMessage(), e);
                ImportResult errorResult = new ImportResult();
                errorResult.failedSessions = -1; // Indicate complete failure
                return CompletableFuture.completedFuture(errorResult);
            }

        } finally {
            // Clean up temporary file after processing
            try {
                Files.deleteIfExists(tempFilePath);
                log.debug("Cleaned up temporary file: {}", tempFilePath);
            } catch (IOException e) {
                log.warn("Failed to delete temporary file: {}", tempFilePath, e);
            }
        }
    }

    /**
     * Extract book ID to MD5 mapping from SQLite database
     */
    private java.util.Map<Long, String> extractBookIdToMd5Map(Connection conn) throws Exception {
        var bookIdToMd5 = new java.util.HashMap<Long, String>();
        String sql = "SELECT id, md5 FROM book";

        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            while (rs.next()) {
                bookIdToMd5.put(rs.getLong("id"), rs.getString("md5"));
            }
        }

        return bookIdToMd5;
    }

    /**
     * Extract book data from SQLite database
     */
    private List<KoreaderBookStatistics> extractBooksFromDb(Connection conn) throws Exception {
        List<KoreaderBookStatistics> books = new ArrayList<>();
        String sql = "SELECT title, authors, notes, last_open, highlights, pages, series, language, md5, total_read_time, total_read_pages FROM book";

        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            while (rs.next()) {
                KoreaderBookStatistics book = new KoreaderBookStatistics();
                book.setTitle(rs.getString("title"));
                book.setAuthors(rs.getString("authors"));
                book.setNotes(rs.getInt("notes"));
                book.setLastOpen(rs.getLong("last_open"));
                book.setHighlights(rs.getInt("highlights"));
                book.setPages(rs.getInt("pages"));
                book.setSeries(rs.getString("series"));
                book.setLanguage(rs.getString("language"));
                book.setMd5(rs.getString("md5"));
                book.setTotalReadTime(rs.getInt("total_read_time"));
                book.setTotalReadPages(rs.getInt("total_read_pages"));
                books.add(book);
            }
        }

        return books;
    }

    /**
     * Extract page statistics from SQLite database
     */
    private List<KoreaderPageStatistic> extractPageStatsFromDb(Connection conn, java.util.Map<Long, String> bookIdToMd5) throws Exception {

        List<KoreaderPageStatistic> stats = new ArrayList<>();
        String sql = "SELECT id_book, page, start_time, duration, total_pages FROM page_stat_data";

        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            while (rs.next()) {
                long bookId = rs.getLong("id_book");
                String bookMd5 = bookIdToMd5.get(bookId);

                if (bookMd5 != null) {
                    KoreaderPageStatistic stat = new KoreaderPageStatistic();
                    stat.setBookMd5(bookMd5);
                    stat.setPage(rs.getInt("page"));
                    stat.setStartTime(rs.getLong("start_time"));
                    stat.setDuration(rs.getInt("duration"));
                    stat.setTotalPages(rs.getInt("total_pages"));
                    stat.setDeviceId("sqlite-upload");
                    stats.add(stat);
                }
            }
        }

        return stats;
    }

    /**
     * Result summary of the import operation
     */
    public static class ImportResult {
        public int importedSessions = 0;
        public int skippedSessions = 0;
        public int duplicateSessions = 0;
        public int failedSessions = 0;
        public List<String> skippedBookHashes = new ArrayList<>();

        public void addSkippedBook(String hash) {
            if (!skippedBookHashes.contains(hash)) {
                skippedBookHashes.add(hash);
            }
        }

        public int getTotalProcessed() {
            return importedSessions + skippedSessions + duplicateSessions + failedSessions;
        }
    }
}
