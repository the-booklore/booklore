package com.adityachandel.booklore.util;

import java.util.regex.Pattern;

public class BookUtils {

    private static final Pattern WHITESPACE_PATTERN = Pattern.compile("\\s+");
    private static final Pattern SPECIAL_CHARACTERS_PATTERN = Pattern.compile("[.,\\-\\[\\]{}()!@#$%^&*_=+|~`<>?/\";:]");
    private static final Pattern PARENTHESES_WITH_OPTIONAL_SPACE_PATTERN = Pattern.compile("\\s?\\(.*?\\)");

    public static String cleanFileName(String fileName) {
        if (fileName == null) {
            return null;
        }
        fileName = fileName.replace("(Z-Library)", "").trim();
        fileName = PARENTHESES_WITH_OPTIONAL_SPACE_PATTERN.matcher(fileName).replaceAll("").trim(); // Remove the author name inside parentheses (e.g. (Jon Yablonski))
        int dotIndex = fileName.lastIndexOf('.'); // Remove the file extension (e.g., .pdf, .docx)
        if (dotIndex > 0) {
            fileName = fileName.substring(0, dotIndex).trim();
        }
        return fileName;
    }

    public static String cleanAndTruncateSearchTerm(String term) {
        term = SPECIAL_CHARACTERS_PATTERN.matcher(term).replaceAll("").trim();
        if (term.length() > 60) {
            String[] words = WHITESPACE_PATTERN.split(term);
            StringBuilder truncated = new StringBuilder();
            for (String word : words) {
                if (truncated.length() + word.length() + 1 > 60) break;
                if (!truncated.isEmpty()) truncated.append(" ");
                truncated.append(word);
            }
            term = truncated.toString();
        }
        return term;
    }
}
