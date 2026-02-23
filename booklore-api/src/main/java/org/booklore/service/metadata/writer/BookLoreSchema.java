package org.booklore.service.metadata.writer;

import org.apache.xmpbox.XMPMetadata;
import org.apache.xmpbox.schema.XMPSchema;

import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

public class BookLoreSchema extends XMPSchema {

    public static final String NAMESPACE = "http://booklore.org/metadata/1.0/";
    public static final String PREFIX = "booklore";

    public static final String SUBTITLE = "Subtitle";
    public static final String MOODS = "Moods";
    public static final String TAGS = "Tags";
    public static final String RATING = "Rating";
    public static final String AMAZON_RATING = "AmazonRating";
    public static final String GOODREADS_RATING = "GoodreadsRating";
    public static final String HARDCOVER_RATING = "HardcoverRating";
    public static final String LUBIMYCZYTAC_RATING = "LubimyczytacRating";
    public static final String RANOBEDB_RATING = "RanobedbRating";
    public static final String LUBIMYCZYTAC_ID = "LubimyczytacId";
    public static final String HARDCOVER_BOOK_ID = "HardcoverBookId";
    public static final String ISBN_10 = "ISBN10";

    public BookLoreSchema(XMPMetadata metadata) {
        super(metadata, PREFIX, NAMESPACE);
    }


    public void setSubtitle(String subtitle) {
        if (subtitle != null && !subtitle.isBlank()) {
            setTextPropertyValue(SUBTITLE, subtitle);
        }
    }

    public void setIsbn10(String isbn10) {
        if (isbn10 != null && !isbn10.isBlank()) {
            setTextPropertyValue(ISBN_10, isbn10);
        }
    }

    public void setLubimyczytacId(String id) {
        if (id != null && !id.isBlank()) {
            setTextPropertyValue(LUBIMYCZYTAC_ID, id);
        }
    }

    public void setHardcoverBookId(Integer id) {
        if (id != null) {
            setTextPropertyValue(HARDCOVER_BOOK_ID, id.toString());
        }
    }


    public void setRating(Double rating) {
        if (rating != null) {
            setTextPropertyValue(RATING, String.format(Locale.US, "%.2f", rating));
        }
    }

    public void setAmazonRating(Double rating) {
        if (rating != null) {
            setTextPropertyValue(AMAZON_RATING, String.format(Locale.US, "%.2f", rating));
        }
    }

    public void setGoodreadsRating(Double rating) {
        if (rating != null) {
            setTextPropertyValue(GOODREADS_RATING, String.format(Locale.US, "%.2f", rating));
        }
    }

    public void setHardcoverRating(Double rating) {
        if (rating != null) {
            setTextPropertyValue(HARDCOVER_RATING, String.format(Locale.US, "%.2f", rating));
        }
    }

    public void setLubimyczytacRating(Double rating) {
        if (rating != null) {
            setTextPropertyValue(LUBIMYCZYTAC_RATING, String.format(Locale.US, "%.2f", rating));
        }
    }

    public void setRanobedbRating(Double rating) {
        if (rating != null) {
            setTextPropertyValue(RANOBEDB_RATING, String.format(Locale.US, "%.2f", rating));
        }
    }

    public void setMoods(Set<String> moods) {
        if (moods == null || moods.isEmpty()) {
            return;
        }
        String joined = moods.stream()
                .filter(m -> m != null && !m.isBlank())
                .collect(Collectors.joining("; "));
        if (!joined.isEmpty()) {
            setTextPropertyValue(MOODS, joined);
        }
    }

    public void setTags(Set<String> tags) {
        if (tags == null || tags.isEmpty()) {
            return;
        }
        String joined = tags.stream()
                .filter(t -> t != null && !t.isBlank())
                .collect(Collectors.joining("; "));
        if (!joined.isEmpty()) {
            setTextPropertyValue(TAGS, joined);
        }
    }
}

