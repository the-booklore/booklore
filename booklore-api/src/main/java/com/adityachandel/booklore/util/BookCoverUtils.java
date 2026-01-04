package com.adityachandel.booklore.util;

import lombok.experimental.UtilityClass;

import java.time.Instant;
import java.util.Random;

@UtilityClass
public class BookCoverUtils {

    private static final String HASH_CHARS = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ";

    public static String generateCoverHash() {
        long timestamp = Instant.now().toEpochMilli();
        Random random = new Random(timestamp);
        StringBuilder hash = new StringBuilder(13);
        hash.append("BL-");
        for (int i = 0; i < 13; i++) {
            hash.append(HASH_CHARS.charAt(random.nextInt(HASH_CHARS.length())));
        }
        return hash.toString();
    }
}
