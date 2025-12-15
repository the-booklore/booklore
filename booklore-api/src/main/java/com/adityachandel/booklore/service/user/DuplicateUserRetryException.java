package com.adityachandel.booklore.service.user;

import com.adityachandel.booklore.exception.APIException;
import org.springframework.http.HttpStatus;

public class DuplicateUserRetryException extends APIException {
    public DuplicateUserRetryException(String username) {
        super("Duplicate user detected, retry needed: " + username, HttpStatus.CONFLICT);
    }
}
