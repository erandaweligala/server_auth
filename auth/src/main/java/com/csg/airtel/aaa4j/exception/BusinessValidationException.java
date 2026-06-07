// New exception class
package com.csg.airtel.aaa4j.exception;

import jakarta.ws.rs.core.Response;

public class BusinessValidationException extends RuntimeException {
    private final String errorCode;
    private final String description;
    private final Response.Status httpStatus;

    public BusinessValidationException(String message, String errorCode, String description, Response.Status httpStatus) {
        super(message);
        this.errorCode = errorCode;
        this.description = description;
        this.httpStatus = httpStatus;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public Response.Status getHttpStatus() {
        return httpStatus;
    }

    public String getDescription() {
        return description;
    }
}