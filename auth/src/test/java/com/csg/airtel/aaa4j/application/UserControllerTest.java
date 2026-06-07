package com.csg.airtel.aaa4j.application;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class UserControllerTest {

    private UserController userController;

    @BeforeEach
    void setUp() {
        userController = new UserController();
    }

    @Test
    void testGetUserData() {
        Boolean result = userController.getUserData("testuser");
        assertTrue(result);
    }

    @Test
    void testFallbackResponse() {
        Boolean result = userController.fallbackResponse("testuser");
        assertFalse(result);
    }
}
