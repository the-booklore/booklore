package com.adityachandel.booklore.exception;

import org.springframework.http.HttpStatus;

public class ComicvineRateLimitException extends APIException {
    public ComicvineRateLimitException(String message) {
        super(message, HttpStatus.TOO_MANY_REQUESTS);
    }
}
