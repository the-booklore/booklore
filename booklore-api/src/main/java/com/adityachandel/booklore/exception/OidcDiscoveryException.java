package com.adityachandel.booklore.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.BAD_GATEWAY)
public class OidcDiscoveryException extends APIException {
    public OidcDiscoveryException(String message) {
        super(message, HttpStatus.BAD_GATEWAY);
    }

    public OidcDiscoveryException(String message, Throwable cause) {
        super(message, HttpStatus.BAD_GATEWAY);
        initCause(cause);
    }
}
