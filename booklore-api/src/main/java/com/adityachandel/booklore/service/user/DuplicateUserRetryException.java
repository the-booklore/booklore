package com.adityachandel.booklore.service.user;

public class DuplicateUserRetryException extends RuntimeException {
    public DuplicateUserRetryException(String username) {
        super("Duplicate user detected, retry needed: " + username);
    }
}
