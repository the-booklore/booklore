package com.adityachandel.booklore.service.metadata.parser;

import com.adityachandel.booklore.model.dto.Book;
import com.adityachandel.booklore.model.dto.BookMetadata;
import com.adityachandel.booklore.model.dto.request.FetchMetadataRequest;
import com.adityachandel.booklore.model.dto.settings.MetadataProviderSettings;
import com.adityachandel.booklore.model.enums.MetadataProvider;
import com.adityachandel.booklore.service.appsettings.AppSettingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class KoboParser implements BookParser {

    private static final String BASE_URL = "https://www.kobo.com";
    private static final String DEFAULT_COUNTRY = "us";
    private static final String DEFAULT_LANGUAGE = "en";
    private static final int DEFAULT_MAX_RESULTS = 5;
    private static final int MAX_SEARCH_PAGES = 3;
    private static final int TIMEOUT_MS = 15000;
    private static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36";
    private static final Pattern SERIES_INDEX_PATTERN = Pattern.compile("Book\\s+(\\d+(?:\\.\\d+)?)\\s*-");
    private static final Pattern KOBO_ID_PATTERN = Pattern.compile("/ebook/([^/?#]+)");

    private final AppSettingService appSettingService;

    @Override
    public List<BookMetadata> fetchMetadata(Book book, FetchMetadataRequest fetchMetadataRequest) {
        MetadataProviderSettings.Kobo koboSettings = Optional.ofNullable(appSettingService.getAppSettings().getMetadataProviderSettings())
                .map(MetadataProviderSettings::getKobo)
                .orElse(null);

        if (koboSettings == null || !koboSettings.isEnabled()) {
            log.info("Kobo provider is disabled or not configured.");
            return List.of();
        }

        String query = buildQuery(fetchMetadataRequest);
        if (!StringUtils.hasText(query)) {
            log.warn("No query available for Kobo metadata fetch.");
            return List.of();
        }

        String country = defaultIfBlank(koboSettings.getCountry(), DEFAULT_COUNTRY);
        String language = defaultIfBlank(koboSettings.getLanguage(), DEFAULT_LANGUAGE);
        int maxResults = Optional.ofNullable(koboSettings.getMaxResults()).filter(v -> v > 0).orElse(DEFAULT_MAX_RESULTS);
        boolean resizeCover = Boolean.TRUE.equals(koboSettings.getResizeCover());

        try {
            List<String> bookUrls = performQuery(query, country, language, maxResults);
            List<BookMetadata> results = new ArrayList<>();
            for (String url : bookUrls) {
                Document doc = fetchDocument(url);
                if (doc == null) {
                    continue;
                }
                BookMetadata metadata = parseBookPage(url, doc, resizeCover);
                if (metadata != null) {
                    results.add(metadata);
                }
            }
            log.info("Found {} Kobo metadata results for query {}", results.size(), query);
            return results;
        } catch (Exception e) {
            log.error("Error fetching Kobo metadata for query {}", query, e);
            return List.of();
        }
    }

    @Override
    public BookMetadata fetchTopMetadata(Book book, FetchMetadataRequest fetchMetadataRequest) {
        List<BookMetadata> results = fetchMetadata(book, fetchMetadataRequest);
        return results.isEmpty() ? null : results.get(0);
    }

    private String buildQuery(FetchMetadataRequest request) {
        if (StringUtils.hasText(request.getIsbn())) {
            return request.getIsbn().trim();
        }
        if (StringUtils.hasText(request.getTitle())) {
            if (StringUtils.hasText(request.getAuthor())) {
                return (request.getTitle().trim() + " " + request.getAuthor().trim()).strip();
            }
            return request.getTitle().trim();
        }
        return "";
    }

    private List<String> performQuery(String query, String country, String language, int maxResults) {
        List<String> results = new ArrayList<>();
        int page = 1;

        while (results.size() < maxResults && page <= MAX_SEARCH_PAGES) {
            String searchUrl = buildSearchUrl(query, page, country, language);
            Document doc = fetchDocument(searchUrl);
            if (doc == null) {
                break;
            }

            if (isBookPage(doc)) {
                results.add(doc.location());
                break;
            }

            results.addAll(parseSearchResults(doc));
            if (results.size() >= maxResults) {
                break;
            }
            page++;
        }

        return results.stream().map(this::ensureAbsoluteUrl).distinct().limit(maxResults).toList();
    }

    private String buildSearchUrl(String query, int pageNumber, String country, String language) {
        String encodedQuery = URLEncoder.encode(query, StandardCharsets.UTF_8);
        StringBuilder sb = new StringBuilder(BASE_URL)
                .append("/")
                .append(country.toLowerCase(Locale.ROOT))
                .append("/")
                .append(language.toLowerCase(Locale.ROOT))
                .append("/search?query=")
                .append(encodedQuery)
                .append("&fcmedia=Book")
                .append("&pageNumber=")
                .append(pageNumber);

        if (!"all".equalsIgnoreCase(language)) {
            sb.append("&fclanguages=").append(language);
        }
        return sb.toString();
    }

    private Document fetchDocument(String url) {
        try {
            return Jsoup.connect(url)
                    .userAgent(USER_AGENT)
                    .referrer(BASE_URL)
                    .timeout(TIMEOUT_MS)
                    .followRedirects(true)
                    .get();
        } catch (Exception e) {
            log.warn("Failed to fetch Kobo page: {}", url, e);
            return null;
        }
    }

    private boolean isBookPage(Document doc) {
        return !doc.select("h1.title.product-field").isEmpty();
    }

    private List<String> parseSearchResults(Document doc) {
        Elements newSearchLinks = doc.select("div[data-testid='search-result-widget'] a[data-testid='title']");
        if (!newSearchLinks.isEmpty()) {
            return newSearchLinks.stream()
                    .map(element -> element.attr("href"))
                    .filter(StringUtils::hasText)
                    .toList();
        }

        Elements legacyLinks = doc.select("h2.title.product-field > a");
        if (!legacyLinks.isEmpty()) {
            return legacyLinks.stream()
                    .map(element -> element.attr("href"))
                    .filter(StringUtils::hasText)
                    .toList();
        }

        log.debug("No Kobo search results found on page {}", doc.location());
        return List.of();
    }

    private BookMetadata parseBookPage(String pageUrl, Document doc, boolean resizeCover) {
        Element titleEl = doc.selectFirst("h1.title.product-field");
        if (titleEl == null) {
            return null;
        }

        BookMetadata metadata = new BookMetadata();
        metadata.setProvider(MetadataProvider.Kobo);
        metadata.setKoboId(extractKoboId(pageUrl));
        metadata.setTitle(cleanText(titleEl.text()));

        Set<String> authors = doc.select("span.visible-contributors > a").stream()
                .map(Element::text)
                .map(this::cleanText)
                .filter(StringUtils::hasText)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (!authors.isEmpty()) {
            metadata.setAuthors(authors);
        }

        parseSeries(doc, metadata);
        parseDetailsSection(doc, metadata);
        parseTags(doc, metadata);
        parseSynopsis(doc, metadata);

        String coverUrl = parseCoverUrl(doc, resizeCover);
        if (StringUtils.hasText(coverUrl)) {
            metadata.setThumbnailUrl(coverUrl);
        }

        return metadata;
    }

    private void parseSeries(Document doc, BookMetadata metadata) {
        Elements seriesElements = doc.select("span.series.product-field");
        if (seriesElements.isEmpty()) {
            return;
        }

        Element lastSeries = seriesElements.last();
        if (lastSeries == null) {
            return;
        }

        Element seriesNameEl = lastSeries.selectFirst("span.product-sequence-field > a");
        if (seriesNameEl != null) {
            metadata.setSeriesName(cleanText(seriesNameEl.text()));
        }

        Element seriesIndexEl = lastSeries.selectFirst("span.sequenced-name-prefix");
        if (seriesIndexEl != null) {
            Matcher matcher = SERIES_INDEX_PATTERN.matcher(seriesIndexEl.text());
            if (matcher.find()) {
                try {
                    metadata.setSeriesNumber(Float.parseFloat(matcher.group(1)));
                } catch (NumberFormatException ignored) {
                    log.debug("Unable to parse series index: {}", seriesIndexEl.text());
                }
            }
        }
    }

    private void parseDetailsSection(Document doc, BookMetadata metadata) {
        Elements details = doc.select("div.bookitem-secondary-metadata > ul > li");
        if (details.isEmpty()) {
            return;
        }

        Element publisherEl = details.getFirst();
        if (publisherEl != null) {
            metadata.setPublisher(cleanText(publisherEl.text()));
        }

        for (int i = 1; i < details.size(); i++) {
            Element detail = details.get(i);
            String descriptor = cleanText(detail.ownText());
            Element valueEl = detail.selectFirst("span");
            String value = valueEl != null ? cleanText(valueEl.text()) : "";

            switch (descriptor) {
                case "Release Date:" -> metadata.setPublishedDate(parseDate(value));
                case "ISBN:", "Book ID:" -> {
                    String isbn = ParserUtils.cleanIsbn(value);
                    if (StringUtils.hasText(isbn)) {
                        if (isbn.length() == 13) metadata.setIsbn13(isbn);
                        else if (isbn.length() == 10) metadata.setIsbn10(isbn);
                    } else {
                        metadata.setKoboId(defaultIfBlank(metadata.getKoboId(), value));
                    }
                }
                case "Language:" -> metadata.setLanguage(normalizeLanguage(value));
                default -> {
                }
            }
        }
    }

    private void parseTags(Document doc, BookMetadata metadata) {
        Set<String> tags = doc.select("ul.category-rankings meta[property='genre']").stream()
                .map(meta -> cleanText(meta.attr("content")))
                .filter(StringUtils::hasText)
                .map(tag -> tag.replace(", ", " "))
                .collect(Collectors.toCollection(LinkedHashSet::new));

        if (!tags.isEmpty()) {
            metadata.setCategories(tags);
            metadata.setTags(tags);
        }
    }

    private void parseSynopsis(Document doc, BookMetadata metadata) {
        Element synopsisEl = doc.selectFirst("div[data-full-synopsis='']");
        if (synopsisEl != null) {
            metadata.setDescription(cleanText(synopsisEl.text()));
        }
    }

    private String parseCoverUrl(Document doc, boolean resizeCover) {
        Element coverEl = doc.selectFirst("img.cover-image");
        if (coverEl == null) {
            return null;
        }

        String src = coverEl.attr("src");
        if (!StringUtils.hasText(src)) {
            return null;
        }

        String url = src.startsWith("http") ? src : "https:" + src;
        if (resizeCover) {
            return url.replace("353/569/90", "1650/2200/100");
        }
        return url.replace("353/569/90/False/", "");
    }

    private LocalDate parseDate(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }

        List<DateTimeFormatter> formatters = List.of(
                DateTimeFormatter.ofPattern("MMMM d, yyyy", Locale.ENGLISH),
                DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.ENGLISH),
                DateTimeFormatter.ISO_LOCAL_DATE
        );

        for (DateTimeFormatter formatter : formatters) {
            try {
                return LocalDate.parse(value, formatter);
            } catch (DateTimeParseException ignored) {
            }
        }

        log.debug("Unable to parse published date: {}", value);
        return null;
    }

    private String normalizeLanguage(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }

        String normalized = value.trim().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "english" -> "en";
            case "french", "français" -> "fr";
            case "german", "deutsch" -> "de";
            case "spanish", "español" -> "es";
            case "italian", "italiano" -> "it";
            case "dutch", "nederlands" -> "nl";
            case "polish" -> "pl";
            case "portuguese", "português" -> "pt";
            case "japanese" -> "ja";
            default -> normalized;
        };
    }

    private String extractKoboId(String url) {
        if (!StringUtils.hasText(url)) {
            return null;
        }
        Matcher matcher = KOBO_ID_PATTERN.matcher(url);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return null;
    }

    private String ensureAbsoluteUrl(String url) {
        if (!StringUtils.hasText(url)) {
            return url;
        }
        if (url.startsWith("http")) {
            return url;
        }
        if (!url.startsWith("/")) {
            url = "/" + url;
        }
        return BASE_URL + url;
    }

    private String cleanText(String text) {
        return text == null ? null : text.trim();
    }

    private String defaultIfBlank(String value, String defaultValue) {
        return StringUtils.hasText(value) ? value.trim() : defaultValue;
    }
}
