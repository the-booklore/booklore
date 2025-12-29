package com.adityachandel.booklore.util;

import com.adityachandel.booklore.model.entity.AuthorEntity;
import com.adityachandel.booklore.model.entity.BookMetadataEntity;
import lombok.experimental.UtilityClass;

import java.util.regex.Pattern;

@UtilityClass
public class BookUtils {

    private static final Pattern WHITESPACE_PATTERN = Pattern.compile("\\s+");
    private static final Pattern SPECIAL_CHARACTERS_PATTERN = Pattern.compile("[!@$%^&*_=|~`<>?/\"]");
    private static final Pattern DIACRITICAL_MARKS_PATTERN = Pattern.compile("\\p{InCombiningDiacriticalMarks}+");
    private static final Pattern PARENTHESIS_PATTERN = Pattern.compile("\\s?\\([^()]*\\)");

    public static String buildSearchText(BookMetadataEntity e) {
        if (e == null) return null;
        
        StringBuilder sb = new StringBuilder(256);
        if (e.getTitle() != null) sb.append(e.getTitle()).append(" ");
        if (e.getSubtitle() != null) sb.append(e.getSubtitle()).append(" ");
        if (e.getSeriesName() != null) sb.append(e.getSeriesName()).append(" ");
        
        try {
            if (e.getAuthors() != null) {
                for (AuthorEntity author : e.getAuthors()) {
                    if (author != null && author.getName() != null) {
                        sb.append(author.getName()).append(" ");
                    }
                }
            }
        } catch (Exception ex) {
            // LazyInitializationException or similar - authors won't be included in search text
        }
        
        return normalizeForSearch(sb.toString().trim());
    }

    public static String normalizeForSearch(String term) {
        if (term == null) {
            return null;
        }
        String s = java.text.Normalizer.normalize(term, java.text.Normalizer.Form.NFD);
        s = DIACRITICAL_MARKS_PATTERN.matcher(s).replaceAll("");
        s = s.replace("ø", "o").replace("Ø", "O")
                .replace("ł", "l").replace("Ł", "L")
                .replace("æ", "ae").replace("Æ", "AE")
                .replace("œ", "oe").replace("Œ", "OE")
                .replace("ß", "ss");
        
        // Use cleanSearchTerm instead of cleanAndTruncateSearchTerm
        s = cleanSearchTerm(s);
        return s.toLowerCase();
    }

    public static String cleanFileName(String fileName) {
        String name = fileName;
        if (name == null) {
            return null;
        }
        name = name.replace("(Z-Library)", "").trim();
        
        String previous;
        do {
            previous = name;
            name = PARENTHESIS_PATTERN.matcher(name).replaceAll("").trim();
        } while (!name.equals(previous));
        
        int dotIndex = name.lastIndexOf('.'); // Remove the file extension (e.g., .pdf, .docx)
        if (dotIndex > 0) {
            name = name.substring(0, dotIndex).trim();
        }
        
        name = WHITESPACE_PATTERN.matcher(name).replaceAll(" ").trim();
        
        return name;
    }

    public static String cleanSearchTerm(String term) {
        if (term == null) {
            return "";
        }
        String s = term;
        s = SPECIAL_CHARACTERS_PATTERN.matcher(s).replaceAll("").trim();
        s = WHITESPACE_PATTERN.matcher(s).replaceAll(" ");
        return s;
    }

    public static String cleanAndTruncateSearchTerm(String term) {
        String s = cleanSearchTerm(term);
        if (s.length() > 60) {
            String[] words = WHITESPACE_PATTERN.split(s);
            if (words.length > 1) {
                StringBuilder truncated = new StringBuilder(64);
                for (String word : words) {
                    if (truncated.length() + word.length() + 1 > 60) break;
                    if (!truncated.isEmpty()) truncated.append(" ");
                    truncated.append(word);
                }
                s = truncated.toString();
            } else {
                s = s.substring(0, Math.min(60, s.length()));
            }
        }
        return s;
    }
}
