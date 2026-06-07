package com.csg.airtel.aaa4j.exception;

import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class BaseExceptionTest {

    @Test
    void testBaseExceptionConstructor() {
        String message = "Test error message";
        String description = "SERVICE_LAYER";
        Response.Status httpStatus = Response.Status.BAD_REQUEST;
        String responseCode = "ERR_001";
        StackTraceElement[] stackTrace = new StackTraceElement[]{
                new StackTraceElement("TestClass", "testMethod", "TestClass.java", 10)
        };

        BaseException exception = new BaseException(message, description, httpStatus, responseCode, stackTrace);

        assertEquals(message, exception.getMessage());
        assertEquals(description, exception.getDescription());
        assertEquals(httpStatus, exception.getHttpStatus());
        assertEquals(responseCode, exception.getResponseCode());
        assertArrayEquals(stackTrace, exception.getStackTraceElements());
    }

    @Test
    void testBaseExceptionWithNullValues() {
        BaseException exception = new BaseException(null, null, null, null, null);

        assertNull(exception.getMessage());
        assertNull(exception.getDescription());
        assertNull(exception.getHttpStatus());
        assertNull(exception.getResponseCode());
        assertNull(exception.getStackTraceElements());
    }

    @Test
    void testBaseExceptionToString() {
        String message = "Test error";
        String description = "CONTROLLER_LAYER";
        Response.Status httpStatus = Response.Status.INTERNAL_SERVER_ERROR;
        String responseCode = "ERR_500";
        StackTraceElement[] stackTrace = new StackTraceElement[]{
                new StackTraceElement("TestClass", "testMethod", "TestClass.java", 20)
        };

        BaseException exception = new BaseException(message, description, httpStatus, responseCode, stackTrace);
        String result = exception.toString();

        assertTrue(result.contains("BaseException"));
        assertTrue(result.contains(message));
        assertTrue(result.contains(description));
        assertTrue(result.contains(httpStatus.toString()));
        assertTrue(result.contains(responseCode));
    }

    @Test
    void testBaseExceptionWithEmptyStackTrace() {
        BaseException exception = new BaseException(
                "Error message",
                "SERVICE",
                Response.Status.NOT_FOUND,
                "ERR_404",
                new StackTraceElement[0]
        );

        assertNotNull(exception.getStackTraceElements());
        assertEquals(0, exception.getStackTraceElements().length);
    }

    @Test
    void testBaseExceptionInheritance() {
        BaseException exception = new BaseException(
                "Test",
                "TEST",
                Response.Status.OK,
                "OK_200",
                new StackTraceElement[0]
        );

        assertTrue(exception instanceof RuntimeException);
        assertTrue(exception instanceof Exception);
    }
}
