package com.adityachandel.booklore.service.metadata.extractor;

import com.adityachandel.booklore.model.dto.BookMetadata;
import io.documentnode.epub4j.domain.Book;
import io.documentnode.epub4j.epub.EpubReader;
import lombok.extern.slf4j.Slf4j;
import net.lingala.zip4j.ZipFile;
import net.lingala.zip4j.model.FileHeader;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.StringUtils;
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
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

@Slf4j
@Component
public class EpubMetadataExtractor implements FileMetadataExtractor {

    private static final Pattern YEAR_ONLY_PATTERN = Pattern.compile("^\\d{4}$");
    private static final String OPF_NS = "http://www.idpf.org/2007/opf";

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

                                if ("calibre:pages".equals(name) || "pagecount".equals(name) || "schema:pagecount".equals(prop) || "media:pagecount".equals(prop) || "booklore:page_count".equals(prop)) {
                                    safeParseInt(content, builderMeta::pageCount);
                                } else if ("calibre:user_metadata:#pagecount".equals(name)) {
                                    try {
                                        JSONObject jsonroot = new JSONObject(content);
                                        Object value = jsonroot.opt("#value#");
                                        safeParseInt(String.valueOf(value), builderMeta::pageCount);
                                    } catch (JSONException ignored) {
                                    }
                                } else if ("calibre:user_metadata".equals(prop)) {
                                    try {
                                        JSONObject jsonroot = new JSONObject(content);
                                        JSONObject pages = jsonroot.getJSONObject("#pagecount");
                                        Object value = pages.opt("#value#");
                                        safeParseInt(String.valueOf(value), builderMeta::pageCount);
                                    } catch (JSONException ignored) {
                                    }
                                }

                                switch (prop) {
                                    case "booklore:asin" -> builderMeta.asin(content);
                                    case "booklore:goodreads_id" -> builderMeta.goodreadsId(content);
                                    case "booklore:comicvine_id" -> builderMeta.comicvineId(content);
                                    case "booklore:hardcover_id" -> builderMeta.hardcoverId(content);
                                    case "booklore:google_books_id" -> builderMeta.googleId(content);
                                    case "booklore:page_count" -> safeParseInt(content, builderMeta::pageCount);
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
                                String value = text.toLowerCase().startsWith("isbn:") ? text.substring(5) : text;

                                if (!scheme.isEmpty()) {
                                    switch (scheme) {
                                        case "ISBN" -> {
                                            if (value.length() == 13) builderMeta.isbn13(value);
                                            else if (value.length() == 10) builderMeta.isbn10(value);
                                        }
                                        case "GOODREADS" -> builderMeta.goodreadsId(value);
                                        case "COMICVINE" -> builderMeta.comicvineId(value);
                                        case "GOOGLE" -> builderMeta.googleId(value);
                                        case "AMAZON" -> builderMeta.asin(value);
                                        case "HARDCOVER" -> builderMeta.hardcoverId(value);
                                    }
                                } else {
                                    if (text.toLowerCase().startsWith("isbn:")) {
                                        if (value.length() == 13) builderMeta.isbn13(value);
                                        else if (value.length() == 10) builderMeta.isbn10(value);
                                    }
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

    private void safeParseInt(String value, java.util.function.IntConsumer setter) {
        try {
            setter.accept(Integer.parseInt(value));
        } catch (NumberFormatException ignored) {
        }
    }

    private void safeParseDouble(String value, java.util.function.DoubleConsumer setter) {
        try {
            setter.accept(Double.parseDouble(value));
        } catch (NumberFormatException ignored) {
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
}