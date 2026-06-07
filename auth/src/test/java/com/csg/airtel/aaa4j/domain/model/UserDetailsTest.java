//package com.csg.airtel.aaa4j.domain.model;
//
//import org.junit.jupiter.api.Test;
//
//import java.util.HashMap;
//import java.util.Map;
//
//import static org.junit.jupiter.api.Assertions.*;
//
//class UserDetailsTest {
//
//    @Test
//    void testNoArgsConstructor() {
//        UserDetails userDetails = new UserDetails();
//
//        assertNull(userDetails.getUsername());
//        assertFalse(userDetails.getIsAuthorized());
//        assertFalse(userDetails.getIsActive());
//        assertNotNull(userDetails.getAttributes());
//        assertTrue(userDetails.getAttributes().isEmpty());
//    }
//
//    @Test
//    void testParameterizedConstructor() {
//        String username = "testuser";
//        boolean isAuthorized = true;
//        boolean isActive = true;
//        boolean isEnoughBalance = true;
//        Map<String, String> attributes = new HashMap<>();
//        attributes.put("role", "admin");
//        attributes.put("department", "IT");
//
//        UserDetails userDetails = new UserDetails(username, isAuthorized, isActive,isEnoughBalance, "", attributes);
//
//        assertEquals(username, userDetails.getUsername());
//        assertTrue(userDetails.getIsAuthorized());
//        assertTrue(userDetails.getIsActive());
//        assertEquals(attributes, userDetails.getAttributes());
//        assertEquals("admin", userDetails.getAttributes().get("role"));
//        assertEquals("IT", userDetails.getAttributes().get("department"));
//    }
//
//    @Test
//    void testSetters() {
//        UserDetails userDetails = new UserDetails();
//
//        String username = "newuser";
//        boolean isAuthorized = true;
//        boolean isActive = false;
//        Map<String, String> attributes = new HashMap<>();
//        attributes.put("level", "senior");
//
//        userDetails.setUsername(username);
//        userDetails.setIsAuthorized(isAuthorized);
//        userDetails.setIsActive(isActive);
//        userDetails.setAttributes(attributes);
//
//        assertEquals(username, userDetails.getUsername());
//        assertTrue(userDetails.getIsAuthorized());
//        assertFalse(userDetails.getIsActive());
//        assertEquals(attributes, userDetails.getAttributes());
//        assertEquals("senior", userDetails.getAttributes().get("level"));
//    }
//
//    @Test
//    void testNullAttributes() {
//        UserDetails userDetails = new UserDetails("user", true, true,true,"", null);
//
//        assertNotNull(userDetails.getAttributes());
//        assertTrue(userDetails.getAttributes().isEmpty());
//    }
//
//    @Test
//    void testSetNullAttributes() {
//        UserDetails userDetails = new UserDetails();
//        userDetails.setAttributes(null);
//
//        assertNotNull(userDetails.getAttributes());
//        assertTrue(userDetails.getAttributes().isEmpty());
//    }
//}
