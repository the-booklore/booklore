package com.adityachandel.booklore.service.metadata.extractor;

import com.adityachandel.booklore.model.dto.BookMetadata;
import io.documentnode.epub4j.domain.Book;
import io.documentnode.epub4j.epub.EpubReader;
import lombok.extern.slf4j.Slf4j;
import net.lingala.zip4j.ZipFile;
import net.lingala.zip4j.model.FileHeader;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.boot.configurationprocessor.json.JSONArray;
import org.springframework.boot.configurationprocessor.json.JSONException;
import org.springframework.boot.configurationprocessor.json.JSONObject;
import org.springframework.stereotype.Component;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.function.BiConsumer;
import java.util.regex.Pattern;

@Slf4j
@Component
public class EpubMetadataExtractor implements FileMetadataExtractor {

    private static final String OPF_NS = "http://www.idpf.org/2007/opf";
    private static final Pattern YEAR_ONLY_PATTERN = Pattern.compile("^\\d{4}$");
    private static final Pattern ISBN_13_PATTERN = Pattern.compile("\\d{13}");
    private static final Pattern ISBN_10_PATTERN = Pattern.compile("\\d{10}|[0-9]{9}[xX]");

    private static class IdentifierMapping {
        final String prefix;
        final String fieldName;
        final BiConsumer<BookMetadata.BookMetadataBuilder, String> setter;

        IdentifierMapping(String prefix, String fieldName, BiConsumer<BookMetadata.BookMetadataBuilder, String> setter) {
            this.prefix = prefix;
            this.fieldName = fieldName;
            this.setter = setter;
        }
    }

    private static final List<IdentifierMapping> IDENTIFIER_PREFIX_MAPPINGS = List.of(
        new IdentifierMapping("urn:isbn:", "isbn", null), // Special handling for ISBN URNs
        new IdentifierMapping("urn:amazon:", "asin", BookMetadata.BookMetadataBuilder::asin),
        new IdentifierMapping("urn:goodreads:", "goodreadsId", BookMetadata.BookMetadataBuilder::goodreadsId),
        new IdentifierMapping("urn:google:", "googleId", BookMetadata.BookMetadataBuilder::googleId),
        new IdentifierMapping("urn:hardcover:", "hardcoverId", BookMetadata.BookMetadataBuilder::hardcoverId),
        new IdentifierMapping("urn:hardcoverbook:", "hardcoverBookId", BookMetadata.BookMetadataBuilder::hardcoverBookId),
        new IdentifierMapping("urn:comicvine:", "comicvineId", BookMetadata.BookMetadataBuilder::comicvineId),
        new IdentifierMapping("urn:lubimyczytac:", "lubimyczytacId", (builder, value) -> builder.lubimyczytacId(value)),
        new IdentifierMapping("urn:ranobedb:", "ranobedbId", BookMetadata.BookMetadataBuilder::ranobedbId),
        new IdentifierMapping("asin:", "asin", BookMetadata.BookMetadataBuilder::asin),
        new IdentifierMapping("amazon:", "asin", BookMetadata.BookMetadataBuilder::asin),
        new IdentifierMapping("mobi-asin:", "asin", BookMetadata.BookMetadataBuilder::asin),
        new IdentifierMapping("goodreads:", "goodreadsId", BookMetadata.BookMetadataBuilder::goodreadsId),
        new IdentifierMapping("google:", "googleId", BookMetadata.BookMetadataBuilder::googleId),
        new IdentifierMapping("hardcover:", "hardcoverId", BookMetadata.BookMetadataBuilder::hardcoverId),
        new IdentifierMapping("hardcoverbook:", "hardcoverBookId", BookMetadata.BookMetadataBuilder::hardcoverBookId),
        new IdentifierMapping("comicvine:", "comicvineId", BookMetadata.BookMetadataBuilder::comicvineId),
        new IdentifierMapping("lubimyczytac:", "lubimyczytacId", (builder, value) -> builder.lubimyczytacId(value)),
        new IdentifierMapping("ranobedb:", "ranobedbId", BookMetadata.BookMetadataBuilder::ranobedbId)
    );

    private static final Map<String, BiConsumer<BookMetadata.BookMetadataBuilder, String>> SCHEME_MAPPINGS = Map.of(
        "GOODREADS", BookMetadata.BookMetadataBuilder::goodreadsId,
        "COMICVINE", BookMetadata.BookMetadataBuilder::comicvineId,
        "GOOGLE", BookMetadata.BookMetadataBuilder::googleId,
        "AMAZON", BookMetadata.BookMetadataBuilder::asin,
        "HARDCOVER", BookMetadata.BookMetadataBuilder::hardcoverId
    );

    private static final Map<String, BiConsumer<BookMetadata.BookMetadataBuilder, String>> CALIBRE_FIELD_MAPPINGS = Map.ofEntries(
        Map.entry("#subtitle", BookMetadata.BookMetadataBuilder::subtitle),
        Map.entry("#pagecount", (builder, value) -> safeParseInt(value, builder::pageCount)),
        Map.entry("#series_total", (builder, value) -> safeParseInt(value, builder::seriesTotal)),
        Map.entry("#amazon_rating", (builder, value) -> safeParseDouble(value, builder::amazonRating)),
        Map.entry("#amazon_review_count", (builder, value) -> safeParseInt(value, builder::amazonReviewCount)),
        Map.entry("#goodreads_rating", (builder, value) -> safeParseDouble(value, builder::goodreadsRating)),
        Map.entry("#goodreads_review_count", (builder, value) -> safeParseInt(value, builder::goodreadsReviewCount)),
        Map.entry("#hardcover_rating", (builder, value) -> safeParseDouble(value, builder::hardcoverRating)),
        Map.entry("#hardcover_review_count", (builder, value) -> safeParseInt(value, builder::hardcoverReviewCount)),
        Map.entry("#lubimyczytac_rating", (builder, value) -> safeParseDouble(value, builder::lubimyczytacRating)),
        Map.entry("#ranobedb_rating", (builder, value) -> safeParseDouble(value, builder::ranobedbRating))
    );

    @Override
    public byte[] extractCover(File epubFile) {
        try (FileInputStream fis = new FileInputStream(epubFile)) {
            Book epub = new EpubReader().readEpub(fis);
            io.documentnode.epub4j.domain.Resource coverImage = epub.getCoverImage();

            if (coverImage == null) {
                String coverHref = findCoverImageHrefInOpf(epubFile);
                if (coverHref != null) {
                    byte[] data = extractFileFromZip(epubFile, coverHref);
                    if (data != null) return data;
                }
            }

            if (coverImage == null) {
                for (io.documentnode.epub4j.domain.Resource res : epub.getResources().getAll()) {
                    String id = res.getId();
                    String href = res.getHref();
                    if ((id != null && id.toLowerCase().contains("cover")) ||
                            (href != null && href.toLowerCase().contains("cover"))) {
                        if (res.getMediaType() != null && res.getMediaType().getName().startsWith("image")) {
                            coverImage = res;
                            break;
                        }
                    }
                }
            }

            return (coverImage != null) ? coverImage.getData() : null;
        } catch (Exception e) {
            log.warn("Failed to extract cover from EPUB: {}", epubFile.getName(), e);
            return null;
        }
    }

    @Override
    public BookMetadata extractMetadata(File epubFile) {
        try (ZipFile zip = new ZipFile(epubFile)) {
            DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
            dbf.setNamespaceAware(true);
            dbf.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            DocumentBuilder builder = dbf.newDocumentBuilder();

            FileHeader containerHdr = zip.getFileHeader("META-INF/container.xml");
            if (containerHdr == null) return null;

            try (InputStream cis = zip.getInputStream(containerHdr)) {
                Document containerDoc = builder.parse(cis);
                NodeList roots = containerDoc.getElementsByTagName("rootfile");
                if (roots.getLength() == 0) return null;

                String opfPath = ((Element) roots.item(0)).getAttribute("full-path");
                if (StringUtils.isBlank(opfPath)) return null;

                FileHeader opfHdr = zip.getFileHeader(opfPath);
                if (opfHdr == null) return null;

                try (InputStream in = zip.getInputStream(opfHdr)) {
                    Document doc = builder.parse(in);
                    Element metadata = (Element) doc.getElementsByTagNameNS("*", "metadata").item(0);
                    if (metadata == null) return null;

                    BookMetadata.BookMetadataBuilder builderMeta = BookMetadata.builder();
                    Set<String> categories = new HashSet<>();
                    Set<String> moods = new HashSet<>();
                    Set<String> tags = new HashSet<>();
                    Set<String> processedIdentifierFields = new HashSet<>();

                    boolean seriesFound = false;
                    boolean seriesIndexFound = false;

                    NodeList children = metadata.getChildNodes();

                    Map<String, String> creatorsById = new HashMap<>();
                    Map<String, String> creatorRoleById = new HashMap<>();
                    Map<String, Set<String>> creatorsByRole = new HashMap<>();
                    creatorsByRole.put("aut", new HashSet<>());

                    Map<String, String> titlesById = new HashMap<>();
                    Map<String, String> titleTypeById = new HashMap<>();

                    for (int i = 0; i < children.getLength(); i++) {
                        if (!(children.item(i) instanceof Element el)) continue;

                        String tag = el.getLocalName();
                        String text = el.getTextContent().trim();

                        switch (tag) {
                            case "title" -> {
                                String id = el.getAttribute("id");
                                if (StringUtils.isNotBlank(id)) {
                                    titlesById.put(id, text);
                                } else {
                                    builderMeta.title(text);
                                }
                            }
                            case "meta" -> {
                                String prop = el.getAttribute("property").trim();
                                String name = el.getAttribute("name").trim();
                                String refines = el.getAttribute("refines").trim();
                                String content = el.hasAttribute("content") ? el.getAttribute("content").trim() : text;

                                if ("title-type".equals(prop) && StringUtils.isNotBlank(refines)) {
                                    titleTypeById.put(refines.substring(1), content.toLowerCase());
                                }

                                if ("role".equals(prop) && StringUtils.isNotBlank(refines)) {
                                    creatorRoleById.put(refines.substring(1), content.toLowerCase());
                                }

                                if (!seriesFound && ("booklore:series".equals(prop) || "calibre:series".equals(name) || "belongs-to-collection".equals(prop))) {
                                    builderMeta.seriesName(content);
                                    seriesFound = true;
                                }
                                if (!seriesIndexFound && ("booklore:series_index".equals(prop) || "calibre:series_index".equals(name) || "group-position".equals(prop))) {
                                    try {
                                        builderMeta.seriesNumber(Float.parseFloat(content));
                                        seriesIndexFound = true;
                                    } catch (NumberFormatException ignored) {
                                    }
                                }

                                switch (prop) {
                                    case "booklore:asin" -> builderMeta.asin(content);
                                    case "booklore:goodreads_id" -> builderMeta.goodreadsId(content);
                                    case "booklore:comicvine_id" -> builderMeta.comicvineId(content);
                                    case "booklore:hardcover_id" -> builderMeta.hardcoverId(content);
                                    case "booklore:google_books_id" -> builderMeta.googleId(content);
                                    case "booklore:page_count" -> safeParseInt(content, builderMeta::pageCount);
                                    case "booklore:moods" -> extractSetField(content, moods);
                                    case "booklore:tags" -> extractSetField(content, tags);
                                    case "booklore:series_total" -> safeParseInt(content, builderMeta::seriesTotal);
                                    case "booklore:amazon_rating" -> safeParseDouble(content, builderMeta::amazonRating);
                                    case "booklore:amazon_review_count" -> safeParseInt(content, builderMeta::amazonReviewCount);
                                    case "booklore:goodreads_rating" -> safeParseDouble(content, builderMeta::goodreadsRating);
                                    case "booklore:goodreads_review_count" -> safeParseInt(content, builderMeta::goodreadsReviewCount);
                                    case "booklore:hardcover_book_id" -> builderMeta.hardcoverBookId(content);
                                    case "booklore:hardcover_rating" -> safeParseDouble(content, builderMeta::hardcoverRating);
                                    case "booklore:hardcover_review_count" -> safeParseInt(content, builderMeta::hardcoverReviewCount);
                                    case "booklore:lubimyczytac_rating" -> safeParseDouble(content, value -> builderMeta.lubimyczytacRating(value));
                                    case "booklore:ranobedb_rating" -> safeParseDouble(content, builderMeta::ranobedbRating);
                                }

                                if ("calibre:user_metadata".equals(prop)) {
                                    try {
                                        JSONObject jsonroot = new JSONObject(content);
                                        extractCalibreUserMetadata(jsonroot, builderMeta, moods, tags);
                                    } catch (JSONException e) {
                                        log.warn("Failed to parse Calibre user_metadata JSON: {}", e.getMessage());
                                    }
                                }
                            }
                            case "creator" -> {
                                String role = el.getAttributeNS(OPF_NS, "role");
                                if (StringUtils.isNotBlank(role)) {
                                    creatorsByRole.computeIfAbsent(role, k -> new HashSet<>()).add(text);
                                } else {
                                    String id = el.getAttribute("id");
                                    if (StringUtils.isNotBlank(id)) {
                                        creatorsById.put(id, text);
                                    } else {
                                        creatorsByRole.get("aut").add(text);
                                    }
                                }
                            }
                            case "subject" -> categories.add(text);
                            case "description" -> builderMeta.description(text);
                            case "publisher" -> builderMeta.publisher(text);
                            case "language" -> builderMeta.language(text);
                            case "identifier" -> {
                                String scheme = el.getAttributeNS(OPF_NS, "scheme").toUpperCase();
                                String value = text.toLowerCase();

                                if (processIdentifierWithPrefix(value, builderMeta, processedIdentifierFields)) {
                                    continue;
                                }

                                if (value.startsWith("isbn:")) {
                                    value = value.substring("isbn:".length());
                                }

                                if (!scheme.isEmpty()) {
                                    processIdentifierByScheme(scheme, value, builderMeta, processedIdentifierFields);
                                } else {
                                    processIsbnIdentifier(value, builderMeta, processedIdentifierFields);
                                }
                            }
                            case "date" -> {
                                LocalDate parsed = parseDate(text);
                                if (parsed != null) builderMeta.publishedDate(parsed);
                            }
                        }
                    }

                    for (Map.Entry<String, String> entry : titlesById.entrySet()) {
                        String id = entry.getKey();
                        String value = entry.getValue();
                        String type = titleTypeById.getOrDefault(id, "main");
                        if ("main".equals(type)) builderMeta.title(value);
                        else if ("subtitle".equals(type)) builderMeta.subtitle(value);
                    }

                    if (builderMeta.build().getPublishedDate() == null) {
                        for (int i = 0; i < children.getLength(); i++) {
                            if (!(children.item(i) instanceof Element el)) continue;
                            if (!"meta".equals(el.getLocalName())) continue;
                            String prop = el.getAttribute("property").trim().toLowerCase();
                            String content = el.hasAttribute("content") ? el.getAttribute("content").trim() : el.getTextContent().trim();
                            if ("dcterms:modified".equals(prop)) {
                                LocalDate parsed = parseDate(content);
                                if (parsed != null) {
                                    builderMeta.publishedDate(parsed);
                                    break;
                                }
                            }
                        }
                    }

                    for (Map.Entry<String, String> entry : creatorsById.entrySet()) {
                        String id = entry.getKey();
                        String value = entry.getValue();
                        String role = creatorRoleById.getOrDefault(id, "aut");
                        creatorsByRole.computeIfAbsent(role, k -> new HashSet<>()).add(value);
                    }

                    builderMeta.authors(creatorsByRole.get("aut"));
                    builderMeta.categories(categories);
                    builderMeta.moods(moods);
                    builderMeta.tags(tags);

                    BookMetadata extractedMetadata = builderMeta.build();

                    if (StringUtils.isBlank(extractedMetadata.getTitle())) {
                        builderMeta.title(FilenameUtils.getBaseName(epubFile.getName()));
                        extractedMetadata = builderMeta.build();
                    }

                    return extractedMetadata;
                }
            }

        } catch (Exception e) {
            log.error("Failed to read metadata from EPUB file {}: {}", epubFile.getName(), e.getMessage(), e);
            return null;
        }
    }

    private boolean processIdentifierWithPrefix(String value, BookMetadata.BookMetadataBuilder builder, 
                                               Set<String> processedFields) {
        for (IdentifierMapping mapping : IDENTIFIER_PREFIX_MAPPINGS) {
            if (value.startsWith(mapping.prefix)) {
                String extractedValue = value.substring(mapping.prefix.length());
                
                // Special handling for ISBN URNs - pass to ISBN processor
                if ("isbn".equals(mapping.fieldName)) {
                    processIsbnIdentifier(extractedValue, builder, processedFields);
                    return true;
                }
                
                if (!processedFields.contains(mapping.fieldName)) {
                    mapping.setter.accept(builder, extractedValue);
                    processedFields.add(mapping.fieldName);
                }
                return true;
            }
        }
        return false;
    }

    private void processIdentifierByScheme(String scheme, String value, BookMetadata.BookMetadataBuilder builder,
                                          Set<String> processedFields) {
        if ("ISBN".equals(scheme)) {
            processIsbnIdentifier(value, builder, processedFields);
        } else {
            BiConsumer<BookMetadata.BookMetadataBuilder, String> setter = SCHEME_MAPPINGS.get(scheme);
            if (setter != null) {
                String fieldName = getFieldNameForScheme(scheme);
                if (!processedFields.contains(fieldName)) {
                    setter.accept(builder, value);
                    processedFields.add(fieldName);
                }
            }
        }
    }

    private void processIsbnIdentifier(String value, BookMetadata.BookMetadataBuilder builder, 
                                      Set<String> processedFields) {
        String cleanIsbn = value.replaceAll("[- ]", "");
        
        if (cleanIsbn.length() == 13 && ISBN_13_PATTERN.matcher(cleanIsbn).matches()) {
            if (!processedFields.contains("isbn13")) {
                builder.isbn13(value);
                processedFields.add("isbn13");
            }
        } else if (cleanIsbn.length() == 10 && ISBN_10_PATTERN.matcher(cleanIsbn).matches()) {
            if (!processedFields.contains("isbn10")) {
                builder.isbn10(value);
                processedFields.add("isbn10");
            }
        }
    }

    private String getFieldNameForScheme(String scheme) {
        return switch (scheme) {
            case "GOODREADS" -> "goodreadsId";
            case "COMICVINE" -> "comicvineId";
            case "GOOGLE" -> "googleId";
            case "AMAZON" -> "asin";
            case "HARDCOVER" -> "hardcoverId";
            default -> scheme.toLowerCase();
        };
    }

    private static void safeParseInt(String value, java.util.function.IntConsumer setter) {
        try {
            setter.accept(Integer.parseInt(value));
        } catch (NumberFormatException ignored) {
        }
    }

    private static void safeParseFloat(String value, java.util.function.Consumer<Float> setter) {
        try {
            setter.accept(Float.parseFloat(value));
        } catch (NumberFormatException ignored) {
        }
    }

    private static void safeParseDouble(String value, java.util.function.DoubleConsumer setter) {
        try {
            setter.accept(Double.parseDouble(value));
        } catch (NumberFormatException ignored) {
        }
    }
    
    private static void extractSetField(String value, Set<String> targetSet) {
        if (value == null || value.trim().isEmpty()) {
            return;
        }
        
        String trimmedValue = value.trim();
        
        if (trimmedValue.startsWith("[")) {
            try {
                JSONArray jsonArray = new JSONArray(trimmedValue);
                for (int i = 0; i < jsonArray.length(); i++) {
                    String item = jsonArray.getString(i).trim();
                    if (!item.isEmpty()) {
                        targetSet.add(item);
                    }
                }
                return;
            } catch (JSONException ignored) {
            }
        }
        
        String[] items = trimmedValue.split(",");
        for (String item : items) {
            String trimmedItem = item.trim();
            if (!trimmedItem.isEmpty()) {
                targetSet.add(trimmedItem);
            }
        }
    }

    private void extractAndSetUserMetadataSet(String value, java.util.function.Consumer<Set<String>> setter) {
        Set<String> items = new HashSet<>();
        extractSetField(value, items);
        if (!items.isEmpty()) {
            setter.accept(items);
        }
    }

    private LocalDate parseDate(String value) {
        if (StringUtils.isBlank(value)) return null;

        value = value.trim();

        // Check for year-only format first (e.g., "2024") - common in EPUB metadata
        if (YEAR_ONLY_PATTERN.matcher(value).matches()) {
            int year = Integer.parseInt(value);
            if (year >= 1 && year <= 9999) {
                return LocalDate.of(year, 1, 1);
            }
        }

        try {
            return LocalDate.parse(value);
        } catch (Exception ignored) {
        }

        try {
            return OffsetDateTime.parse(value).toLocalDate();
        } catch (Exception ignored) {
        }

        // Try parsing first 10 characters for ISO date format with extra content
        if (value.length() >= 10) {
            try {
                return LocalDate.parse(value.substring(0, 10));
            } catch (Exception ignored) {
            }
        }

        log.warn("Failed to parse date from string: {}", value);
        return null;
    }

    private String findCoverImageHrefInOpf(File epubFile) {
        try (ZipFile zip = new ZipFile(epubFile)) {
            DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
            dbf.setNamespaceAware(true);
            dbf.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            DocumentBuilder builder = dbf.newDocumentBuilder();

            FileHeader containerHdr = zip.getFileHeader("META-INF/container.xml");
            if (containerHdr == null) return null;

            try (InputStream cis = zip.getInputStream(containerHdr)) {
                Document containerDoc = builder.parse(cis);
                NodeList roots = containerDoc.getElementsByTagName("rootfile");
                if (roots.getLength() == 0) return null;

                String opfPath = ((Element) roots.item(0)).getAttribute("full-path");
                if (StringUtils.isBlank(opfPath)) return null;

                FileHeader opfHdr = zip.getFileHeader(opfPath);
                if (opfHdr == null) return null;

                try (InputStream in = zip.getInputStream(opfHdr)) {
                    Document doc = builder.parse(in);
                    NodeList manifestItems = doc.getElementsByTagName("item");

                    for (int i = 0; i < manifestItems.getLength(); i++) {
                        Element item = (Element) manifestItems.item(i);
                        String properties = item.getAttribute("properties");
                        if (properties != null && properties.contains("cover-image")) {
                            String href = item.getAttribute("href");
                            String decodedHref = URLDecoder.decode(href, StandardCharsets.UTF_8);
                            return resolvePath(opfPath, decodedHref);
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.debug("Failed to find cover image in OPF: {}", e.getMessage());
        }
        return null;
    }

    private String resolvePath(String opfPath, String href) {
        if (href == null || href.isEmpty()) return null;

        // If href is absolute within the zip (starts with /), return it without leading /
        if (href.startsWith("/")) return href.substring(1);

        int lastSlash = opfPath.lastIndexOf('/');
        String basePath = (lastSlash == -1) ? "" : opfPath.substring(0, lastSlash + 1);

        String combined = basePath + href;

        // Normalize path components to handle ".." and "."
        java.util.LinkedList<String> parts = new java.util.LinkedList<>();
        for (String part : combined.split("/")) {
            if ("..".equals(part)) {
                if (!parts.isEmpty()) parts.removeLast();
            } else if (!".".equals(part) && !part.isEmpty()) {
                parts.add(part);
            }
        }

        return String.join("/", parts);
    }

    private byte[] extractFileFromZip(File epubFile, String path) {
        try (ZipFile zip = new ZipFile(epubFile)) {
            FileHeader header = zip.getFileHeader(path);
            if (header == null) return null;
            try (InputStream is = zip.getInputStream(header)) {
                return is.readAllBytes();
            }
        } catch (Exception e) {
            log.warn("Failed to extract file {} from zip", path);
            return null;
        }
    }

    private void extractCalibreUserMetadata(JSONObject userMetadata, BookMetadata.BookMetadataBuilder builder, 
                                           Set<String> moodsSet, Set<String> tagsSet) {
        try {
            java.util.Iterator<String> keys = userMetadata.keys();
            
            while (keys.hasNext()) {
                String fieldName = keys.next();
                
                try {
                    JSONObject fieldObject = userMetadata.optJSONObject(fieldName);
                    if (fieldObject == null) {
                        continue;
                    }
                    
                    Object rawValue = fieldObject.opt("#value#");
                    if (rawValue == null) {
                        rawValue = fieldObject.opt("value");
                        if (rawValue == null) {
                            rawValue = fieldObject.opt("#val#");
                        }
                        if (rawValue == null) {
                            continue;
                        }
                    }
                    
                    String value = String.valueOf(rawValue).trim();
                    if (value.isEmpty() || "null".equals(value)) {
                        continue;
                    }
                    
                    if ("#moods".equals(fieldName)) {
                        extractSetField(value, moodsSet);
                        continue;
                    }
                    
                    if ("#extra_tags".equals(fieldName)) {
                        extractSetField(value, tagsSet);
                        continue;
                    }
                    
                    BiConsumer<BookMetadata.BookMetadataBuilder, String> mapper = CALIBRE_FIELD_MAPPINGS.get(fieldName);
                    if (mapper != null) {
                        mapper.accept(builder, value);
                    }
                    
                } catch (Exception e) {
                    log.debug("Failed to extract Calibre field '{}': {}", fieldName, e.getMessage());
                }
            }
            
        } catch (Exception e) {
            log.debug("Failed to process Calibre user_metadata: {}", e.getMessage());
        }
    }
}