package com.adityachandel.booklore.util;

import java.util.regex.Pattern;

public class BookUtils {

    private static final Pattern WHITESPACE_PATTERN = Pattern.compile("\\s+");
    private static final Pattern SPECIAL_CHARACTERS_PATTERN = Pattern.compile("[.,\\-\\[\\]{}()!@#$%^&*_=+|~`<>?/\";:]");
    private static final Pattern PARENTHESES_WITH_OPTIONAL_SPACE_PATTERN = Pattern.compile("\\s?\\(.*?\\)");

    public static String cleanFileName(String fileName) {
        String name = fileName;
        if (name == null) {
            return null;
        }
        name = name.replace("(Z-Library)", "").trim();
        name = PARENTHESES_WITH_OPTIONAL_SPACE_PATTERN.matcher(name).replaceAll("").trim(); // Remove the author name inside parentheses (e.g. (Jon Yablonski))
        int dotIndex = name.lastIndexOf('.'); // Remove the file extension (e.g., .pdf, .docx)
        if (dotIndex > 0) {
            name = name.substring(0, dotIndex).trim();
        }
        return name;
    }

    public static String cleanAndTruncateSearchTerm(String term) {
        String s = term;
        s = SPECIAL_CHARACTERS_PATTERN.matcher(s).replaceAll("").trim();
        if (s.length() > 60) {
            String[] words = WHITESPACE_PATTERN.split(s);
            StringBuilder truncated = new StringBuilder();
            for (String word : words) {
                if (truncated.length() + word.length() + 1 > 60) break;
                if (!truncated.isEmpty()) truncated.append(" ");
                truncated.append(word);
            }
            s = truncated.toString();
        }
        return s;
    }
}
