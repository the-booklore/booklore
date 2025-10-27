package com.adityachandel.booklore.service.metadata.parser;

import java.util.regex.Pattern;

public class ParserUtils {

    private static final Pattern NON_DIGIT_PATTERN = Pattern.compile("[^0-9]");

    public static String cleanIsbn(String isbn) {
        if (isbn == null) return null;
        return NON_DIGIT_PATTERN.matcher(isbn).replaceAll("");
    }
}
