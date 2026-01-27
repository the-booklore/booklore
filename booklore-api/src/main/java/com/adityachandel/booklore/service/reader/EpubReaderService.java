package com.adityachandel.booklore.service.reader;

import com.adityachandel.booklore.exception.ApiError;
import com.adityachandel.booklore.model.dto.response.EpubBookInfo;
import com.adityachandel.booklore.model.dto.response.EpubManifestItem;
import com.adityachandel.booklore.model.dto.response.EpubSpineItem;
import com.adityachandel.booklore.model.dto.response.EpubTocItem;
import com.adityachandel.booklore.model.entity.BookEntity;
import com.adityachandel.booklore.model.entity.BookFileEntity;
import com.adityachandel.booklore.model.enums.BookFileType;
import com.adityachandel.booklore.repository.BookRepository;
import com.adityachandel.booklore.util.FileUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipFile;
import org.apache.pdfbox.io.IOUtils;
import org.springframework.stereotype.Service;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class EpubReaderService {

    private static final String CONTAINER_PATH = "META-INF/container.xml";
    private static final String CONTAINER_NS = "urn:oasis:names:tc:opendocument:xmlns:container";
    private static final String OPF_NS = "http://www.idpf.org/2007/opf";
    private static final String DC_NS = "http://purl.org/dc/elements/1.1/";
    private static final String NCX_NS = "http://www.daisy.org/z3986/2005/ncx/";
    private static final String XHTML_NS = "http://www.w3.org/1999/xhtml";
    private static final String EPUB_NS = "http://www.idpf.org/2007/ops";

    private static final int MAX_CACHE_ENTRIES = 50;
    private static final Charset[] ENCODINGS_TO_TRY = {
            StandardCharsets.UTF_8,
            StandardCharsets.ISO_8859_1,
            Charset.forName("CP437")
    };
    private static final Pattern WHITESPACE_PATTERN = Pattern.compile("\\s+");

    private final BookRepository bookRepository;
    private final Map<String, CachedEpubMetadata> metadataCache = new ConcurrentHashMap<>();

    private static class CachedEpubMetadata {
        final EpubBookInfo bookInfo;
        final long lastModified;
        final Charset successfulEncoding;
        volatile long lastAccessed;

        CachedEpubMetadata(EpubBookInfo bookInfo, long lastModified, Charset encoding) {
            this.bookInfo = bookInfo;
            this.lastModified = lastModified;
            this.successfulEncoding = encoding;
            this.lastAccessed = System.currentTimeMillis();
        }
    }

    public EpubBookInfo getBookInfo(Long bookId) {
        return getBookInfo(bookId, null);
    }

    public EpubBookInfo getBookInfo(Long bookId, String bookType) {
        Path epubPath = getBookPath(bookId, bookType);
        try {
            CachedEpubMetadata metadata = getCachedMetadata(epubPath);
            return metadata.bookInfo;
        } catch (IOException e) {
            log.error("Failed to read EPUB for book {}", bookId, e);
            throw ApiError.FILE_READ_ERROR.createException("Failed to read EPUB: " + e.getMessage());
        }
    }

    public void streamFile(Long bookId, String filePath, OutputStream outputStream) throws IOException {
        streamFile(bookId, null, filePath, outputStream);
    }

    public void streamFile(Long bookId, String bookType, String filePath, OutputStream outputStream) throws IOException {
        Path epubPath = getBookPath(bookId, bookType);
        CachedEpubMetadata metadata = getCachedMetadata(epubPath);

        String cleanPath = filePath.startsWith("/") ? filePath.substring(1) : filePath;
        String actualPath;
        if (CONTAINER_PATH.equals(cleanPath) || cleanPath.equals(metadata.bookInfo.getContainerPath())) {
            actualPath = cleanPath;
        } else {
            actualPath = normalizePath(filePath, metadata.bookInfo.getRootPath());
        }

        if (!isValidPath(actualPath, metadata.bookInfo)) {
            throw new FileNotFoundException("File not found in EPUB: " + filePath);
        }

        streamEntryFromZip(epubPath, actualPath, outputStream, metadata.successfulEncoding);
    }

    public String getContentType(Long bookId, String filePath) {
        return getContentType(bookId, null, filePath);
    }

    public String getContentType(Long bookId, String bookType, String filePath) {
        Path epubPath = getBookPath(bookId, bookType);
        try {
            CachedEpubMetadata metadata = getCachedMetadata(epubPath);
            String normalizedPath = normalizePath(filePath, metadata.bookInfo.getRootPath());

            return metadata.bookInfo.getManifest().stream()
                    .filter(item -> item.getHref().equals(normalizedPath))
                    .findFirst()
                    .map(EpubManifestItem::getMediaType)
                    .orElse(guessContentType(filePath));
        } catch (IOException e) {
            return guessContentType(filePath);
        }
    }

    public long getFileSize(Long bookId, String filePath) {
        return getFileSize(bookId, null, filePath);
    }

    public long getFileSize(Long bookId, String bookType, String filePath) {
        Path epubPath = getBookPath(bookId, bookType);
        try {
            CachedEpubMetadata metadata = getCachedMetadata(epubPath);
            String normalizedPath = normalizePath(filePath, metadata.bookInfo.getRootPath());

            return metadata.bookInfo.getManifest().stream()
                    .filter(item -> item.getHref().equals(normalizedPath))
                    .findFirst()
                    .map(EpubManifestItem::getSize)
                    .orElse(0L);
        } catch (IOException e) {
            return 0L;
        }
    }

    private Path getBookPath(Long bookId, String bookType) {
        BookEntity bookEntity = bookRepository.findById(bookId)
                .orElseThrow(() -> ApiError.BOOK_NOT_FOUND.createException(bookId));
        if (bookType != null) {
            BookFileType requestedType = BookFileType.valueOf(bookType.toUpperCase());
            BookFileEntity bookFile = bookEntity.getBookFiles().stream()
                    .filter(bf -> bf.getBookType() == requestedType)
                    .findFirst()
                    .orElseThrow(() -> ApiError.FILE_NOT_FOUND.createException("No file of type " + bookType + " found for book"));
            return bookFile.getFullFilePath();
        }
        String bookFullPath = FileUtils.getBookFullPath(bookEntity);
        return Path.of(bookFullPath);
    }

    private CachedEpubMetadata getCachedMetadata(Path epubPath) throws IOException {
        String cacheKey = epubPath.toString();
        long currentModified = Files.getLastModifiedTime(epubPath).toMillis();
        CachedEpubMetadata cached = metadataCache.get(cacheKey);

        if (cached != null && cached.lastModified == currentModified) {
            cached.lastAccessed = System.currentTimeMillis();
            log.debug("Cache hit for EPUB: {}", epubPath.getFileName());
            return cached;
        }

        log.debug("Cache miss for EPUB: {}, parsing...", epubPath.getFileName());
        CachedEpubMetadata newMetadata = parseEpubMetadata(epubPath, currentModified);
        metadataCache.put(cacheKey, newMetadata);
        evictOldestCacheEntries();
        return newMetadata;
    }

    private void evictOldestCacheEntries() {
        if (metadataCache.size() <= MAX_CACHE_ENTRIES) {
            return;
        }
        List<String> keysToRemove = metadataCache.entrySet().stream()
                .sorted(Comparator.comparingLong(e -> e.getValue().lastAccessed))
                .limit(metadataCache.size() - MAX_CACHE_ENTRIES)
                .map(Map.Entry::getKey)
                .toList();
        keysToRemove.forEach(key -> {
            metadataCache.remove(key);
            log.debug("Evicted EPUB cache entry: {}", key);
        });
    }

    private CachedEpubMetadata parseEpubMetadata(Path epubPath, long lastModified) throws IOException {
        for (Charset encoding : ENCODINGS_TO_TRY) {
            try {
                EpubBookInfo bookInfo = parseEpubWithEncoding(epubPath, encoding);
                return new CachedEpubMetadata(bookInfo, lastModified, encoding);
            } catch (Exception e) {
                log.debug("Failed to parse EPUB with encoding {}: {}", encoding, e.getMessage());
            }
        }
        throw new IOException("Unable to parse EPUB with any supported encoding");
    }

    private EpubBookInfo parseEpubWithEncoding(Path epubPath, Charset charset) throws Exception {
        try (ZipFile zipFile = ZipFile.builder()
                .setPath(epubPath)
                .setCharset(charset)
                .setUseUnicodeExtraFields(true)
                .get()) {

            String opfPath = parseContainerXml(zipFile);
            String rootPath = opfPath.contains("/") ? opfPath.substring(0, opfPath.lastIndexOf('/') + 1) : "";

            Document opfDoc = parseXmlEntry(zipFile, opfPath);
            Element packageEl = opfDoc.getDocumentElement();

            List<EpubManifestItem> manifest = parseManifest(opfDoc, rootPath, zipFile);
            Map<String, EpubManifestItem> manifestById = new HashMap<>();
            for (EpubManifestItem item : manifest) {
                manifestById.put(item.getId(), item);
            }

            List<EpubSpineItem> spine = parseSpine(opfDoc, manifestById);

            Map<String, Object> metadata = parseMetadata(opfDoc);

            String navPath = null;
            String ncxPath = null;
            for (EpubManifestItem item : manifest) {
                if (item.getProperties() != null && item.getProperties().contains("nav")) {
                    navPath = item.getHref();
                }
                if ("application/x-dtbncx+xml".equals(item.getMediaType())) {
                    ncxPath = item.getHref();
                }
            }

            EpubTocItem toc = null;
            if (navPath != null) {
                try {
                    toc = parseNavDocument(zipFile, navPath, rootPath);
                } catch (Exception e) {
                    log.warn("Failed to parse nav document, trying NCX: {}", e.getMessage());
                }
            }
            if (toc == null && ncxPath != null) {
                try {
                    toc = parseNcx(zipFile, ncxPath, rootPath);
                } catch (Exception e) {
                    log.warn("Failed to parse NCX: {}", e.getMessage());
                }
            }

            String coverPath = findCoverPath(opfDoc, manifest, manifestById);

            return EpubBookInfo.builder()
                    .containerPath(opfPath)
                    .rootPath(rootPath)
                    .spine(spine)
                    .manifest(manifest)
                    .toc(toc)
                    .metadata(metadata)
                    .coverPath(coverPath)
                    .build();
        }
    }

    private String parseContainerXml(ZipFile zipFile) throws Exception {
        Document doc = parseXmlEntry(zipFile, CONTAINER_PATH);
        NodeList rootfiles = doc.getElementsByTagNameNS(CONTAINER_NS, "rootfile");
        if (rootfiles.getLength() == 0) {
            rootfiles = doc.getElementsByTagName("rootfile");
        }
        if (rootfiles.getLength() == 0) {
            throw new IOException("No rootfile found in container.xml");
        }
        Element rootfile = (Element) rootfiles.item(0);
        String fullPath = rootfile.getAttribute("full-path");
        if (fullPath == null || fullPath.isEmpty()) {
            throw new IOException("No full-path attribute in rootfile");
        }
        return fullPath;
    }

    private List<EpubManifestItem> parseManifest(Document opfDoc, String rootPath, ZipFile zipFile) {
        List<EpubManifestItem> manifest = new ArrayList<>();
        NodeList items = opfDoc.getElementsByTagNameNS(OPF_NS, "item");
        if (items.getLength() == 0) {
            items = opfDoc.getElementsByTagName("item");
        }

        for (int i = 0; i < items.getLength(); i++) {
            Element item = (Element) items.item(i);
            String id = item.getAttribute("id");
            String href = item.getAttribute("href");
            String mediaType = item.getAttribute("media-type");
            String properties = item.getAttribute("properties");

            String fullHref = rootPath + href;
            long size = getEntrySize(zipFile, fullHref);

            List<String> propList = null;
            if (properties != null && !properties.isEmpty()) {
                propList = Arrays.asList(WHITESPACE_PATTERN.split(properties));
            }

            manifest.add(EpubManifestItem.builder()
                    .id(id)
                    .href(fullHref)
                    .mediaType(mediaType)
                    .properties(propList)
                    .size(size)
                    .build());
        }
        return manifest;
    }

    private List<EpubSpineItem> parseSpine(Document opfDoc, Map<String, EpubManifestItem> manifestById) {
        List<EpubSpineItem> spine = new ArrayList<>();
        NodeList itemrefs = opfDoc.getElementsByTagNameNS(OPF_NS, "itemref");
        if (itemrefs.getLength() == 0) {
            itemrefs = opfDoc.getElementsByTagName("itemref");
        }

        for (int i = 0; i < itemrefs.getLength(); i++) {
            Element itemref = (Element) itemrefs.item(i);
            String idref = itemref.getAttribute("idref");
            String linear = itemref.getAttribute("linear");

            EpubManifestItem manifestItem = manifestById.get(idref);
            if (manifestItem != null) {
                spine.add(EpubSpineItem.builder()
                        .idref(idref)
                        .href(manifestItem.getHref())
                        .mediaType(manifestItem.getMediaType())
                        .linear(!"no".equals(linear))
                        .build());
            }
        }
        return spine;
    }

    private Map<String, Object> parseMetadata(Document opfDoc) {
        Map<String, Object> metadata = new HashMap<>();

        String title = getElementTextByNS(opfDoc, DC_NS, "title");
        if (title == null) title = getElementText(opfDoc, "dc:title");
        if (title != null) metadata.put("title", title);

        String creator = getElementTextByNS(opfDoc, DC_NS, "creator");
        if (creator == null) creator = getElementText(opfDoc, "dc:creator");
        if (creator != null) metadata.put("creator", creator);

        String language = getElementTextByNS(opfDoc, DC_NS, "language");
        if (language == null) language = getElementText(opfDoc, "dc:language");
        if (language != null) metadata.put("language", language);

        String publisher = getElementTextByNS(opfDoc, DC_NS, "publisher");
        if (publisher == null) publisher = getElementText(opfDoc, "dc:publisher");
        if (publisher != null) metadata.put("publisher", publisher);

        String identifier = getElementTextByNS(opfDoc, DC_NS, "identifier");
        if (identifier == null) identifier = getElementText(opfDoc, "dc:identifier");
        if (identifier != null) metadata.put("identifier", identifier);

        String description = getElementTextByNS(opfDoc, DC_NS, "description");
        if (description == null) description = getElementText(opfDoc, "dc:description");
        if (description != null) metadata.put("description", description);

        return metadata;
    }

    private EpubTocItem parseNavDocument(ZipFile zipFile, String navPath, String rootPath) throws Exception {
        Document doc = parseXmlEntry(zipFile, navPath);

        NodeList navs = doc.getElementsByTagNameNS(XHTML_NS, "nav");
        if (navs.getLength() == 0) {
            navs = doc.getElementsByTagName("nav");
        }

        Element tocNav = null;
        for (int i = 0; i < navs.getLength(); i++) {
            Element nav = (Element) navs.item(i);
            String type = nav.getAttributeNS(EPUB_NS, "type");
            if (type == null || type.isEmpty()) {
                type = nav.getAttribute("epub:type");
            }
            if ("toc".equals(type)) {
                tocNav = nav;
                break;
            }
        }

        if (tocNav == null && navs.getLength() > 0) {
            tocNav = (Element) navs.item(0);
        }

        if (tocNav == null) {
            return null;
        }

        NodeList ols = tocNav.getElementsByTagNameNS(XHTML_NS, "ol");
        if (ols.getLength() == 0) {
            ols = tocNav.getElementsByTagName("ol");
        }

        if (ols.getLength() == 0) {
            return null;
        }

        String navDir = navPath.contains("/") ? navPath.substring(0, navPath.lastIndexOf('/') + 1) : rootPath;
        List<EpubTocItem> children = parseNavOl((Element) ols.item(0), navDir);

        return EpubTocItem.builder()
                .label("Table of Contents")
                .children(children)
                .build();
    }

    private List<EpubTocItem> parseNavOl(Element ol, String basePath) {
        List<EpubTocItem> items = new ArrayList<>();
        NodeList children = ol.getChildNodes();

        for (int i = 0; i < children.getLength(); i++) {
            if (children.item(i) instanceof Element li && "li".equals(li.getLocalName())) {
                EpubTocItem item = parseNavLi(li, basePath);
                if (item != null) {
                    items.add(item);
                }
            }
        }
        return items;
    }

    private EpubTocItem parseNavLi(Element li, String basePath) {
        Element link = null;
        NodeList children = li.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            if (children.item(i) instanceof Element el) {
                if ("a".equals(el.getLocalName()) || "span".equals(el.getLocalName())) {
                    link = el;
                    break;
                }
            }
        }

        if (link == null) return null;

        String label = link.getTextContent().trim();
        String href = link.getAttribute("href");
        if (href != null && !href.isEmpty() && !href.startsWith("http")) {
            href = resolveHref(href, basePath);
        }

        List<EpubTocItem> subItems = null;
        for (int i = 0; i < children.getLength(); i++) {
            if (children.item(i) instanceof Element el && "ol".equals(el.getLocalName())) {
                subItems = parseNavOl(el, basePath);
                break;
            }
        }

        return EpubTocItem.builder()
                .label(label)
                .href(href)
                .children(subItems)
                .build();
    }

    private EpubTocItem parseNcx(ZipFile zipFile, String ncxPath, String rootPath) throws Exception {
        Document doc = parseXmlEntry(zipFile, ncxPath);

        NodeList navMaps = doc.getElementsByTagNameNS(NCX_NS, "navMap");
        if (navMaps.getLength() == 0) {
            navMaps = doc.getElementsByTagName("navMap");
        }

        if (navMaps.getLength() == 0) {
            return null;
        }

        Element navMap = (Element) navMaps.item(0);
        String ncxDir = ncxPath.contains("/") ? ncxPath.substring(0, ncxPath.lastIndexOf('/') + 1) : rootPath;
        List<EpubTocItem> children = parseNcxNavPoints(navMap, ncxDir);

        return EpubTocItem.builder()
                .label("Table of Contents")
                .children(children)
                .build();
    }

    private List<EpubTocItem> parseNcxNavPoints(Element parent, String basePath) {
        List<EpubTocItem> items = new ArrayList<>();
        NodeList navPoints = parent.getChildNodes();

        for (int i = 0; i < navPoints.getLength(); i++) {
            if (navPoints.item(i) instanceof Element el && "navPoint".equals(el.getLocalName())) {
                EpubTocItem item = parseNcxNavPoint(el, basePath);
                if (item != null) {
                    items.add(item);
                }
            }
        }
        return items;
    }

    private EpubTocItem parseNcxNavPoint(Element navPoint, String basePath) {
        String label = null;
        NodeList labels = navPoint.getElementsByTagNameNS(NCX_NS, "navLabel");
        if (labels.getLength() == 0) {
            labels = navPoint.getElementsByTagName("navLabel");
        }
        if (labels.getLength() > 0) {
            Element labelEl = (Element) labels.item(0);
            NodeList texts = labelEl.getElementsByTagNameNS(NCX_NS, "text");
            if (texts.getLength() == 0) {
                texts = labelEl.getElementsByTagName("text");
            }
            if (texts.getLength() > 0) {
                label = texts.item(0).getTextContent().trim();
            }
        }

        String href = null;
        NodeList contents = navPoint.getElementsByTagNameNS(NCX_NS, "content");
        if (contents.getLength() == 0) {
            contents = navPoint.getElementsByTagName("content");
        }
        if (contents.getLength() > 0) {
            Element content = (Element) contents.item(0);
            href = content.getAttribute("src");
            if (href != null && !href.isEmpty()) {
                href = resolveHref(href, basePath);
            }
        }

        List<EpubTocItem> subItems = parseNcxNavPoints(navPoint, basePath);

        return EpubTocItem.builder()
                .label(label)
                .href(href)
                .children(subItems.isEmpty() ? null : subItems)
                .build();
    }

    private String findCoverPath(Document opfDoc, List<EpubManifestItem> manifest, Map<String, EpubManifestItem> manifestById) {
        for (EpubManifestItem item : manifest) {
            if (item.getProperties() != null && item.getProperties().contains("cover-image")) {
                return item.getHref();
            }
        }

        NodeList metas = opfDoc.getElementsByTagNameNS(OPF_NS, "meta");
        if (metas.getLength() == 0) {
            metas = opfDoc.getElementsByTagName("meta");
        }
        for (int i = 0; i < metas.getLength(); i++) {
            Element meta = (Element) metas.item(i);
            if ("cover".equals(meta.getAttribute("name"))) {
                String coverId = meta.getAttribute("content");
                EpubManifestItem coverItem = manifestById.get(coverId);
                if (coverItem != null) {
                    return coverItem.getHref();
                }
            }
        }

        return null;
    }

    private Document parseXmlEntry(ZipFile zipFile, String entryPath) throws Exception {
        ZipArchiveEntry entry = zipFile.getEntry(entryPath);
        if (entry == null) {
            throw new FileNotFoundException("Entry not found: " + entryPath);
        }

        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);

        DocumentBuilder builder = factory.newDocumentBuilder();
        try (InputStream is = zipFile.getInputStream(entry)) {
            return builder.parse(is);
        }
    }

    private long getEntrySize(ZipFile zipFile, String entryPath) {
        ZipArchiveEntry entry = zipFile.getEntry(entryPath);
        return entry != null ? entry.getSize() : 0;
    }

    private String getElementTextByNS(Document doc, String ns, String tagName) {
        NodeList nodes = doc.getElementsByTagNameNS(ns, tagName);
        if (nodes.getLength() > 0) {
            return nodes.item(0).getTextContent().trim();
        }
        return null;
    }

    private String getElementText(Document doc, String tagName) {
        NodeList nodes = doc.getElementsByTagName(tagName);
        if (nodes.getLength() > 0) {
            return nodes.item(0).getTextContent().trim();
        }
        return null;
    }

    private String resolveHref(String href, String basePath) {
        if (href == null || href.isEmpty()) return href;
        if (href.startsWith("/")) return href.substring(1);
        if (href.startsWith("http://") || href.startsWith("https://")) return href;

        String result = basePath + href;
        while (result.contains("/../")) {
            int idx = result.indexOf("/../");
            int prevSlash = result.lastIndexOf('/', idx - 1);
            if (prevSlash >= 0) {
                result = result.substring(0, prevSlash) + result.substring(idx + 3);
            } else {
                break;
            }
        }
        return result;
    }

    private String normalizePath(String path, String rootPath) {
        if (path == null) return null;

        String normalized = path.startsWith("/") ? path.substring(1) : path;

        if (rootPath != null && !rootPath.isEmpty() && normalized.startsWith(rootPath)) {
            return normalized;
        }

        if (rootPath != null && !rootPath.isEmpty()) {
            return rootPath + normalized;
        }

        return normalized;
    }

    private boolean isValidPath(String path, EpubBookInfo info) {
        if (path == null) return false;

        if (path.contains("..")) return false;

        if (CONTAINER_PATH.equals(path)) return true;
        if (info.getContainerPath() != null && info.getContainerPath().equals(path)) return true;

        return info.getManifest().stream()
                .anyMatch(item -> item.getHref().equals(path));
    }

    private void streamEntryFromZip(Path epubPath, String entryName, OutputStream outputStream, Charset cachedEncoding) throws IOException {
        if (cachedEncoding != null) {
            if (tryStreamEntryFromZip(epubPath, entryName, outputStream, cachedEncoding)) {
                return;
            }
        }

        for (Charset encoding : ENCODINGS_TO_TRY) {
            if (encoding.equals(cachedEncoding)) continue;
            if (tryStreamEntryFromZip(epubPath, entryName, outputStream, encoding)) {
                return;
            }
        }

        throw new IOException("Unable to stream entry from EPUB: " + entryName);
    }

    private boolean tryStreamEntryFromZip(Path epubPath, String entryName, OutputStream outputStream, Charset charset) throws IOException {
        try (ZipFile zipFile = ZipFile.builder()
                .setPath(epubPath)
                .setCharset(charset)
                .setUseUnicodeExtraFields(true)
                .get()) {
            ZipArchiveEntry entry = zipFile.getEntry(entryName);
            if (entry != null) {
                try (InputStream in = zipFile.getInputStream(entry)) {
                    IOUtils.copy(in, outputStream);
                }
                return true;
            }
        }
        return false;
    }

    private String guessContentType(String path) {
        if (path == null) return "application/octet-stream";
        String lower = path.toLowerCase();
        if (lower.endsWith(".xhtml") || lower.endsWith(".html") || lower.endsWith(".htm")) {
            return "application/xhtml+xml";
        }
        if (lower.endsWith(".css")) return "text/css";
        if (lower.endsWith(".js")) return "application/javascript";
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) return "image/jpeg";
        if (lower.endsWith(".png")) return "image/png";
        if (lower.endsWith(".gif")) return "image/gif";
        if (lower.endsWith(".svg")) return "image/svg+xml";
        if (lower.endsWith(".webp")) return "image/webp";
        if (lower.endsWith(".woff")) return "font/woff";
        if (lower.endsWith(".woff2")) return "font/woff2";
        if (lower.endsWith(".ttf")) return "font/ttf";
        if (lower.endsWith(".otf")) return "font/otf";
        if (lower.endsWith(".eot")) return "application/vnd.ms-fontobject";
        if (lower.endsWith(".xml")) return "application/xml";
        if (lower.endsWith(".ncx")) return "application/x-dtbncx+xml";
        if (lower.endsWith(".smil")) return "application/smil+xml";
        if (lower.endsWith(".mp3")) return "audio/mpeg";
        if (lower.endsWith(".mp4")) return "video/mp4";
        if (lower.endsWith(".webm")) return "video/webm";
        return "application/octet-stream";
    }
}
