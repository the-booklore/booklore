package org.booklore.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.FORBIDDEN)
public class OidcProvisioningException extends APIException {
    public OidcProvisioningException(String message) {
        super(message, HttpStatus.FORBIDDEN);
    }
}
