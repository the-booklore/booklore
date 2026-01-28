package com.adityachandel.booklore.service.metadata.extractor;

import com.adityachandel.booklore.model.dto.BookMetadata;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.cos.COSDictionary;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.io.RandomAccessReadBufferedFile;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDDocumentInformation;
import org.apache.pdfbox.pdmodel.common.PDMetadata;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.springframework.stereotype.Component;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;

import javax.imageio.ImageIO;
import javax.xml.namespace.NamespaceContext;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Component
@Slf4j
public class PdfMetadataExtractor implements FileMetadataExtractor {


    private static final Pattern COMMA_AMPERSAND_PATTERN = Pattern.compile("[,&]");
    private static final Pattern ISBN_CLEANUP_PATTERN = Pattern.compile("[^0-9Xx]");

    @Override
    public byte[] extractCover(File file) {
        BufferedImage coverImage = null;
        try (RandomAccessReadBufferedFile randomAccessRead = new RandomAccessReadBufferedFile(file);
             PDDocument pdf = Loader.loadPDF(randomAccessRead)) {
            coverImage = new PDFRenderer(pdf).renderImageWithDPI(0, 300, ImageType.RGB);
            try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
                ImageIO.write(coverImage, "jpg", baos);
                return baos.toByteArray();
            }
        } catch (Exception e) {
            log.warn("Failed to extract cover from PDF: {}", file.getAbsolutePath(), e);
            return null;
        } finally {
            if (coverImage != null) {
                coverImage.flush(); // Release native resources
            }
        }
    }

    @Override
    public BookMetadata extractMetadata(File file) {
        if (!file.exists() || !file.isFile()) {
            log.warn("File does not exist or is not a file: {}", file.getPath());
            return BookMetadata.builder().build();
        }

        BookMetadata.BookMetadataBuilder metadataBuilder = BookMetadata.builder();

        try (RandomAccessReadBufferedFile randomAccessRead = new RandomAccessReadBufferedFile(file);
             PDDocument pdf = Loader.loadPDF(randomAccessRead)) {
            PDDocumentInformation info = pdf.getDocumentInformation();

            if (info != null) {
                if (StringUtils.isNotBlank(info.getTitle())) {
                    metadataBuilder.title(info.getTitle());
                } else {
                    metadataBuilder.title(FilenameUtils.getBaseName(file.getName()));
                }

                if (StringUtils.isNotBlank(info.getAuthor())) {
                    Set<String> authors = parseAuthors(info.getAuthor());
                    if (!authors.isEmpty()) {
                        metadataBuilder.authors(authors);
                    }
                }

                if (StringUtils.isNotBlank(info.getSubject())) {
                    metadataBuilder.description(info.getSubject());
                }

                COSDictionary cosDict = info.getCOSObject();
                if (cosDict != null && cosDict.containsKey(COSName.getPDFName("EBX_PUBLISHER"))) {
                    String ebxPublisher = cosDict.getString(COSName.getPDFName("EBX_PUBLISHER"));
                    if (StringUtils.isNotBlank(ebxPublisher)) {
                        metadataBuilder.publisher(ebxPublisher);
                    }
                }

                if (info.getCreationDate() != null) {
                    LocalDate createdDate = convertCalendarToLocalDate(info.getCreationDate());
                    if (createdDate != null) {
                        metadataBuilder.publishedDate(createdDate);
                    }
                }

                metadataBuilder.pageCount(pdf.getNumberOfPages());

                if (StringUtils.isNotBlank(info.getKeywords())) {
                    Set<String> categories = Arrays.stream(info.getKeywords().split(","))
                            .map(String::trim)
                            .filter(StringUtils::isNotBlank)
                            .collect(Collectors.toSet());
                    if (!categories.isEmpty()) {
                        metadataBuilder.categories(categories);
                    }
                }

                String languageValue = info.getCustomMetadataValue("Language");
                if (StringUtils.isNotBlank(languageValue)) {
                    metadataBuilder.language(languageValue);
                }
            }

            PDMetadata metadata = pdf.getDocumentCatalog().getMetadata();

            if (metadata != null) {
                try (InputStream is = metadata.createInputStream()) {
                    if (is == null) {
                        log.warn("PDMetadata InputStream is null");
                    } else {
                        String rawXmp = IOUtils.toString(is, StandardCharsets.UTF_8);
                        if (StringUtils.isNotBlank(rawXmp)) {
                            DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
                            dbFactory.setNamespaceAware(true);
                            DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
                            Document doc = dBuilder.parse(new ByteArrayInputStream(rawXmp.getBytes(StandardCharsets.UTF_8)));

                            XPathFactory xPathfactory = XPathFactory.newInstance();
                            XPath xpath = xPathfactory.newXPath();
                            xpath.setNamespaceContext(new XmpNamespaceContext());

                            extractDublinCoreMetadata(xpath, doc, metadataBuilder);
                            extractCalibreMetadata(xpath, doc, metadataBuilder);
                            extractBookloreMetadata(xpath, doc, metadataBuilder);

                            Map<String, String> identifiers = extractIdentifiers(xpath, doc);
                            if (!identifiers.isEmpty()) {
                                // Extract generic ISBN first
                                String isbn = identifiers.get("isbn");
                                if (StringUtils.isNotBlank(isbn)) {
                                    isbn = ISBN_CLEANUP_PATTERN.matcher(isbn).replaceAll("");
                                    if (isbn.length() == 10) {
                                        metadataBuilder.isbn10(isbn);
                                    } else if (isbn.length() == 13) {
                                        metadataBuilder.isbn13(isbn);
                                    } else {
                                        metadataBuilder.isbn13(isbn); // Fallback
                                    }
                                }

                                // Extract specific ISBN schemes (overwrites generic if present)
                                String isbn13 = identifiers.get("isbn13");
                                if (StringUtils.isNotBlank(isbn13)) {
                                    metadataBuilder.isbn13(ISBN_CLEANUP_PATTERN.matcher(isbn13).replaceAll(""));
                                }

                                String isbn10 = identifiers.get("isbn10");
                                if (StringUtils.isNotBlank(isbn10)) {
                                    metadataBuilder.isbn10(ISBN_CLEANUP_PATTERN.matcher(isbn10).replaceAll(""));
                                }

                                String google = identifiers.get("google");
                                if (StringUtils.isNotBlank(google)) {
                                    metadataBuilder.googleId(google);
                                }

                                String amazon = identifiers.get("amazon");
                                if (StringUtils.isNotBlank(amazon)) {
                                    metadataBuilder.asin(amazon);
                                }

                                String goodreads = identifiers.get("goodreads");
                                if (StringUtils.isNotBlank(goodreads)) {
                                    metadataBuilder.goodreadsId(goodreads);
                                }

                                String comicvine = identifiers.get("comicvine");
                                if (StringUtils.isNotBlank(comicvine)) {
                                    metadataBuilder.comicvineId(comicvine);
                                }

                                String ranobedb = identifiers.get("ranobedb");
                                if (StringUtils.isNotBlank(ranobedb)) {
                                    metadataBuilder.ranobedbId(ranobedb);
                                }

                                String hardcover = identifiers.get("hardcover");
                                if (StringUtils.isNotBlank(hardcover)) {
                                    metadataBuilder.hardcoverId(hardcover);
                                }

                                String hardcoverBookId = identifiers.get("hardcover_book_id");
                                if (StringUtils.isNotBlank(hardcoverBookId)) {
                                    try {
                                        metadataBuilder.hardcoverBookId(Integer.parseInt(hardcoverBookId));
                                    } catch (NumberFormatException e) {
                                        log.warn("Invalid hardcover_book_id: {}", hardcoverBookId);
                                    }
                                }

                                String lubimyczytac = identifiers.get("lubimyczytac");
                                if (StringUtils.isNotBlank(lubimyczytac)) {
                                    metadataBuilder.lubimyczytacId(lubimyczytac);
                                }
                            }
                        }
                    }
                } catch (Exception e) {
                    log.warn("Failed to parse XMP metadata with XML parser: {}", e.getMessage(), e);
                }
            }

        } catch (Exception e) {
            log.error("Failed to load PDF file: {}", file.getPath(), e);
        }

        return metadataBuilder.build();
    }

    private void extractDublinCoreMetadata(XPath xpath, Document doc, BookMetadata.BookMetadataBuilder builder) throws XPathExpressionException {
        String title = xpathEvaluate(xpath, doc, "//dc:title/rdf:Alt/rdf:li/text()");
        if (StringUtils.isNotBlank(title)) {
            builder.title(title);
        }

        String description = xpathEvaluate(xpath, doc, "//dc:description/rdf:Alt/rdf:li/text()");
        if (StringUtils.isNotBlank(description)) {
            builder.description(description);
        }

        String publisher = xpathEvaluate(xpath, doc, "//dc:publisher/rdf:Bag/rdf:li/text()");
        if (StringUtils.isNotBlank(publisher)) {
            builder.publisher(publisher);
        }

        String language = xpathEvaluate(xpath, doc, "//dc:language/rdf:Bag/rdf:li/text()");
        if (StringUtils.isNotBlank(language)) {
            builder.language(language);
        }

        Set<String> creators = xpathEvaluateMultiple(xpath, doc, "//dc:creator/rdf:Seq/rdf:li/text()");
        if (!creators.isEmpty()) {
            builder.authors(creators);
        }

        Set<String> subjects = xpathEvaluateMultiple(xpath, doc, "//dc:subject/rdf:Bag/rdf:li/text()");
        if (!subjects.isEmpty()) {
            Set<String> knownNonCategories = new HashSet<>();
            
            try {
                String moods = xpath.evaluate("//booklore:Moods/text()", doc);
                if (StringUtils.isNotBlank(moods)) {
                    Arrays.stream(moods.split(";")).map(String::trim).forEach(knownNonCategories::add);
                }
                String tags = xpath.evaluate("//booklore:Tags/text()", doc);
                if (StringUtils.isNotBlank(tags)) {
                    Arrays.stream(tags.split(";")).map(String::trim).forEach(knownNonCategories::add);
                }
            } catch (Exception ignored) {}
            
            subjects.removeAll(knownNonCategories);
            
            if (!subjects.isEmpty()) {
                builder.categories(subjects);
            }
        }
    }

    private void extractCalibreMetadata(XPath xpath, Document doc, BookMetadata.BookMetadataBuilder builder) {
        try {
            String series = xpath.evaluate("//calibre:series/rdf:value/text()", doc);
            if (StringUtils.isNotBlank(series)) {
                builder.seriesName(series);
            }

            // Try fully qualified series_index
            String seriesIndex = xpath.evaluate("//calibre:series/calibreSI:series_index/text()", doc);

            if (StringUtils.isBlank(seriesIndex)) {
                // Try without prefix, in case it's missing namespace
                seriesIndex = xpath.evaluate("//calibre:series/*[local-name()='series_index']/text()", doc);
            }

            if (StringUtils.isNotBlank(seriesIndex)) {
                try {
                    builder.seriesNumber(Float.parseFloat(seriesIndex));
                } catch (NumberFormatException e) {
                    log.warn("Invalid series index: {}", seriesIndex);
                }
            }

        } catch (Exception e) {
            log.warn("Failed to extract calibre metadata: {}", e.getMessage(), e);
        }
    }

    private void extractBookloreMetadata(XPath xpath, Document doc, BookMetadata.BookMetadataBuilder builder) {
        try {
            String subtitle = xpath.evaluate("//booklore:Subtitle/text()", doc);
            if (StringUtils.isNotBlank(subtitle)) {
                builder.subtitle(subtitle.trim());
            }

            String moods = xpath.evaluate("//booklore:Moods/text()", doc);
            if (StringUtils.isNotBlank(moods)) {
                Set<String> moodSet = Arrays.stream(moods.split(";"))
                        .map(String::trim)
                        .filter(StringUtils::isNotBlank)
                        .collect(Collectors.toSet());
                if (!moodSet.isEmpty()) {
                    builder.moods(moodSet);
                }
            }

            String tags = xpath.evaluate("//booklore:Tags/text()", doc);
            if (StringUtils.isNotBlank(tags)) {
                Set<String> tagSet = Arrays.stream(tags.split(";"))
                        .map(String::trim)
                        .filter(StringUtils::isNotBlank)
                        .collect(Collectors.toSet());
                if (!tagSet.isEmpty()) {
                    builder.tags(tagSet);
                }
            }

            String seriesTotal = xpath.evaluate("//booklore:SeriesTotal/text()", doc);
            if (StringUtils.isNotBlank(seriesTotal)) {
                try {
                    builder.seriesTotal(Integer.parseInt(seriesTotal.trim()));
                } catch (NumberFormatException e) {
                    log.warn("Invalid series total: {}", seriesTotal);
                }
            }

            String amazonRating = xpath.evaluate("//booklore:AmazonRating/text()", doc);
            if (StringUtils.isNotBlank(amazonRating)) {
                try {
                    builder.amazonRating(Double.parseDouble(amazonRating.trim()));
                } catch (NumberFormatException e) {
                    log.warn("Invalid Amazon rating: {}", amazonRating);
                }
            }

            String goodreadsRating = xpath.evaluate("//booklore:GoodreadsRating/text()", doc);
            if (StringUtils.isNotBlank(goodreadsRating)) {
                try {
                    builder.goodreadsRating(Double.parseDouble(goodreadsRating.trim()));
                } catch (NumberFormatException e) {
                    log.warn("Invalid Goodreads rating: {}", goodreadsRating);
                }
            }

            String hardcoverRating = xpath.evaluate("//booklore:HardcoverRating/text()", doc);
            if (StringUtils.isNotBlank(hardcoverRating)) {
                try {
                    builder.hardcoverRating(Double.parseDouble(hardcoverRating.trim()));
                } catch (NumberFormatException e) {
                    log.warn("Invalid Hardcover rating: {}", hardcoverRating);
                }
            }

            String lubimyczytacRating = xpath.evaluate("//booklore:LubimyczytacRating/text()", doc);
            if (StringUtils.isNotBlank(lubimyczytacRating)) {
                try {
                    builder.lubimyczytacRating(Double.parseDouble(lubimyczytacRating.trim()));
                } catch (NumberFormatException e) {
                    log.warn("Invalid Lubimyczytac rating: {}", lubimyczytacRating);
                }
            }

            String ranobedbRating = xpath.evaluate("//booklore:RanobedbRating/text()", doc);
            if (StringUtils.isNotBlank(ranobedbRating)) {
                try {
                    builder.ranobedbRating(Double.parseDouble(ranobedbRating.trim()));
                } catch (NumberFormatException e) {
                    log.warn("Invalid RanobeDB rating: {}", ranobedbRating);
                }
            }
        } catch (Exception e) {
            log.warn("Failed to extract booklore metadata: {}", e.getMessage(), e);
        }
    }

    private Map<String, String> extractIdentifiers(XPath xpath, Document doc) throws XPathExpressionException {
        Map<String, String> ids = new HashMap<>();
        NodeList idNodes = (NodeList) xpath.evaluate(
                "//xmp:Identifier/rdf:Bag/rdf:li", doc, XPathConstants.NODESET);

        if (idNodes != null) {
            for (int i = 0; i < idNodes.getLength(); i++) {
                String scheme = xpathEvaluate(xpath, idNodes.item(i), "xmpidq:Scheme/text()");
                String value = xpathEvaluate(xpath, idNodes.item(i), "rdf:value/text()");
                if (StringUtils.isNotBlank(scheme) && StringUtils.isNotBlank(value)) {
                    ids.put(scheme.toLowerCase(Locale.ROOT), value);
                }
            }
        }
        return ids;
    }

    private Set<String> parseAuthors(String authorString) {
        if (authorString == null) return Collections.emptySet();
        return Arrays.stream(COMMA_AMPERSAND_PATTERN.split(authorString))
                .map(String::trim)
                .filter(StringUtils::isNotBlank)
                .collect(Collectors.toSet());
    }

    private LocalDate convertCalendarToLocalDate(Calendar calendar) {
        if (calendar == null) return null;
        return calendar.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
    }

    private String xpathEvaluate(XPath xpath, Document doc, String expression) throws XPathExpressionException {
        String result = xpath.evaluate(expression, doc);
        return result == null ? "" : result.trim();
    }

    private String xpathEvaluate(XPath xpath, org.w3c.dom.Node node, String expression) throws XPathExpressionException {
        String result = xpath.evaluate(expression, node);
        return result == null ? "" : result.trim();
    }

    private Set<String> xpathEvaluateMultiple(XPath xpath, Document doc, String expression) throws XPathExpressionException {
        NodeList nodes = (NodeList) xpath.evaluate(expression, doc, XPathConstants.NODESET);
        Set<String> results = new HashSet<>();
        if (nodes != null) {
            for (int i = 0; i < nodes.getLength(); i++) {
                String text = nodes.item(i).getNodeValue();
                if (StringUtils.isNotBlank(text)) {
                    results.add(text.trim());
                }
            }
        }
        return results;
    }

    private static class XmpNamespaceContext implements NamespaceContext {
        private final Map<String, String> prefixMap = new HashMap<>();

        public XmpNamespaceContext() {
            prefixMap.put("dc", "http://purl.org/dc/elements/1.1/");
            prefixMap.put("rdf", "http://www.w3.org/1999/02/22-rdf-syntax-ns#");
            prefixMap.put("xmp", "http://ns.adobe.com/xap/1.0/");
            prefixMap.put("xmpidq", "http://ns.adobe.com/xmp/Identifier/qual/1.0/");
            prefixMap.put("calibre", "http://calibre-ebook.com/xmp-namespace");
            prefixMap.put("calibreSI", "http://calibre-ebook.com/xmp-namespace/seriesIndex");
            prefixMap.put("booklore", "http://booklore.org/metadata/1.0/");
        }

        @Override
        public String getNamespaceURI(String prefix) {
            return prefixMap.getOrDefault(prefix, "");
        }

        @Override
        public String getPrefix(String namespaceURI) {
            for (Map.Entry<String, String> e : prefixMap.entrySet()) {
                if (e.getValue().equals(namespaceURI)) {
                    return e.getKey();
                }
            }
            return null;
        }

        @Override
        public Iterator<String> getPrefixes(String namespaceURI) {
            return prefixMap.keySet().iterator();
        }
    }
}
