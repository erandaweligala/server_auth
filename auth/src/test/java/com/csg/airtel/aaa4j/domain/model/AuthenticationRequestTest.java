package com.csg.airtel.aaa4j.domain.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AuthenticationRequestTest {

    @Test
    void testConstructorAndGetters() {
        String username = "testuser";
        String password = "password123";
        String chapChallenge = "challenge";
        String chapPassword = "chappass";
        String nasIpAddress = "192.168.1.100";
        String framedProtocol = "1";

        AuthenticationRequest request = new AuthenticationRequest(username, password, chapChallenge, chapPassword, nasIpAddress, framedProtocol);

        assertEquals(username, request.getUsername());
        assertEquals(password, request.getPassword());
        assertEquals(chapChallenge, request.getChapChallenge());
        assertEquals(chapPassword, request.getChapPassword());
    }

    @Test
    void testSetters() {
        AuthenticationRequest request = new AuthenticationRequest("", "", "", "", "", "");

        String newUsername = "newuser";
        String newPassword = "newpass";
        String newChapChallenge = "newchallenge";
        String newChapPassword = "newchappass";

        request.setUsername(newUsername);
        request.setPassword(newPassword);
        request.setChapChallenge(newChapChallenge);
        request.setChapPassword(newChapPassword);

        assertEquals(newUsername, request.getUsername());
        assertEquals(newPassword, request.getPassword());
        assertEquals(newChapChallenge, request.getChapChallenge());
        assertEquals(newChapPassword, request.getChapPassword());
    }

    @Test
    void testNullValues() {
        AuthenticationRequest request = new AuthenticationRequest(null, null, null, null, null, "");

        assertNull(request.getUsername());
        assertNull(request.getPassword());
        assertNull(request.getChapChallenge());
        assertNull(request.getChapPassword());
    }
}
