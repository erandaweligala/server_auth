package com.csg.airtel.aaa4j.domain.constant;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;

import static org.junit.jupiter.api.Assertions.*;

class ConstantsTest {

    @Test
    void testProtocolConstants() {
        assertEquals("PAP", Constants.PAP_PROTOCOL);
        assertEquals("CHAP", Constants.CHAP_PROTOCOL);
        assertEquals("MAC", Constants.MAC_PROTOCOL);
    }

    @Test
    void testChapConstants() {
        assertEquals(16, Constants.CHAP_CHALLENGE_LENGTH);
        assertEquals(256, Constants.CHAP_ID_MAX);
    }

    @Test
    void testMacPattern() {
        assertEquals("^[0-9A-F]{12}$", Constants.MAC_PATTERN);
    }

    @Test
    void testErrorConstants() {
        assertEquals("error", Constants.ERROR);
        assertEquals("code", Constants.CODE);
        assertEquals("description", Constants.DESCRIPTION);
        assertEquals("message", Constants.MESSAGE);
    }

    @Test
    void testOtherConstants() {
        assertEquals("CHAP authentication error for user: %s", Constants.CHAP_AUTH_ERROR);
        assertEquals("password", Constants.PASSWORD);
        assertEquals("status", Constants.STATUS);
        assertEquals("ACTIVE", Constants.STATUS_ACTIVE);
    }

    @Test
    void testMacPatternValidation() {
        String validMac = "001122334455";
        String invalidMac1 = "00112233445";  // Too short
        String invalidMac2 = "0011223344556"; // Too long
        String invalidMac3 = "00112233445G"; // Invalid character

        assertTrue(validMac.matches(Constants.MAC_PATTERN));
        assertFalse(invalidMac1.matches(Constants.MAC_PATTERN));
        assertFalse(invalidMac2.matches(Constants.MAC_PATTERN));
        assertFalse(invalidMac3.matches(Constants.MAC_PATTERN));
    }
}
