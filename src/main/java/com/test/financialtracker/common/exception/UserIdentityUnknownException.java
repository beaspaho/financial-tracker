package com.test.financialtracker.common.exception;

import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.http.HttpStatus;

@EqualsAndHashCode(callSuper = true)
@Data
public class UserIdentityUnknownException extends RuntimeException {

    private final int statusCode;

    public UserIdentityUnknownException(String message) {
        this(message, HttpStatus.UNAUTHORIZED.value());
    }

    public UserIdentityUnknownException(String message, int statusCode) {
        super(message);
        this.statusCode = statusCode;
    }

}
