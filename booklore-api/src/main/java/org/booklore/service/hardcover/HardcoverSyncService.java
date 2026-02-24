package org.booklore.service.hardcover;

import org.booklore.model.dto.HardcoverSyncSettings;
import org.booklore.model.entity.BookEntity;
import org.booklore.model.entity.BookMetadataEntity;
import org.booklore.repository.BookMetadataRepository;
import org.booklore.repository.BookRepository;
import org.booklore.service.metadata.parser.hardcover.GraphQLRequest;
import org.booklore.util.MarkdownEscaper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Service to interact with the Hardcover GraphQL API.
 * Handles reading progress sync and journal entry management.
 * Uses per-user Hardcover API tokens.
 */
@Slf4j
@Service
public class HardcoverSyncService {

    private static final String HARDCOVER_API_URL = "https://api.hardcover.app/v1/graphql";
    private static final int STATUS_CURRENTLY_READING = 2;
    private static final int STATUS_READ = 3;
    private static final List<Map<String, Object>> TAGS = List.of(
            Map.of("tag", "BookLore", "category", "general", "spoiler", false),
            Map.of("tag", "Kobo", "category", "general", "spoiler", false)
    );

    private final RestClient restClient;
    private final HardcoverSyncSettingsService hardcoverSyncSettingsService;
    private final BookRepository bookRepository;
    private final BookMetadataRepository bookMetadataRepository;

    @Autowired
    public HardcoverSyncService(HardcoverSyncSettingsService hardcoverSyncSettingsService,
                                BookRepository bookRepository,
                                BookMetadataRepository bookMetadataRepository) {
        this.hardcoverSyncSettingsService = hardcoverSyncSettingsService;
        this.bookRepository = bookRepository;
        this.bookMetadataRepository = bookMetadataRepository;
        this.restClient = RestClient.builder()
                .baseUrl(HARDCOVER_API_URL)
                .build();
    }

    // === Reading Progress Sync ===

    /**
     * Asynchronously sync Kobo reading progress to Hardcover.
     * This method is non-blocking and will not fail the calling process if sync fails.
     * Uses the user's personal Hardcover API key if configured.
     *
     * @param bookId The book ID to sync progress for
     * @param progressPercent The reading progress as a percentage (0-100)
     * @param userId The user ID whose reading progress is being synced
     */
    @Async
    @Transactional
    public void syncProgressToHardcover(Long bookId, Float progressPercent, Long userId) {
        try {
            // Get user's Hardcover settings
            HardcoverSyncSettings userSettings = hardcoverSyncSettingsService.getSettingsForUserId(userId);

            if (!isHardcoverSyncEnabledForUser(userSettings)) {
                log.trace("Hardcover sync skipped for user {}: not enabled or no API token configured", userId);
                return;
            }

            String apiToken = userSettings.getHardcoverApiKey();

            if (progressPercent == null) {
                log.debug("Hardcover sync skipped: no progress to sync");
                return;
            }

            // Fetch book fresh within the async context to avoid lazy loading issues
            BookEntity book = bookRepository.findById(bookId).orElse(null);
            if (book == null) {
                log.debug("Hardcover sync skipped: book {} not found", bookId);
                return;
            }

            // Resolve the book on Hardcover (stored ID first, then ISBN fallback; persist for future syncs)
            HardcoverBookInfo hardcoverBook = resolveHardcoverBook(book.getMetadata(), apiToken, true);
            if (hardcoverBook == null) {
                log.debug("Hardcover sync skipped: book {} not found on Hardcover", bookId);
                return;
            }

            // Determine the status based on progress
            int statusId = progressPercent >= 99.0f ? STATUS_READ : STATUS_CURRENTLY_READING;

            // Calculate progress in pages
            int progressPages = 0;
            if (hardcoverBook.pages() != null && hardcoverBook.pages() > 0) {
                progressPages = Math.round((progressPercent / 100.0f) * hardcoverBook.pages());
                progressPages = Math.max(0, Math.min(hardcoverBook.pages(), progressPages));
            }
            log.info("Progress calculation: userId={}, progressPercent={}%, totalPages={}, progressPages={}",
                    userId, progressPercent, hardcoverBook.pages(), progressPages);

            // Step 1: Add/update the book in user's library
            Integer bookIdInt = Integer.parseInt(hardcoverBook.bookId());
            Integer userBookId = insertOrGetUserBook(bookIdInt, hardcoverBook.editionId(), statusId, apiToken);
            if (userBookId == null) {
                log.warn("Hardcover sync failed: could not get user_book_id for book {}", bookId);
                return;
            }

            // Step 2: Create or update the reading progress
            boolean isFinished = progressPercent >= 99.0f;
            boolean success = upsertReadingProgress(userBookId, hardcoverBook.editionId(), progressPages, isFinished, apiToken);

            if (success) {
                log.info("Synced progress to Hardcover: userId={}, book={}, hardcoverBookId={}, progress={}% ({}pages)",
                        userId, bookId, hardcoverBook.bookId(), Math.round(progressPercent), progressPages);
            }

        } catch (Exception e) {
            log.error("Failed to sync progress to Hardcover for book {} (user {}): {}",
                    bookId, userId, e.getMessage());
        }
    }

    // === Journal Entry Management ===

    public Integer getPrivacy(String apiToken) {
        String query = """
                query GetPrivacy {
                  me {
                    account_privacy_setting_id
                  }
                }
                """;

        GraphQLRequest request = new GraphQLRequest();
        request.setQuery(query);

        Map<String, Object> response = executeGraphQL(request, apiToken);
        if (response == null) return null;

        Map<String, Object> data = asMap(response.get("data"));
        if (data == null) return null;
        Map<String, Object> me = asMap(data.get("me"));
        if (me == null) return null;

        Object privacyId = me.get("account_privacy_setting_id");
        return privacyId instanceof Number n ? n.intValue() : null;
    }

    public HardcoverUserBookInfo getUserBook(String apiToken, String hardcoverBookId) {
        String query = """
                query GetUserBook($bookId: Int!) {
                  me {
                    user_books(where: {book_id: {_eq: $bookId}}, limit: 1) {
                      id
                      edition {
                        id
                        pages
                      }
                    }
                  }
                }
                """;

        int bookIdInt;
        try {
            bookIdInt = Integer.parseInt(hardcoverBookId);
        } catch (NumberFormatException e) {
            log.warn("Invalid Hardcover book ID: {}", hardcoverBookId);
            return null;
        }

        GraphQLRequest request = new GraphQLRequest();
        request.setQuery(query);
        request.setVariables(Map.of("bookId", bookIdInt));

        Map<String, Object> response = executeGraphQL(request, apiToken);
        if (response == null) return null;

        Map<String, Object> data = asMap(response.get("data"));
        if (data == null) return null;
        Map<String, Object> me = asMap(data.get("me"));
        if (me == null) return null;

        List<Map<String, Object>> userBooks = asList(me.get("user_books"));
        if (userBooks.isEmpty()) return null;

        Map<String, Object> userBook = userBooks.getFirst();
        Integer userBookId = asInt(userBook.get("id"));
        Integer editionId = null;
        Integer pages = null;

        Map<String, Object> edition = asMap(userBook.get("edition"));
        if (edition != null) {
            editionId = asInt(edition.get("id"));
            pages = asInt(edition.get("pages"));
        }

        return new HardcoverUserBookInfo(userBookId, editionId, pages);
    }

    public Integer addJournalEntry(String apiToken, String bookId, Integer editionId,
                                   Integer privacySettingId, String highlightedText,
                                   String noteText, Double progressPercent, Integer totalPages) {
        String entryText = formatEntryText(highlightedText, noteText);
        if (entryText == null || entryText.isBlank()) {
            return null;
        }

        String mutation = """
                mutation ($bookId: Int!, $entry: String!, $event: String!, $privacySettingId: Int!, $editionId: Int, $actionAt: date, $tags: [BasicTag]!, $metadata: jsonb) {
                  insert_reading_journal(object: {
                    book_id: $bookId,
                    entry: $entry,
                    event: $event,
                    privacy_setting_id: $privacySettingId,
                    edition_id: $editionId,
                    action_at: $actionAt,
                    tags: $tags,
                    metadata: $metadata
                  }) {
                    errors
                    id
                    reading_journal {
                      id
                      entry
                    }
                  }
                }
                """;

        int bookIdInt;
        try {
            bookIdInt = Integer.parseInt(bookId);
        } catch (NumberFormatException e) {
            log.warn("Invalid book ID for journal entry: {}", bookId);
            return null;
        }

        // Determine event type: "note" if noteText present, "quote" if only highlight
        String event = (noteText != null && !noteText.isBlank()) ? "note" : "quote";

        // Build progress metadata if available
        Map<String, Object> metadata = null;
        if (progressPercent != null && totalPages != null && totalPages > 0) {
            int currentPage = (int) Math.round((progressPercent / 100.0) * totalPages);
            currentPage = Math.max(0, Math.min(totalPages, currentPage));
            double roundedPercent = Math.round(progressPercent * 100.0) / 100.0;
            metadata = Map.of("position", Map.of(
                    "type", "pages",
                    "value", currentPage,
                    "percent", roundedPercent,
                    "possible", totalPages
            ));
        }

        Map<String, Object> variables = new HashMap<>();
        variables.put("bookId", bookIdInt);
        variables.put("entry", entryText);
        variables.put("event", event);
        variables.put("privacySettingId", privacySettingId != null ? privacySettingId : 1);
        variables.put("editionId", editionId);
        variables.put("actionAt", LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE));
        variables.put("tags", TAGS);
        variables.put("metadata", metadata);

        GraphQLRequest request = new GraphQLRequest();
        request.setQuery(mutation);
        request.setVariables(variables);

        Map<String, Object> response = executeGraphQL(request, apiToken);
        if (response == null) return null;

        Map<String, Object> data = asMap(response.get("data"));
        if (data == null) return null;
        Map<String, Object> insertResult = asMap(data.get("insert_reading_journal"));
        if (insertResult == null) return null;

        Object errors = insertResult.get("errors");
        if (errors != null) {
            log.warn("Hardcover journal entry creation errors: {}", errors);
            return null;
        }

        Map<String, Object> journalEntry = asMap(insertResult.get("reading_journal"));
        if (journalEntry == null) return null;
        return asInt(journalEntry.get("id"));
    }

    public boolean updateJournalEntry(String apiToken, Integer journalId,
                                      String highlightedText, String noteText) {
        String entryText = formatEntryText(highlightedText, noteText);
        if (entryText == null || entryText.isBlank()) {
            return false;
        }

        String mutation = """
                mutation ($journalId: Int!, $entry: String!, $event: String!) {
                  update_reading_journal(id: $journalId, object: {
                    entry: $entry,
                    event: $event
                  }) {
                    errors
                    id
                  }
                }
                """;

        // Determine event type: "note" if noteText present, "quote" if only highlight
        String event = (noteText != null && !noteText.isBlank()) ? "note" : "quote";

        GraphQLRequest request = new GraphQLRequest();
        request.setQuery(mutation);
        request.setVariables(Map.of("journalId", journalId, "entry", entryText, "event", event));

        Map<String, Object> response = executeGraphQL(request, apiToken);
        if (response == null) return false;

        Map<String, Object> data = asMap(response.get("data"));
        if (data == null) return false;
        Map<String, Object> updateResult = asMap(data.get("update_reading_journal"));
        if (updateResult == null) return false;

        Object errors = updateResult.get("errors");
        if (errors != null) {
            log.warn("Hardcover journal entry update errors: {}", errors);
            return false;
        }
        return true;
    }

    public Integer deleteJournalEntry(String apiToken, Integer journalId) {
        String mutation = """
                mutation ($journalId: Int!) {
                  delete_reading_journal(id: $journalId) {
                    id
                  }
                }
                """;

        GraphQLRequest request = new GraphQLRequest();
        request.setQuery(mutation);
        request.setVariables(Map.of("journalId", journalId));

        Map<String, Object> response = executeGraphQL(request, apiToken);
        if (response == null) return null;

        Map<String, Object> data = asMap(response.get("data"));
        if (data == null) return null;
        Map<String, Object> deleteResult = asMap(data.get("delete_reading_journal"));
        if (deleteResult == null) return null;

        return asInt(deleteResult.get("id"));
    }

    // === Book Resolution ===

    /**
     * Resolve a book's Hardcover identity from its metadata.
     * Checks the stored hardcoverBookId first; falls back to ISBN search.
     *
     * @param metadata the book metadata entity
     * @param apiToken the user's Hardcover API token
     * @param persistIfFound if true and the book ID was resolved via ISBN search,
     *                       persist it to the metadata entity so future syncs skip the search
     * @return HardcoverBookInfo with bookId (always set), editionId and pages (may be null), or null if unresolvable
     */
    public HardcoverBookInfo resolveHardcoverBook(BookMetadataEntity metadata, String apiToken, boolean persistIfFound) {
        if (metadata == null) {
            return null;
        }

        if (metadata.getHardcoverBookId() != null) {
            String storedId = metadata.getHardcoverBookId();
            log.debug("Using stored Hardcover book ID: {}", storedId);
            HardcoverBookInfo fetched = findHardcoverBookById(Integer.parseInt(storedId), apiToken);
            if (fetched != null) {
                return fetched;
            }
            log.warn("Could not fetch edition info from Hardcover for book ID: {}", storedId);
            return new HardcoverBookInfo(storedId, null, null);
        }

        HardcoverBookInfo found = findHardcoverBook(metadata, apiToken);
        if (found != null && persistIfFound) {
            persistHardcoverBookId(metadata, found.bookId());
        }
        return found;
    }

    /**
     * Persist a discovered Hardcover book ID to the metadata entity, respecting the lock flag.
     */
    private void persistHardcoverBookId(BookMetadataEntity metadata, String hardcoverBookId) {
        if (Boolean.TRUE.equals(metadata.getHardcoverBookIdLocked())) {
            return;
        }
        try {
            metadata.setHardcoverBookId(hardcoverBookId);
            bookMetadataRepository.save(metadata);
            log.info("Persisted Hardcover book ID {} for book {}", hardcoverBookId, metadata.getBookId());
        } catch (Exception e) {
            log.warn("Failed to persist Hardcover book ID: {}", e.getMessage());
        }
    }

    // === Private Helpers ===

    /**
     * Check if Hardcover sync is enabled for a specific user.
     */
    private boolean isHardcoverSyncEnabledForUser(HardcoverSyncSettings userSettings) {
        if (userSettings == null) {
            return false;
        }

        return userSettings.isHardcoverSyncEnabled()
                && userSettings.getHardcoverApiKey() != null
                && !userSettings.getHardcoverApiKey().isBlank();
    }

    /**
     * Find a book on Hardcover by ISBN.
     * Returns the numeric book_id, edition_id, and page count.
     */
    private HardcoverBookInfo findHardcoverBook(BookMetadataEntity metadata, String apiToken) {
        // Try ISBN first
        String isbn = metadata.getIsbn13();
        if (isbn == null || isbn.isBlank()) {
            isbn = metadata.getIsbn10();
        }

        if (isbn == null || isbn.isBlank()) {
            log.debug("No ISBN available for Hardcover lookup");
            return null;
        }

        try {
            String searchQuery = """
                query SearchBooks($query: String!) {
                  search(query: $query, query_type: "Book", per_page: 1, page: 1) {
                    results
                  }
                }
                """;

            GraphQLRequest request = new GraphQLRequest();
            request.setQuery(searchQuery);
            request.setVariables(Map.of("query", isbn));

            Map<String, Object> response = executeGraphQL(request, apiToken);
            log.debug("Hardcover search response for ISBN {}: {}", isbn, response);
            if (response == null) {
                return null;
            }

            // Navigate the response to get book info
            Map<String, Object> data = asMap(response.get("data"));
            if (data == null) return null;

            Map<String, Object> search = asMap(data.get("search"));
            if (search == null) return null;

            Map<String, Object> results = asMap(search.get("results"));
            if (results == null) return null;

            List<Map<String, Object>> hits = asList(results.get("hits"));
            if (hits.isEmpty()) return null;

            Map<String, Object> document = asMap(hits.getFirst().get("document"));
            if (document == null) return null;

            // Extract book ID
            String bookId = null;
            Object idObj = document.get("id");
            if (idObj instanceof String s) {
                bookId = s;
            } else if (idObj instanceof Number n) {
                bookId = String.valueOf(n.intValue());
            }
            if (bookId == null) return null;

            // Get page count
            Integer pages = asInt(document.get("pages"));

            // Try to get default_physical_edition_id from the search results
            Integer editionId = null;
            Object defaultPhysicalEditionObj = document.get("default_physical_edition_id");
            if (defaultPhysicalEditionObj instanceof Number n) {
                editionId = n.intValue();
            } else if (defaultPhysicalEditionObj instanceof String s) {
                try {
                    editionId = Integer.parseInt(s);
                } catch (NumberFormatException e) {
                    // Ignore
                }
            }

            // If no default physical edition found, try to look up edition by ISBN as fallback
            if (editionId == null) {
                EditionInfo edition = findEditionByIsbn(bookId, isbn, apiToken);
                if (edition != null) {
                    editionId = edition.id;
                }
            }

            // Fetch page count from the edition (prioritizing edition page count over book-level page count)
            if (editionId != null) {
                EditionInfo edition = findEditionById(editionId, apiToken);
                if (edition != null && edition.pages != null && edition.pages > 0) {
                    pages = edition.pages;
                    log.debug("Using page count from edition {}: {} pages", editionId, pages);
                }
            }

            log.info("Found Hardcover book: bookId={}, editionId={}, pages={}", bookId, editionId, pages);
            return new HardcoverBookInfo(bookId, editionId, pages);

        } catch (Exception e) {
            log.warn("Failed to search Hardcover by ISBN {}: {}", isbn, e.getMessage());
            return null;
        }
    }

    /**
     * Find an edition by ISBN for a given book.
     * This queries Hardcover's editions table to match by ISBN.
     */
    private EditionInfo findEditionByIsbn(String bookId, String isbn, String apiToken) {
        String query = """
            query FindEditionByIsbn($bookId: Int!, $isbn: String!) {
              editions(where: {
                book_id: {_eq: $bookId},
                _or: [
                  {isbn_10: {_eq: $isbn}},
                  {isbn_13: {_eq: $isbn}}
                ]
              }, limit: 1) {
                id
                pages
              }
            }
            """;

        GraphQLRequest request = new GraphQLRequest();
        request.setQuery(query);
        request.setVariables(Map.of("bookId", Integer.parseInt(bookId), "isbn", isbn));

        try {
            Map<String, Object> response = executeGraphQL(request, apiToken);
            log.debug("Edition lookup response: {}", response);
            if (response == null) return null;

            Map<String, Object> data = asMap(response.get("data"));
            if (data == null) return null;

            List<Map<String, Object>> editions = asList(data.get("editions"));
            if (editions.isEmpty()) return null;

            Map<String, Object> edition = editions.getFirst();
            EditionInfo info = new EditionInfo();
            info.id = asInt(edition.get("id"));
            info.pages = asInt(edition.get("pages"));

            return info.id != null ? info : null;

        } catch (Exception e) {
            log.debug("Failed to find edition by ISBN: {}", e.getMessage());
            return null;
        }
    }

    private EditionInfo findEditionById(Integer editionId, String apiToken) {
        String query = """
            query FindEditionById($editionId: Int!) {
              editions(where: {id: {_eq: $editionId}}, limit: 1) {
                id
                pages
              }
            }
            """;

        GraphQLRequest request = new GraphQLRequest();
        request.setQuery(query);
        request.setVariables(Map.of("editionId", editionId));

        try {
            Map<String, Object> response = executeGraphQL(request, apiToken);
            if (response == null) return null;

            Map<String, Object> data = asMap(response.get("data"));
            if (data == null) return null;

            List<Map<String, Object>> editions = asList(data.get("editions"));
            if (editions.isEmpty()) return null;

            Map<String, Object> edition = editions.getFirst();
            EditionInfo info = new EditionInfo();
            info.id = asInt(edition.get("id"));
            info.pages = asInt(edition.get("pages"));

            return info.id != null ? info : null;

        } catch (Exception e) {
            log.debug("Failed to find edition by ID: {}", e.getMessage());
            return null;
        }
    }

    private HardcoverBookInfo findHardcoverBookById(Integer bookId, String apiToken) {
        String query = """
            query FindBookById($bookId: Int!) {
              books(where: {id: {_eq: $bookId}}, limit: 1) {
                id
                default_physical_edition_id
                pages
              }
            }
            """;

        GraphQLRequest request = new GraphQLRequest();
        request.setQuery(query);
        request.setVariables(Map.of("bookId", bookId));

        try {
            Map<String, Object> response = executeGraphQL(request, apiToken);
            if (response == null) return null;

            Map<String, Object> data = asMap(response.get("data"));
            if (data == null) return null;

            List<Map<String, Object>> books = asList(data.get("books"));
            if (books.isEmpty()) return null;

            Map<String, Object> book = books.getFirst();

            Integer editionId = null;
            Object defaultPhysicalEditionObj = book.get("default_physical_edition_id");
            if (defaultPhysicalEditionObj instanceof Number n) {
                editionId = n.intValue();
            } else if (defaultPhysicalEditionObj instanceof String s) {
                try {
                    editionId = Integer.parseInt(s);
                } catch (NumberFormatException e) {
                    // Ignore
                }
            }

            // Get pages from the book level first
            Integer pages = asInt(book.get("pages"));

            // If we have an edition ID, fetch the page count from that edition
            if (editionId != null) {
                EditionInfo edition = findEditionById(editionId, apiToken);
                if (edition != null && edition.pages != null && edition.pages > 0) {
                    pages = edition.pages;
                    log.debug("Using page count from default physical edition {}: {} pages", editionId, pages);
                }
            }

            if (editionId == null && pages == null) return null;
            return new HardcoverBookInfo(String.valueOf(bookId), editionId, pages);

        } catch (Exception e) {
            log.debug("Failed to find Hardcover book by ID {}: {}", bookId, e.getMessage());
            return null;
        }
    }

    /**
     * Insert a book into the user's library or get existing user_book_id.
     */
    private Integer insertOrGetUserBook(Integer bookId, Integer editionId, int statusId, String apiToken) {
        String mutation = """
            mutation InsertUserBook($object: UserBookCreateInput!) {
              insert_user_book(object: $object) {
                user_book {
                  id
                }
                error
              }
            }
            """;

        Map<String, Object> bookInput = new HashMap<>();
        bookInput.put("book_id", bookId);
        bookInput.put("status_id", statusId);
        bookInput.put("date_added", LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE));
        if (editionId != null) {
            bookInput.put("edition_id", editionId);
        }

        GraphQLRequest request = new GraphQLRequest();
        request.setQuery(mutation);
        request.setVariables(Map.of("object", bookInput));

        try {
            Map<String, Object> response = executeGraphQL(request, apiToken);
            log.debug("insert_user_book response: {}", response);
            if (response == null) return null;

            Map<String, Object> data = asMap(response.get("data"));
            if (data == null) return null;

            Map<String, Object> insertResult = asMap(data.get("insert_user_book"));
            if (insertResult == null) return null;

            // Check for error (might mean book already exists)
            String error = (String) insertResult.get("error");
            if (error != null && !error.isBlank()) {
                log.debug("insert_user_book returned error: {} - book may already exist, trying to find it", error);
                return findExistingUserBook(bookId, apiToken);
            }

            Map<String, Object> userBook = asMap(insertResult.get("user_book"));
            if (userBook == null) return null;

            return asInt(userBook.get("id"));

        } catch (RestClientException e) {
            log.warn("Failed to insert user_book: {}", e.getMessage());
            // Try to find existing
            return findExistingUserBook(bookId, apiToken);
        }
    }

    /**
     * Find an existing user_book entry for a book.
     */
    private Integer findExistingUserBook(Integer bookId, String apiToken) {
        HardcoverUserBookInfo info = getUserBook(apiToken, String.valueOf(bookId));
        return info != null ? info.userBookId() : null;
    }

    /**
     * Create or update reading progress for a user_book.
     */
    private boolean upsertReadingProgress(Integer userBookId, Integer editionId, int progressPages, boolean isFinished, String apiToken) {
        log.info("upsertReadingProgress: userBookId={}, editionId={}, progressPages={}, isFinished={}",
                userBookId, editionId, progressPages, isFinished);

        // First, try to find existing user_book_read
        Integer existingReadId = findExistingUserBookRead(userBookId, apiToken);

        if (existingReadId != null) {
            // Update existing
            log.info("Updating existing user_book_read: id={}", existingReadId);
            return updateUserBookRead(existingReadId, editionId, progressPages, isFinished, apiToken);
        } else {
            // Create new
            log.info("Creating new user_book_read for userBookId={}", userBookId);
            return insertUserBookRead(userBookId, editionId, progressPages, isFinished, apiToken);
        }
    }

    private Integer findExistingUserBookRead(Integer userBookId, String apiToken) {
        String query = """
            query FindUserBookRead($userBookId: Int!) {
              user_book_reads(where: {user_book_id: {_eq: $userBookId}}, limit: 1) {
                id
              }
            }
            """;

        GraphQLRequest request = new GraphQLRequest();
        request.setQuery(query);
        request.setVariables(Map.of("userBookId", userBookId));

        try {
            Map<String, Object> response = executeGraphQL(request, apiToken);
            if (response == null) return null;

            Map<String, Object> data = asMap(response.get("data"));
            if (data == null) return null;

            List<Map<String, Object>> reads = asList(data.get("user_book_reads"));
            if (reads.isEmpty()) return null;

            return asInt(reads.getFirst().get("id"));

        } catch (RestClientException e) {
            log.warn("Failed to find existing user_book_read: {}", e.getMessage());
            return null;
        }
    }

    private boolean insertUserBookRead(Integer userBookId, Integer editionId, int progressPages, boolean isFinished, String apiToken) {
        String mutation = """
            mutation InsertUserBookRead($userBookId: Int!, $object: DatesReadInput!) {
              insert_user_book_read(user_book_id: $userBookId, user_book_read: $object) {
                user_book_read {
                  id
                }
                error
              }
            }
            """;

        Map<String, Object> readInput = new HashMap<>();
        readInput.put("started_at", LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE));
        readInput.put("progress_pages", progressPages);
        if (isFinished) {
            readInput.put("finished_at", LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE));
        }
        if (editionId != null) {
            readInput.put("edition_id", editionId);
        }

        GraphQLRequest request = new GraphQLRequest();
        request.setQuery(mutation);
        request.setVariables(Map.of(
            "userBookId", userBookId,
            "object", readInput
        ));

        try {
            Map<String, Object> response = executeGraphQL(request, apiToken);
            log.info("insert_user_book_read response: {}", response);
            if (response == null) return false;

            if (response.containsKey("errors")) {
                log.warn("insert_user_book_read returned errors: {}", response.get("errors"));
                return false;
            }

            return true;

        } catch (RestClientException e) {
            log.error("Failed to insert user_book_read: {}", e.getMessage());
            return false;
        }
    }

    private boolean updateUserBookRead(Integer readId, Integer editionId, int progressPages, boolean isFinished, String apiToken) {
        String mutation = """
            mutation UpdateUserBookRead($id: Int!, $object: DatesReadInput!) {
              update_user_book_read(id: $id, object: $object) {
                user_book_read {
                  id
                  progress
                }
                error
              }
            }
            """;

        Map<String, Object> readInput = new HashMap<>();
        readInput.put("progress_pages", progressPages);
        if (isFinished) {
            readInput.put("finished_at", LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE));
        }
        if (editionId != null) {
            readInput.put("edition_id", editionId);
        }

        GraphQLRequest request = new GraphQLRequest();
        request.setQuery(mutation);
        request.setVariables(Map.of(
            "id", readId,
            "object", readInput
        ));

        try {
            Map<String, Object> response = executeGraphQL(request, apiToken);
            log.debug("update_user_book_read response: {}", response);
            if (response == null) return false;

            if (response.containsKey("errors")) {
                log.warn("update_user_book_read returned errors: {}", response.get("errors"));
                return false;
            }

            return true;

        } catch (RestClientException e) {
            log.error("Failed to update user_book_read: {}", e.getMessage());
            return false;
        }
    }

    private String formatEntryText(String highlightedText, String noteText) {
        String escapedHighlight = (highlightedText != null && !highlightedText.isBlank())
                ? MarkdownEscaper.escape(highlightedText.trim())
                : null;
        String escapedNote = (noteText != null && !noteText.isBlank())
                ? MarkdownEscaper.escape(noteText.trim())
                : null;

        if (escapedHighlight != null && escapedNote != null) {
            return toBlockquote(escapedHighlight) + "\n\n -- " + escapedNote;
        } else if (escapedHighlight != null) {
            return toBlockquote(escapedHighlight);
        } else if (escapedNote != null) {
            return escapedNote;
        }
        return null;
    }

    private String toBlockquote(String text) {
        return text.lines()
                .map(line -> "> " + line)
                .collect(Collectors.joining("\n"));
    }

    // === GraphQL & Response Parsing ===

    @SuppressWarnings("unchecked")
    private Map<String, Object> executeGraphQL(GraphQLRequest request, String apiToken) {
        try {
            Map<String, Object> response = restClient.post()
                    .uri("")
                    .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiToken)
                    .body(request)
                    .retrieve()
                    .body(Map.class);
            if (response != null && response.containsKey("errors")) {
                log.warn("Hardcover GraphQL errors: {}", response.get("errors"));
            }
            return response;
        } catch (RestClientException e) {
            log.error("GraphQL request failed: {}", e.getMessage());
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> asMap(Object obj) {
        if (obj instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> asList(Object obj) {
        if (obj instanceof List<?> list) {
            return (List<Map<String, Object>>) list;
        }
        return List.of();
    }

    private Integer asInt(Object obj) {
        if (obj instanceof Number n) {
            return n.intValue();
        }
        return null;
    }

    // === Inner Types ===

    public record HardcoverUserBookInfo(Integer userBookId, Integer editionId, Integer pages) {}

    public record HardcoverBookInfo(String bookId, Integer editionId, Integer pages) {}

    /**
     * Helper class to hold edition information.
     */
    private static class EditionInfo {
        Integer id;
        Integer pages;
    }
}
