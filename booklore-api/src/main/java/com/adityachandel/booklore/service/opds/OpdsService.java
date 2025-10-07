package com.adityachandel.booklore.service.opds;

import com.adityachandel.booklore.config.security.service.AuthenticationService;
import com.adityachandel.booklore.config.security.userdetails.OpdsUserDetails;
import com.adityachandel.booklore.mapper.custom.BookLoreUserTransformer;
import com.adityachandel.booklore.model.dto.*;
import com.adityachandel.booklore.model.entity.BookLoreUserEntity;
import com.adityachandel.booklore.repository.UserRepository;
import com.adityachandel.booklore.repository.ShelfRepository;
import com.adityachandel.booklore.service.library.LibraryService;
import com.adityachandel.booklore.service.BookQueryService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

@Slf4j
@Service
@RequiredArgsConstructor
public class OpdsService {

    private final BookQueryService bookQueryService;
    private final AuthenticationService authenticationService;
    private final UserRepository userRepository;
    private final BookLoreUserTransformer bookLoreUserTransformer;
    private final ShelfRepository shelfRepository;
    private final LibraryService libraryService;
    

    public String generateCatalogFeed(HttpServletRequest request) {
        Long libraryId = parseLongParam(request, "libraryId", null);
        Long shelfId = parseLongParam(request, "shelfId", null);
        String forceV2 = (libraryId != null || shelfId != null) ? "2.0" : null;
        String feedVersion = forceV2 != null ? forceV2 : extractVersionFromAcceptHeader(request);
        return switch (feedVersion) {
            case "2.0" -> {
                int page = parseIntParam(request, "page", 1);
                int size = parseIntParam(request, "size", 50);
                var result = getAllowedBooksPage(null, libraryId, shelfId, page, size);
                var qp = new java.util.LinkedHashMap<String,String>();
                if (libraryId != null) qp.put("libraryId", String.valueOf(libraryId));
                if (shelfId != null) qp.put("shelfId", String.valueOf(shelfId));
                yield generateOpdsV2Feed(result.getContent(), result.getTotalElements(), "/api/v2/opds/catalog", qp, page, size);
            }
            default -> {
                List<Book> books = getAllowedBooks(null);
                yield generateOpdsV1Feed(books, request);
            }
        };
    }

    public String generateSearchResults(HttpServletRequest request, String queryParam) {
        Long libraryId = parseLongParam(request, "libraryId", null);
        Long shelfId = parseLongParam(request, "shelfId", null);
        String forceV2 = (libraryId != null || shelfId != null) ? "2.0" : null;
        String feedVersion = forceV2 != null ? forceV2 : extractVersionFromAcceptHeader(request);
        return switch (feedVersion) {
            case "2.0" -> {
                int page = parseIntParam(request, "page", 1);
                int size = parseIntParam(request, "size", 50);
                String q = request.getParameter("q");
                var result = getAllowedBooksPage(q, libraryId, shelfId, page, size);
                var qp = new java.util.LinkedHashMap<String,String>();
                if (q != null && !q.isBlank()) qp.put("q", q);
                if (libraryId != null) qp.put("libraryId", String.valueOf(libraryId));
                if (shelfId != null) qp.put("shelfId", String.valueOf(shelfId));
                yield generateOpdsV2Feed(result.getContent(), result.getTotalElements(), "/api/v2/opds/search", qp, page, size);
            }
            default -> {
                List<Book> books = getAllowedBooks(queryParam);
                yield generateOpdsV1Feed(books, request);
            }
        };
    }

    public String generateRecentFeed(HttpServletRequest request) {
        int page = parseIntParam(request, "page", 1);
        int size = parseIntParam(request, "size", 50);

        // Determine context: legacy OPDS user vs OPDS v2 user
        OpdsUserDetails details = authenticationService.getOpdsUser();
        OpdsUser opdsUser = details.getOpdsUser();

        var qp = new java.util.LinkedHashMap<String, String>();
        qp.put("page", String.valueOf(page));
        qp.put("size", String.valueOf(size));

        if (opdsUser != null) {
            var result = bookQueryService.getRecentBooksPage(true, page, size);
            return generateOpdsV2Feed(result.getContent(), result.getTotalElements(), "/api/v2/opds/recent", qp, page, size);
        }

        OpdsUserV2 v2 = details.getOpdsUserV2();
        BookLoreUserEntity entity = userRepository.findById(v2.getUserId())
                .orElseThrow(() -> new org.springframework.security.access.AccessDeniedException("User not found"));
        BookLoreUser user = bookLoreUserTransformer.toDTO(entity);
        boolean isAdmin = user.getPermissions().isAdmin();
        if (isAdmin) {
            var result = bookQueryService.getRecentBooksPage(true, page, size);
            return generateOpdsV2Feed(result.getContent(), result.getTotalElements(), "/api/v2/opds/recent", qp, page, size);
        }

        java.util.Set<Long> libraryIds = user.getAssignedLibraries().stream()
                .map(Library::getId)
                .collect(java.util.stream.Collectors.toSet());
        var result = bookQueryService.getRecentBooksByLibraryIdsPage(libraryIds, true, page, size, v2.getUserId());
        return generateOpdsV2Feed(result.getContent(), result.getTotalElements(), "/api/v2/opds/recent", qp, page, size);
    }

    public String generateOpdsV2Navigation(HttpServletRequest request) {
        try {
            ObjectMapper mapper = new ObjectMapper();
            mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

            String rootPath = "/api/v2/opds";
            var root = new java.util.LinkedHashMap<String, Object>();

            // metadata
            var meta = new java.util.LinkedHashMap<String, Object>();
            meta.put("title", "Booklore");
            root.put("metadata", meta);

            // links: self, start, search
            var links = new java.util.ArrayList<java.util.Map<String, Object>>();
            links.add(java.util.Map.of(
                    "rel", "self",
                    "href", rootPath,
                    "type", "application/opds+json;profile=navigation"
            ));
            links.add(java.util.Map.of(
                    "rel", "start",
                    "href", rootPath,
                    "type", "application/opds+json;profile=navigation"
            ));
            links.add(java.util.Map.of(
                    "rel", "search",
                    "href", rootPath + "/search.opds",
                    "type", "application/opensearchdescription+xml"
            ));
            root.put("links", links);

            // navigation items
            var navigation = new java.util.ArrayList<java.util.Map<String, Object>>();
            navigation.add(new java.util.LinkedHashMap<>(java.util.Map.of(
                    "title", "All Books",
                    "href", rootPath + "/catalog",
                    "type", "application/opds+json;profile=acquisition"
            )));
            navigation.add(new java.util.LinkedHashMap<>(java.util.Map.of(
                    "title", "Recently Added",
                    "href", rootPath + "/recent",
                    "type", "application/opds+json;profile=acquisition"
            )));
            navigation.add(new java.util.LinkedHashMap<>(java.util.Map.of(
                    "title", "Libraries",
                    "href", rootPath + "/libraries",
                    "type", "application/opds+json;profile=navigation"
            )));
            navigation.add(new java.util.LinkedHashMap<>(java.util.Map.of(
                    "title", "Shelves",
                    "href", rootPath + "/shelves",
                    "type", "application/opds+json;profile=navigation"
            )));
            

            // Enrich with libraries and shelves for OPDS v2 users
            OpdsUserDetails details = authenticationService.getOpdsUser();
            if (details != null && details.getOpdsUserV2() != null) {
                Long userId = details.getOpdsUserV2().getUserId();
                BookLoreUserEntity entity = userRepository.findById(userId)
                        .orElseThrow(() -> new org.springframework.security.access.AccessDeniedException("User not found"));
                BookLoreUser user = bookLoreUserTransformer.toDTO(entity);

                java.util.List<Library> libraries;
                try {
                    libraries = libraryService.getLibraries();
                } catch (Exception ex) {
                    libraries = user.getAssignedLibraries();
                }
                // Keep root clean: only references to collections; no inline lists
                if (libraries != null) {
                    // optionally keep this empty or add a count in future
                }
            }
            root.put("navigation", navigation);

            return mapper.writeValueAsString(root);
        } catch (Exception e) {
            log.error("Failed generating OPDS v2 navigation collection", e);
            throw new RuntimeException("Failed generating OPDS v2 navigation collection", e);
        }
    }

    public String generateOpdsV2LibrariesNavigation(HttpServletRequest request) {
        try {
            ObjectMapper mapper = new ObjectMapper();
            mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

            String rootPath = "/api/v2/opds";
            String selfPath = rootPath + "/libraries";
            var root = new java.util.LinkedHashMap<String, Object>();

            // metadata
            var meta = new java.util.LinkedHashMap<String, Object>();
            meta.put("title", "Libraries");
            root.put("metadata", meta);

            // links
            var links = new java.util.ArrayList<java.util.Map<String, Object>>();
            links.add(java.util.Map.of(
                    "rel", "self",
                    "href", selfPath,
                    "type", "application/opds+json;profile=navigation"
            ));
            links.add(java.util.Map.of(
                    "rel", "start",
                    "href", rootPath,
                    "type", "application/opds+json;profile=navigation"
            ));
            links.add(java.util.Map.of(
                    "rel", "search",
                    "href", rootPath + "/search.opds",
                    "type", "application/opensearchdescription+xml"
            ));
            root.put("links", links);

            // navigation list of libraries
            var navigation = new java.util.ArrayList<java.util.Map<String, Object>>();

            OpdsUserDetails details = authenticationService.getOpdsUser();
            if (details != null && details.getOpdsUserV2() != null) {
                Long userId = details.getOpdsUserV2().getUserId();
                BookLoreUserEntity entity = userRepository.findById(userId)
                        .orElseThrow(() -> new AccessDeniedException("User not found"));
                BookLoreUser user = bookLoreUserTransformer.toDTO(entity);

                java.util.List<Library> libraries = (user.getPermissions() != null && user.getPermissions().isAdmin())
                        ? libraryService.getAllLibraries()
                        : user.getAssignedLibraries();
                if (libraries != null) {
                    for (Library lib : libraries) {
                        navigation.add(new java.util.LinkedHashMap<>(java.util.Map.of(
                                "title", lib.getName(),
                                "href", buildHref(rootPath + "/catalog", java.util.Map.of("libraryId", String.valueOf(lib.getId()))),
                                "type", "application/opds+json;profile=acquisition"
                        )));
                    }
                }
            }

            root.put("navigation", navigation);
            return mapper.writeValueAsString(root);
        } catch (Exception e) {
            log.error("Failed generating OPDS v2 libraries navigation", e);
            throw new RuntimeException("Failed generating OPDS v2 libraries navigation", e);
        }
    }

    

    public String generateOpdsV2ShelvesNavigation(HttpServletRequest request) {
        try {
            ObjectMapper mapper = new ObjectMapper();
            mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

            String rootPath = "/api/v2/opds";
            String selfPath = rootPath + "/shelves";
            var root = new java.util.LinkedHashMap<String, Object>();

            var meta = new java.util.LinkedHashMap<String, Object>();
            meta.put("title", "Shelves");
            root.put("metadata", meta);

            var links = new java.util.ArrayList<java.util.Map<String, Object>>();
            links.add(java.util.Map.of("rel", "self", "href", selfPath, "type", "application/opds+json;profile=navigation"));
            links.add(java.util.Map.of("rel", "start", "href", rootPath, "type", "application/opds+json;profile=navigation"));
            links.add(java.util.Map.of("rel", "search", "href", rootPath + "/search.opds", "type", "application/opensearchdescription+xml"));
            root.put("links", links);

            var navigation = new java.util.ArrayList<java.util.Map<String, Object>>();
            OpdsUserDetails details = authenticationService.getOpdsUser();
            if (details != null && details.getOpdsUserV2() != null) {
                Long userId = details.getOpdsUserV2().getUserId();
                var shelves = shelfRepository.findByUserId(userId);
                if (shelves != null) {
                    for (var shelf : shelves) {
                        navigation.add(new java.util.LinkedHashMap<>(java.util.Map.of(
                                "title", shelf.getName(),
                                "href", buildHref(rootPath + "/catalog", java.util.Map.of("shelfId", String.valueOf(shelf.getId()))),
                                "type", "application/opds+json;profile=acquisition"
                        )));
                    }
                }
            }
            root.put("navigation", navigation);
            return mapper.writeValueAsString(root);
        } catch (Exception e) {
            log.error("Failed generating OPDS v2 shelves navigation", e);
            throw new RuntimeException("Failed generating OPDS v2 shelves navigation", e);
        }
    }

    private List<Book> getAllowedBooks(String queryParam) {
        OpdsUserDetails opdsUserDetails = authenticationService.getOpdsUser();
        OpdsUser opdsUser = opdsUserDetails.getOpdsUser();

        if (opdsUser != null) {
            return (queryParam != null)
                    ? bookQueryService.searchBooksByMetadata(queryParam)
                    : bookQueryService.getAllBooks(true);
        }

        OpdsUserV2 opdsUserV2 = opdsUserDetails.getOpdsUserV2();
        BookLoreUserEntity entity = userRepository.findById(opdsUserV2.getUserId())
                .orElseThrow(() -> new AccessDeniedException("User not found"));

        if (!entity.getPermissions().isPermissionAccessOpds() && !entity.getPermissions().isPermissionAdmin()) {
            throw new AccessDeniedException("You are not allowed to access this resource");
        }

        BookLoreUser user = bookLoreUserTransformer.toDTO(entity);
        boolean isAdmin = user.getPermissions().isAdmin();
        Set<Long> libraryIds = user.getAssignedLibraries().stream()
                .map(Library::getId)
                .collect(Collectors.toSet());

        if (isAdmin) {
            return (queryParam != null)
                    ? bookQueryService.searchBooksByMetadata(queryParam)
                    : bookQueryService.getAllBooks(true);
        } else {
            return (queryParam != null)
                    ? bookQueryService.searchBooksByMetadataInLibraries(queryParam, libraryIds)
                    : bookQueryService.getAllBooksByLibraryIds(libraryIds, true, opdsUserV2.getUserId());
        }
    }

    public String generateSearchDescription(HttpServletRequest request) {
        var feedVersion = extractVersionFromAcceptHeader(request);

        return switch (feedVersion) {
            case "2.0" -> generateOpdsV2SearchDescription();
            default -> generateOpdsV1SearchDescription();
        };
    }

    private String extractVersionFromAcceptHeader(HttpServletRequest request) {
        var acceptHeader = request.getHeader("Accept");
        if (acceptHeader == null) return "1.2";
        // Accept either explicit version or generic OPDS 2 media type
        if (acceptHeader.contains("version=2.0") || acceptHeader.contains("application/opds+json")) {
            return "2.0";
        }
        return "1.2";
    }

    private String generateOpdsV1SearchDescription() {
        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <OpenSearchDescription xmlns="http://a9.com/-/spec/opensearch/1.1/">
                    <LongName>Booklore catalog</LongName>
                    <Description>Search the Booklore ebook catalog.</Description>
                    <Url type="application/atom+xml;profile=opds-catalog" template="/api/v1/opds/search?q={searchTerms}"/>
                    <Language>en-us</Language>
                    <OutputEncoding>UTF-8</OutputEncoding>
                    <InputEncoding>UTF-8</InputEncoding>
                </OpenSearchDescription>
                """;
    }

    private String generateOpdsV1Feed(List<Book> books, HttpServletRequest request) {
        String version = extractVersionFromRequest(request); // v1 or v2
        var feed = new StringBuilder("""
                <?xml version="1.0" encoding="UTF-8"?>
                <feed xmlns="http://www.w3.org/2005/Atom" xmlns:dc="http://purl.org/dc/terms/">
                  <title>Booklore Catalog</title>
                  <id>urn:booklore:catalog</id>
                  <updated>%s</updated>
                  <link rel="search" type="application/opensearchdescription+xml" title="Booklore Search" href="/api/%s/opds/search.opds"/>
                """.formatted(now(), version));

        books.forEach(book -> appendBookEntryV1(feed, book, version));

        feed.append("</feed>");
        return feed.toString();
    }

    private void appendBookEntryV1(StringBuilder feed, Book book, String version) {
        feed.append("""
                <entry>
                  <title>%s</title>
                  <id>urn:booklore:book:%d</id>
                  <updated>%s</updated>
                """.formatted(escapeXml(book.getMetadata().getTitle()), book.getId(),
                book.getAddedOn() != null ? book.getAddedOn() : now()));

        book.getMetadata().getAuthors().forEach(author -> feed.append("""
                <author>
                  <name>%s</name>
                </author>
                """.formatted(escapeXml(author))));

        appendOptionalTags(feed, book, version);
        feed.append("  </entry>");
    }

    private void appendOptionalTags(StringBuilder feed, Book book, String version) {
        String basePath = "/api/" + version + "/opds/";

        if (book.getMetadata().getPublisher() != null) {
            feed.append("<dc:publisher>").append(escapeXml(book.getMetadata().getPublisher())).append("</dc:publisher>");
        }

        if (book.getMetadata().getLanguage() != null) {
            feed.append("<dc:language>").append(escapeXml(book.getMetadata().getLanguage())).append("</dc:language>");
        }

        if (book.getMetadata().getCategories() != null) {
            book.getMetadata().getCategories().forEach(category ->
                    feed.append("<dc:subject>").append(escapeXml(category)).append("</dc:subject>")
            );
        }

        if (book.getMetadata().getIsbn10() != null) {
            feed.append("<dc:identifier>").append(escapeXml("urn:isbn:" + book.getMetadata().getIsbn10())).append("</dc:identifier>");
        }

        if (book.getMetadata().getPublishedDate() != null) {
            feed.append("<published>").append(book.getMetadata().getPublishedDate()).append("</published>");
        }

        // download link
        feed.append("""
                <link href="%s%d/download" rel="http://opds-spec.org/acquisition" type="application/%s"/>
                """.formatted(basePath, book.getId(), fileMimeType(book)));

        // cover link
        if (book.getMetadata().getCoverUpdatedOn() != null) {
            String coverPath = basePath + book.getId() + "/cover?" + book.getMetadata().getCoverUpdatedOn();
            feed.append("""
                    <link rel="http://opds-spec.org/image" href="%s" type="image/jpeg"/>
                    <link rel="http://opds-spec.org/image/thumbnail" href="%s" type="image/jpeg"/>
                    """.formatted(escapeXml(coverPath), escapeXml(coverPath)));
        }

        if (book.getMetadata().getDescription() != null) {
            feed.append("<summary>").append(escapeXml(book.getMetadata().getDescription())).append("</summary>");
        }
    }

    private String generateOpdsV2Feed(List<Book> content, long total, String basePath, java.util.Map<String,String> queryParams, int page, int size) {
        try {
            ObjectMapper mapper = new ObjectMapper();
            mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

            // Root collection
            var root = new java.util.LinkedHashMap<String, Object>();

            // metadata with pagination
            var meta = new java.util.LinkedHashMap<String, Object>();
            meta.put("title", "Booklore Catalog");
            if (page < 1) page = 1;
            if (size < 1) size = 1;
            if (size > 200) size = 200;
            meta.put("itemsPerPage", size);
            meta.put("currentPage", page);
            meta.put("numberOfItems", total);
            root.put("metadata", meta);

            // links
            var links = new java.util.ArrayList<java.util.Map<String, Object>>();
            links.add(java.util.Map.of(
                    "rel", "self",
                    "href", buildHref(basePath, mergeQuery(queryParams, java.util.Map.of("page", String.valueOf(page), "size", String.valueOf(size)))),
                    "type", "application/opds+json;profile=acquisition"
            ));
            links.add(java.util.Map.of(
                    "rel", "start",
                    "href", "/api/v2/opds",
                    "type", "application/opds+json;profile=navigation"
            ));
            links.add(java.util.Map.of(
                    "rel", "search",
                    "href", "/api/v2/opds/search.opds",
                    "type", "application/opensearchdescription+xml"
            ));
            if ((page - 1) > 0) {
                links.add(java.util.Map.of(
                        "rel", "previous",
                        "href", buildHref(basePath, mergeQuery(queryParams, java.util.Map.of("page", String.valueOf(page - 1), "size", String.valueOf(size)))),
                        "type", "application/opds+json;profile=acquisition"
                ));
            }
            if ((long) page * size < total) {
                links.add(java.util.Map.of(
                        "rel", "next",
                        "href", buildHref(basePath, mergeQuery(queryParams, java.util.Map.of("page", String.valueOf(page + 1), "size", String.valueOf(size)))),
                        "type", "application/opds+json;profile=acquisition"
                ));
            }
            root.put("links", links);

            // publications
            var pubs = new java.util.ArrayList<java.util.Map<String, Object>>();
            for (Book book : content) {
                pubs.add(toPublicationMap(book));
            }
            root.put("publications", pubs);

            return mapper.writeValueAsString(root);
        } catch (Exception e) {
            log.error("Failed generating OPDS v2 feed", e);
            throw new RuntimeException("Failed generating OPDS v2 feed", e);
        }
    }

    public String generateOpdsV2Publication(HttpServletRequest request, long bookId) {
        List<Book> allowed = getAllowedBooks(null);
        Book target = allowed.stream().filter(b -> b.getId() != null && b.getId() == bookId).findFirst()
                .orElseThrow(() -> new AccessDeniedException("You are not allowed to access this resource"));
        try {
            ObjectMapper mapper = new ObjectMapper();
            mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
            return mapper.writeValueAsString(toPublicationMap(target));
        } catch (Exception e) {
            log.error("Failed generating OPDS v2 publication", e);
            throw new RuntimeException("Failed generating OPDS v2 publication", e);
        }
    }

    private java.util.Map<String, Object> toPublicationMap(Book book) {
        String base = "/api/v2/opds";
        var pub = new java.util.LinkedHashMap<String, Object>();
        var pm = new java.util.LinkedHashMap<String, Object>();
        String title = (book.getMetadata() != null ? book.getMetadata().getTitle() : book.getTitle());
        pm.put("title", title != null ? title : "Untitled");
        if (book.getMetadata() != null) {
            if (book.getMetadata().getLanguage() != null) {
                pm.put("language", book.getMetadata().getLanguage());
            }
            if (book.getMetadata().getIsbn13() != null) {
                pm.put("identifier", "urn:isbn:" + book.getMetadata().getIsbn13());
            } else if (book.getMetadata().getIsbn10() != null) {
                pm.put("identifier", "urn:isbn:" + book.getMetadata().getIsbn10());
            }
            if (book.getMetadata().getAuthors() != null && !book.getMetadata().getAuthors().isEmpty()) {
                var authors = book.getMetadata().getAuthors().stream()
                        .map(a -> java.util.Map.of("name", a))
                        .collect(java.util.stream.Collectors.toList());
                pm.put("author", authors);
            }
            if (book.getMetadata().getDescription() != null) {
                pm.put("description", book.getMetadata().getDescription());
            }
        }
        pub.put("metadata", pm);

        var plinks = new java.util.ArrayList<java.util.Map<String, Object>>();
        String type = "application/" + fileMimeType(book);
        plinks.add(new java.util.LinkedHashMap<>(java.util.Map.of(
                "rel", "http://opds-spec.org/acquisition/open-access",
                "href", base + "/" + book.getId() + "/download",
                "type", type
        )));
        plinks.add(new java.util.LinkedHashMap<>(java.util.Map.of(
                "rel", "self",
                "href", base + "/publications/" + book.getId(),
                "type", "application/opds-publication+json"
        )));
        pub.put("links", plinks);

        if (book.getMetadata() != null && book.getMetadata().getCoverUpdatedOn() != null) {
            var images = new java.util.ArrayList<java.util.Map<String, Object>>();
            String coverHref = base + "/" + book.getId() + "/cover?" + book.getMetadata().getCoverUpdatedOn();
            images.add(java.util.Map.of("href", coverHref, "type", "image/jpeg"));
            pub.put("images", images);
        }
        return pub;
    }

    private String generateOpdsV2SearchDescription() {
        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <OpenSearchDescription xmlns="http://a9.com/-/spec/opensearch/1.1/">
                    <LongName>Booklore catalog (OPDS 2)</LongName>
                    <Description>Search the Booklore ebook catalog.</Description>
                    <Url type="application/opds+json" template="/api/v2/opds/search{?q}"/>
                    <Language>en-us</Language>
                    <OutputEncoding>UTF-8</OutputEncoding>
                    <InputEncoding>UTF-8</InputEncoding>
                </OpenSearchDescription>
                """;
    }


    private String now() {
        return DateTimeFormatter.ISO_INSTANT.format(java.time.Instant.now());
    }

    private String fileMimeType(Book book) {
        if (book == null || book.getBookType() == null) {
            return "octet-stream";
        }
        return switch (book.getBookType()) {
            case PDF -> "pdf";
            case EPUB -> "epub+zip";
            default -> "octet-stream";
        };
    }

    private String escapeXml(String input) {
        return input == null ? "" :
                input.replace("&", "&amp;")
                        .replace("<", "&lt;")
                        .replace(">", "&gt;")
                        .replace("\"", "&quot;")
                        .replace("'", "&apos;");
    }

    private String extractVersionFromRequest(HttpServletRequest request) {
        return (request.getRequestURI() != null && request.getRequestURI().startsWith("/api/v2/opds")) ? "v2" : "v1";
    }

    private String urlEncode(String value) {
        try {
            return java.net.URLEncoder.encode(value, java.nio.charset.StandardCharsets.UTF_8);
        } catch (Exception e) {
            return value;
        }
    }

    private int parseIntParam(HttpServletRequest request, String name, int defaultValue) {
        try {
            String v = request.getParameter(name);
            if (v == null || v.isBlank()) return defaultValue;
            return Integer.parseInt(v);
        } catch (Exception e) {
            return defaultValue;
        }
    }

    private String buildHref(String basePath, java.util.Map<String, String> params) {
        if (params == null || params.isEmpty()) return basePath;
        String query = params.entrySet().stream()
                .map(e -> urlEncode(e.getKey()) + "=" + urlEncode(e.getValue()))
                .collect(java.util.stream.Collectors.joining("&"));
        return basePath + (query.isEmpty() ? "" : ("?" + query));
    }

    private java.util.Map<String, String> mergeQuery(java.util.Map<String, String> base, java.util.Map<String, String> extra) {
        var map = new java.util.LinkedHashMap<String, String>();
        if (base != null) map.putAll(base);
        if (extra != null) map.putAll(extra);
        return map;
    }

    private Long parseLongParam(HttpServletRequest request, String name, Long defaultValue) {
        try {
            String v = request.getParameter(name);
            if (v == null || v.isBlank()) return defaultValue;
            return Long.parseLong(v);
        } catch (Exception e) {
            return defaultValue;
        }
    }

    private org.springframework.data.domain.Page<Book> getAllowedBooksPage(String queryParam, Long libraryId, Long shelfId, int page, int size) {
        OpdsUserDetails opdsUserDetails = authenticationService.getOpdsUser();
        OpdsUser opdsUser = opdsUserDetails.getOpdsUser();

        if (opdsUser != null) {
            if (shelfId != null) {
                return bookQueryService.getAllBooksByShelfPage(shelfId, true, page, size);
            }
            if (libraryId != null) {
                return bookQueryService.getAllBooksByLibraryIdsPage(java.util.Set.of(libraryId), true, page, size, null);
            }
            if (queryParam != null && !queryParam.isBlank()) {
                return bookQueryService.searchBooksByMetadataPage(queryParam, page, size);
            }
            return bookQueryService.getAllBooksPage(true, page, size);
        }

        OpdsUserV2 opdsUserV2 = opdsUserDetails.getOpdsUserV2();
        BookLoreUserEntity entity = userRepository.findById(opdsUserV2.getUserId())
                .orElseThrow(() -> new AccessDeniedException("User not found"));

        if (!entity.getPermissions().isPermissionAccessOpds() && !entity.getPermissions().isPermissionAdmin()) {
            throw new AccessDeniedException("You are not allowed to access this resource");
        }

        BookLoreUser user = bookLoreUserTransformer.toDTO(entity);
        boolean isAdmin = user.getPermissions().isAdmin();
        java.util.Set<Long> libraryIds = user.getAssignedLibraries().stream()
                .map(Library::getId)
                .collect(java.util.stream.Collectors.toSet());

        if (shelfId != null) {
            var shelf = shelfRepository.findById(shelfId).orElseThrow(() -> new AccessDeniedException("Shelf not found"));
            if (!shelf.getUser().getId().equals(user.getId()) && !isAdmin) {
                throw new AccessDeniedException("You are not allowed to access this shelf");
            }
            return bookQueryService.getAllBooksByShelfPage(shelfId, true, page, size);
        }

        if (libraryId != null) {
            if (!isAdmin && !libraryIds.contains(libraryId)) {
                throw new AccessDeniedException("You are not allowed to access this library");
            }
            return (queryParam != null && !queryParam.isBlank())
                    ? bookQueryService.searchBooksByMetadataInLibrariesPage(queryParam, java.util.Set.of(libraryId), page, size)
                    : bookQueryService.getAllBooksByLibraryIdsPage(java.util.Set.of(libraryId), true, page, size, opdsUserV2.getUserId());
        }

        if (isAdmin) {
            return (queryParam != null && !queryParam.isBlank())
                    ? bookQueryService.searchBooksByMetadataPage(queryParam, page, size)
                    : bookQueryService.getAllBooksPage(true, page, size);
        }

        return (queryParam != null && !queryParam.isBlank())
                ? bookQueryService.searchBooksByMetadataInLibrariesPage(queryParam, libraryIds, page, size)
                : bookQueryService.getAllBooksByLibraryIdsPage(libraryIds, true, page, size, opdsUserV2.getUserId());
    }

}
