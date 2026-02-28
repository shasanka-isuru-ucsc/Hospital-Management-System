package com.hms.finance.exception;

public class ResourceNotFoundException extends BusinessException {

    public ResourceNotFoundException(String message) {
        super("NOT_FOUND", message, 404);
    }
}
