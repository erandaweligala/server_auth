package com.csg.airtel.aaa4j.exception;

import com.csg.airtel.aaa4j.common.constant.ResponseCodeEnum;
import jakarta.ws.rs.core.Response;

/**
 * Custom exception for RADIUS client service errors.
 * Extends BaseException to provide specific error handling for RADIUS operations.
 */
public class RadiusClientServiceException extends BaseException {

    /**
     * Constructs a new RadiusClientServiceException.
     *
     * @param message        the error message
     * @param responseCode   the response code from ResponseCodeEnum
     * @param cause          the underlying cause (typically RadiusClientException)
     */
    public RadiusClientServiceException(String message, ResponseCodeEnum responseCode, Throwable cause) {
        super(
            message,
            "RADIUS_SERVICE",
            Response.Status.INTERNAL_SERVER_ERROR,
            responseCode.code(),
            cause != null ? cause.getStackTrace() : Thread.currentThread().getStackTrace()
        );
        if (cause != null) {
            initCause(cause);
        }
    }

    /**
     * Constructs a new RadiusClientServiceException without a cause.
     *
     * @param message        the error message
     * @param responseCode   the response code from ResponseCodeEnum
     */
    public RadiusClientServiceException(String message, ResponseCodeEnum responseCode) {
        this(message, responseCode, null);
    }
}
