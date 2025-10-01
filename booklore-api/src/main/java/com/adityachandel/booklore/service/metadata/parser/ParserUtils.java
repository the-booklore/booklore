package com.adityachandel.booklore.service.metadata.parser;

public class ParserUtils {

    public static String cleanIsbn(String isbn) {
        if (isbn == null) return null;
        return isbn.replaceAll("[^0-9]", "");
    }
}
