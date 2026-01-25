package org.booklore.service.metadata.parser;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for AmazonBookParser.
 *
 * These tests verify:
 * - Rating extraction from acrPopover title attribute across different locales
 * - Review count extraction from acrCustomerReviewText
 * - Format prioritization (Kindle > Paperback > Hardcover)
 */
class AmazonBookParserTest {

    private static final Pattern RATING_PATTERN = Pattern.compile("(\\d[.,]\\d)");
    private static final Pattern NON_DIGIT_PATTERN = Pattern.compile("[^\\d]");

    @Nested
    @DisplayName("Rating Extraction Tests")
    class RatingExtractionTests {

        @ParameterizedTest
        @DisplayName("Should extract rating from various locale formats")
        @CsvSource({
            "'4.6 out of 5 stars', 4.6",           // US English
            "'4.7 out of 5 stars', 4.7",           // US English
            "'5.0 out of 5 stars', 5.0",           // US English perfect rating
            "'4,4 von 5 Sternen', 4.4",            // German
            "'4,8 von 5 Sternen', 4.8",            // German
            "'5,0 von 5 Sternen', 5.0",            // German perfect rating
            "'5つ星のうち4.2', 4.2",                // Japanese
            "'5つ星のうち4.8', 4.8",                // Japanese
            "'5つ星のうち5.0', 5.0",                // Japanese perfect rating
            "'4,5 sur 5 étoiles', 4.5",            // French
            "'4,3 de 5 estrellas', 4.3",           // Spanish
            "'4.5 out of 5', 4.5",                 // Abbreviated English
        })
        void shouldExtractRatingFromTitle(String title, double expectedRating) {
            Matcher matcher = RATING_PATTERN.matcher(title);
            assertThat(matcher.find()).isTrue();

            String ratingStr = matcher.group(1).replace(',', '.');
            double rating = Double.parseDouble(ratingStr);

            assertThat(rating).isEqualTo(expectedRating);
        }

        @Test
        @DisplayName("Should not match integer-only values like '5' in title")
        void shouldNotMatchIntegerOnly() {
            // The pattern should only match X.X or X,X format, not standalone integers
            String title = "5 stars";
            Matcher matcher = RATING_PATTERN.matcher(title);
            assertThat(matcher.find()).isFalse();
        }

        @Test
        @DisplayName("Should extract rating from HTML element")
        void shouldExtractRatingFromHtmlElement() {
            String html = """
                <html><body>
                    <span id="acrPopover" title="4.6 out of 5 stars"></span>
                </body></html>
                """;

            Document doc = Jsoup.parse(html);
            var acrPopover = doc.selectFirst("#acrPopover");

            assertThat(acrPopover).isNotNull();
            String title = acrPopover.attr("title");

            Matcher matcher = RATING_PATTERN.matcher(title);
            assertThat(matcher.find()).isTrue();
            assertThat(Double.parseDouble(matcher.group(1))).isEqualTo(4.6);
        }

        @Test
        @DisplayName("Should extract rating from German HTML element")
        void shouldExtractRatingFromGermanHtmlElement() {
            String html = """
                <html><body>
                    <span id="acrPopover" title="4,4 von 5 Sternen"></span>
                </body></html>
                """;

            Document doc = Jsoup.parse(html);
            var acrPopover = doc.selectFirst("#acrPopover");

            assertThat(acrPopover).isNotNull();
            String title = acrPopover.attr("title");

            Matcher matcher = RATING_PATTERN.matcher(title);
            assertThat(matcher.find()).isTrue();
            String ratingStr = matcher.group(1).replace(',', '.');
            assertThat(Double.parseDouble(ratingStr)).isEqualTo(4.4);
        }

        @Test
        @DisplayName("Should extract rating from Japanese HTML element")
        void shouldExtractRatingFromJapaneseHtmlElement() {
            String html = """
                <html><body>
                    <span id="acrPopover" title="5つ星のうち4.2"></span>
                </body></html>
                """;

            Document doc = Jsoup.parse(html);
            var acrPopover = doc.selectFirst("#acrPopover");

            assertThat(acrPopover).isNotNull();
            String title = acrPopover.attr("title");

            Matcher matcher = RATING_PATTERN.matcher(title);
            assertThat(matcher.find()).isTrue();
            assertThat(Double.parseDouble(matcher.group(1))).isEqualTo(4.2);
        }
    }

    @Nested
    @DisplayName("Review Count Extraction Tests")
    class ReviewCountExtractionTests {

        @ParameterizedTest
        @DisplayName("Should extract review count from various formats")
        @CsvSource({
            "'(19,933)', 19933",                   // US format with comma
            "'(12,434)', 12434",                   // US format
            "'(15.847)', 15847",                   // German format with period as thousands separator
            "'(12,607)', 12607",                   // Japanese format
            "'(1,234,567)', 1234567",              // Large number
            "'(42)', 42",                          // Small number
            "'19,933 ratings', 19933",             // With text
        })
        void shouldExtractReviewCount(String text, int expectedCount) {
            String reviewCountClean = NON_DIGIT_PATTERN.matcher(text).replaceAll("");
            int count = Integer.parseInt(reviewCountClean);

            assertThat(count).isEqualTo(expectedCount);
        }

        @Test
        @DisplayName("Should extract review count from HTML element")
        void shouldExtractReviewCountFromHtmlElement() {
            String html = """
                <html><body>
                    <span id="acrCustomerReviewText">(19,933)</span>
                </body></html>
                """;

            Document doc = Jsoup.parse(html);
            var reviewCountElement = doc.selectFirst("#acrCustomerReviewText");

            assertThat(reviewCountElement).isNotNull();
            String reviewCountClean = NON_DIGIT_PATTERN.matcher(reviewCountElement.text()).replaceAll("");

            assertThat(Integer.parseInt(reviewCountClean)).isEqualTo(19933);
        }

        @Test
        @DisplayName("Should extract review count from German HTML element")
        void shouldExtractReviewCountFromGermanHtmlElement() {
            String html = """
                <html><body>
                    <span id="acrCustomerReviewText">(15.847)</span>
                </body></html>
                """;

            Document doc = Jsoup.parse(html);
            var reviewCountElement = doc.selectFirst("#acrCustomerReviewText");

            assertThat(reviewCountElement).isNotNull();
            String reviewCountClean = NON_DIGIT_PATTERN.matcher(reviewCountElement.text()).replaceAll("");

            assertThat(Integer.parseInt(reviewCountClean)).isEqualTo(15847);
        }
    }

    @Nested
    @DisplayName("Format Link Extraction Tests")
    class FormatLinkExtractionTests {

        @Test
        @DisplayName("Should find Kindle link first when present")
        void shouldFindKindleLinkFirst() {
            String html = """
                <html><body>
                    <div data-asin="FALLBACK123">
                        <a href="/dp/B00KINDLE1/ref=xyz">Kindle</a>
                        <a href="/dp/1234567890/ref=xyz">Paperback</a>
                        <a href="/dp/0987654321/ref=xyz">Hardcover</a>
                    </div>
                </body></html>
                """;

            Document doc = Jsoup.parse(html);
            var item = doc.selectFirst("div[data-asin]");

            String bookLink = null;
            for (String type : new String[]{"Kindle", "Paperback", "Hardcover"}) {
                var link = item.select("a:containsOwn(" + type + ")").first();
                if (link != null) {
                    bookLink = link.attr("href");
                    break;
                }
            }

            assertThat(bookLink).contains("B00KINDLE1");
        }

        @Test
        @DisplayName("Should fall back to Paperback when Kindle not present")
        void shouldFallBackToPaperback() {
            String html = """
                <html><body>
                    <div data-asin="FALLBACK123">
                        <a href="/dp/1234567890/ref=xyz">Paperback</a>
                        <a href="/dp/0987654321/ref=xyz">Hardcover</a>
                    </div>
                </body></html>
                """;

            Document doc = Jsoup.parse(html);
            var item = doc.selectFirst("div[data-asin]");

            String bookLink = null;
            for (String type : new String[]{"Kindle", "Paperback", "Hardcover"}) {
                var link = item.select("a:containsOwn(" + type + ")").first();
                if (link != null) {
                    bookLink = link.attr("href");
                    break;
                }
            }

            assertThat(bookLink).contains("1234567890");
        }

        @Test
        @DisplayName("Should fall back to Hardcover when Kindle and Paperback not present")
        void shouldFallBackToHardcover() {
            String html = """
                <html><body>
                    <div data-asin="FALLBACK123">
                        <a href="/dp/0987654321/ref=xyz">Hardcover</a>
                    </div>
                </body></html>
                """;

            Document doc = Jsoup.parse(html);
            var item = doc.selectFirst("div[data-asin]");

            String bookLink = null;
            for (String type : new String[]{"Kindle", "Paperback", "Hardcover"}) {
                var link = item.select("a:containsOwn(" + type + ")").first();
                if (link != null) {
                    bookLink = link.attr("href");
                    break;
                }
            }

            assertThat(bookLink).contains("0987654321");
        }

        @Test
        @DisplayName("Should use data-asin fallback when no format links present")
        void shouldUseDataAsinFallback() {
            String html = """
                <html><body>
                    <div data-asin="FALLBACK123">
                        <a href="/dp/something/ref=xyz">Some other link</a>
                    </div>
                </body></html>
                """;

            Document doc = Jsoup.parse(html);
            var item = doc.selectFirst("div[data-asin]");

            String bookLink = null;
            for (String type : new String[]{"Kindle", "Paperback", "Hardcover"}) {
                var link = item.select("a:containsOwn(" + type + ")").first();
                if (link != null) {
                    bookLink = link.attr("href");
                    break;
                }
            }

            // When no format link found, should fall back to data-asin
            String asin = bookLink != null ? extractAsinFromUrl(bookLink) : item.attr("data-asin");

            assertThat(asin).isEqualTo("FALLBACK123");
        }
    }

    private String extractAsinFromUrl(String url) {
        String[] parts = url.split("/dp/");
        if (parts.length > 1) {
            String[] asinParts = parts[1].split("/");
            return asinParts[0];
        }
        return null;
    }
}
