package com.csg.airtel.aaa4j.domain.constant;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ResponseCodeEnumTest {

    @Test
    void testExceptionLayerCodes() {
        assertEquals("E1000", ResponseCodeEnum.EXCEPTION_CONTROLLER_LAYER.code());
        assertEquals("Exception Controller Layer Error", ResponseCodeEnum.EXCEPTION_CONTROLLER_LAYER.description());

        assertEquals("E1001", ResponseCodeEnum.EXCEPTION_SERVICE_LAYER.code());
        assertEquals("Exception Service Layer Error", ResponseCodeEnum.EXCEPTION_SERVICE_LAYER.description());

        assertEquals("E1002", ResponseCodeEnum.EXCEPTION_DATABASE_LAYER.code());
        assertEquals("Exception in Database Layer Error", ResponseCodeEnum.EXCEPTION_DATABASE_LAYER.description());
    }

    @Test
    void testAuthenticationErrorCodes() {
        assertEquals("E2001", ResponseCodeEnum.USER_NOT_FOUND.code());
        assertEquals("User Not Found", ResponseCodeEnum.USER_NOT_FOUND.description());

        assertEquals("E2002", ResponseCodeEnum.INVALID_CREDENTIALS.code());
        assertEquals("Invalid credentials", ResponseCodeEnum.INVALID_CREDENTIALS.description());

        assertEquals("E2003", ResponseCodeEnum.AUTHENTICATION_FAILED.code());
        assertEquals("Authentication failed", ResponseCodeEnum.AUTHENTICATION_FAILED.description());
    }

    @Test
    void testEnumValueOf() {
        assertEquals(ResponseCodeEnum.USER_NOT_FOUND, ResponseCodeEnum.valueOf("USER_NOT_FOUND"));
        assertEquals(ResponseCodeEnum.INVALID_CREDENTIALS, ResponseCodeEnum.valueOf("INVALID_CREDENTIALS"));
        assertEquals(ResponseCodeEnum.AUTHENTICATION_FAILED, ResponseCodeEnum.valueOf("AUTHENTICATION_FAILED"));
    }

    @Test
    void testEnumValueOfInvalid() {
        assertThrows(IllegalArgumentException.class, () -> ResponseCodeEnum.valueOf("INVALID_ENUM"));
    }

}
