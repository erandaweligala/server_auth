package com.csg.airtel.aaa4j.domain.model;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class AuthenticationDbDetailsTest {

    @Test
    void testConstructorAndGetters() {
        Map<String, String> attributes = new HashMap<>();
        attributes.put("key1", "value1");

        BucketDetails newService = new BucketDetails("rule", 1L, "bucketId", 100L, "10-12", null, null, null, 1);
        List<BucketDetails> bucketDetailsList = List.of(newService);

        AuthenticationDbDetails details = new AuthenticationDbDetails(
                "testuser", "password", "active", "192.168.1.1", 0, attributes, bucketDetailsList);

        assertEquals("testuser", details.getUserName());
        assertEquals("password", details.getPassword());
        assertEquals("active", details.getStatus());
        assertEquals("192.168.1.1", details.getNasIpAddress());
        assertEquals(attributes, details.getAttributes());
        assertEquals(bucketDetailsList, details.getBucketDetails());
    }

    @Test
    void testSetters() {
        AuthenticationDbDetails details = new AuthenticationDbDetails();

        details.setUserName("newuser");
        details.setPassword("newpassword");
        details.setStatus("inactive");
        details.setNasIpAddress("10.0.0.1");

        Map<String, String> newAttributes = new HashMap<>();
        newAttributes.put("key2", "value2");
        details.setAttributes(newAttributes);

        BucketDetails newService = new BucketDetails("rule", 1L, "bucketId", 100L, "10-12", null, null, null, 1);
        details.setBucketDetails(List.of(newService));

        assertEquals("newuser", details.getUserName());
        assertEquals("newpassword", details.getPassword());
        assertEquals("inactive", details.getStatus());
        assertEquals("10.0.0.1", details.getNasIpAddress());
        assertEquals(newAttributes, details.getAttributes());
        assertEquals(1, details.getBucketDetails().size());
    }
}
