package com.hms.reception.exception;

public class ResourceNotFoundException extends BusinessException {
    public ResourceNotFoundException(String message) {
        super("NOT_FOUND", message, 404);
    }
}
