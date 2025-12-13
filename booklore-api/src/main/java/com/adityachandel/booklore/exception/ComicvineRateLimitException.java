package com.adityachandel.booklore.exception;

public class ComicvineRateLimitException extends RuntimeException {
    public ComicvineRateLimitException(String message) {
        super(message);
    }
}
