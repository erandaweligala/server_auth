package com.csg.airtel.aaa4j.domain.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

class VendorAttributeFormatterTest {

    private VendorAttributeFormatter formatter;

    @BeforeEach
    void setUp() {
        formatter = new VendorAttributeFormatter();
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   "})
    @DisplayName("Should return raw value when rawValue is empty or blank")
    void formatValue_emptyRawValue(String rawValue) {
        String result = formatter.formatValue("prefix:", rawValue);
        assertEquals(rawValue, result);
    }

    @Test
    @DisplayName("Should return null when rawValue is null")
    void formatValue_nullRawValue() {
        String result = formatter.formatValue("prefix:", null);
        assertNull(result);
    }

    @Test
    @DisplayName("Should return raw value when prefix is null")
    void formatValue_nullPrefix() {
        String rawValue = "12345";
        String result = formatter.formatValue(null, rawValue);
        assertEquals(rawValue, result);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   "})
    @DisplayName("Should return raw value when prefix is empty or blank")
    void formatValue_emptyPrefix(String prefix) {
        String rawValue = "12345";
        String result = formatter.formatValue(prefix, rawValue);
        assertEquals(rawValue, result);
    }

    @Test
    @DisplayName("Should return raw value as-is if it already contains '='")
    void formatValue_alreadyContainsEqual() {
        String prefix = "subscriber:sa=";
        String rawValue = "existing=value";
        String result = formatter.formatValue(prefix, rawValue);
        assertEquals(rawValue, result);
    }

    @Test
    @DisplayName("Should successfully append prefix to raw value")
    void formatValue_success() {
        String prefix = "subscriber:sa=";
        String rawValue = "gold_plan";
        String expected = "subscriber:sa=gold_plan";

        String result = formatter.formatValue(prefix, rawValue);

        assertEquals(expected, result);
    }
}