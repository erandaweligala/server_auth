package com.csg.airtel.aaa4j.common.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

class IdentifierUtilTest {

    @Test
    void testNormalizeMacAddressWithColons() {
        String input = "00:11:22:33:44:55";
        String expected = "001122334455";
        String result = IdentifierUtil.normalizeIdentifier(input);
        assertEquals(expected, result);
    }

    @ParameterizedTest(name = "Case {index}: input=''{0}'' → expected=''{1}''")
    @CsvSource({
            // MAC address formats
            "'00-11-22-33-44-55', '001122334455'",
            "'00:11-22:33-44:55', '001122334455'",
            "'001122334455', '001122334455'",

            // Usernames and lowercase normalization
            "'user123', 'USER123'",
            "'abcdef123456', 'ABCDEF123456'",

            // Edge cases
            "'', ''",
            "':::', ''",
            "'---', ''"
    })
    void testNormalizeIdentifier(String input, String expected) {
        String result = IdentifierUtil.normalizeIdentifier(input);
        assertEquals(expected, result,
                () -> "Expected normalized form of '" + input + "' to be '" + expected + "' but got '" + result + "'");
    }

    @Test
    void testNormalizeNullInput() {
        String result = IdentifierUtil.normalizeIdentifier(null);
        assertNull(result);
    }

        // ==================== MAC Address Tests ====================

        @Test
        @DisplayName("Should return true for valid 12-digit hex MAC address without separators")
        void testIsMacAddress_ValidHex12Digits_ReturnsTrue() {
            assertTrue(IdentifierUtil.isMacAddress("001122334455"));
            assertTrue(IdentifierUtil.isMacAddress("AABBCCDDEEFF"));
            assertTrue(IdentifierUtil.isMacAddress("aabbccddeeff"));
            assertTrue(IdentifierUtil.isMacAddress("123456789ABC"));
            assertTrue(IdentifierUtil.isMacAddress("000000000000"));
            assertTrue(IdentifierUtil.isMacAddress("FFFFFFFFFFFF"));
        }

        @Test
        @DisplayName("Should return true for valid MAC address with colon separators")
        void testIsMacAddress_ValidWithColonSeparators_ReturnsTrue() {
            assertTrue(IdentifierUtil.isMacAddress("00:11:22:33:44:55"));
            assertTrue(IdentifierUtil.isMacAddress("AA:BB:CC:DD:EE:FF"));
            assertTrue(IdentifierUtil.isMacAddress("aa:bb:cc:dd:ee:ff"));
            assertTrue(IdentifierUtil.isMacAddress("12:34:56:78:9A:BC"));
            assertTrue(IdentifierUtil.isMacAddress("00:00:00:00:00:00"));
            assertTrue(IdentifierUtil.isMacAddress("FF:FF:FF:FF:FF:FF"));
        }

        @Test
        @DisplayName("Should return true for valid MAC address with hyphen separators")
        void testIsMacAddress_ValidWithHyphenSeparators_ReturnsTrue() {
            assertTrue(IdentifierUtil.isMacAddress("00-11-22-33-44-55"));
            assertTrue(IdentifierUtil.isMacAddress("AA-BB-CC-DD-EE-FF"));
            assertTrue(IdentifierUtil.isMacAddress("aa-bb-cc-dd-ee-ff"));
            assertTrue(IdentifierUtil.isMacAddress("12-34-56-78-9A-BC"));
            assertTrue(IdentifierUtil.isMacAddress("00-00-00-00-00-00"));
            assertTrue(IdentifierUtil.isMacAddress("FF-FF-FF-FF-FF-FF"));
        }

        @Test
        @DisplayName("Should return true for mixed case MAC addresses")
        void testIsMacAddress_MixedCase_ReturnsTrue() {
            assertTrue(IdentifierUtil.isMacAddress("AaBbCcDdEeFf"));
            assertTrue(IdentifierUtil.isMacAddress("aA:bB:cC:dD:eE:fF"));
            assertTrue(IdentifierUtil.isMacAddress("Aa-Bb-Cc-Dd-Ee-Ff"));
        }

        @ParameterizedTest
        @ValueSource(strings = {
                "00112233445",      // 11 digits (too short)
                "0011223344556",    // 13 digits (too long)
                "GGHHIIJJKKLL",     // Invalid hex characters
                "00:11:22:33:44",   // Missing last octet
                "00:11:22:33:44:55:66", // Too many octets
                "0:11:22:33:44:55", // First octet too short
                "00:1:22:33:44:55", // Second octet too short
                "00:11:22:33:44:5", // Last octet too short
                "00.11.22.33.44.55", // Wrong separator (dot)
                "00 11 22 33 44 55", // Wrong separator (space)
                "ZZ:ZZ:ZZ:ZZ:ZZ:ZZ", // Invalid hex
                "192.168.1.1",      // IP address
                "username123",      // Random text
                "!@#$%^&*()12",     // Special characters
                "",                 // Empty string
                "   ",              // Whitespace
                "001122334455 ",    // Trailing space
                " 001122334455",    // Leading space
        })
        @DisplayName("Should return false for invalid MAC address formats")
        void testIsMacAddress_InvalidFormats_ReturnsFalse(String input) {
            assertFalse(IdentifierUtil.isMacAddress(input));
        }

        @Test
        @DisplayName("Should return false for null MAC address input")
        void testIsMacAddress_NullInput_ReturnsFalse() {
            assertFalse(IdentifierUtil.isMacAddress(null));
        }

        // ==================== IP Address Tests ====================

        @Test
        @DisplayName("Should return true for valid IPv4 addresses")
        void testIsIpAddress_ValidIPv4_ReturnsTrue() {
            assertTrue(IdentifierUtil.isIpAddress("0.0.0.0"));
            assertTrue(IdentifierUtil.isIpAddress("192.168.1.1"));
            assertTrue(IdentifierUtil.isIpAddress("10.0.0.1"));
            assertTrue(IdentifierUtil.isIpAddress("172.16.0.1"));
            assertTrue(IdentifierUtil.isIpAddress("255.255.255.255"));
            assertTrue(IdentifierUtil.isIpAddress("127.0.0.1"));
            assertTrue(IdentifierUtil.isIpAddress("8.8.8.8"));
            assertTrue(IdentifierUtil.isIpAddress("1.2.3.4"));
        }


        @Test
        @DisplayName("Should return true for single digit octets")
        void testIsIpAddress_SingleDigitOctets_ReturnsTrue() {
            assertTrue(IdentifierUtil.isIpAddress("1.1.1.1"));
            assertTrue(IdentifierUtil.isIpAddress("9.8.7.6"));
            assertTrue(IdentifierUtil.isIpAddress("0.0.0.1"));
        }

        @ParameterizedTest
        @ValueSource(strings = {
                "256.1.1.1",        // First octet > 255
                "1.256.1.1",        // Second octet > 255
                "1.1.256.1",        // Third octet > 255
                "1.1.1.256",        // Fourth octet > 255
                "300.168.1.1",      // Octet > 255
                "192.168.1",        // Missing octet
                "192.168.1.1.1",    // Too many octets
                "192.168.-1.1",     // Negative number
                "192.168.1.1a",     // Letter in octet
                "192.168.1.",       // Trailing dot
                ".192.168.1.1",     // Leading dot
                "192..168.1.1",     // Double dot
                "192.168.1.1..",    // Multiple trailing dots
                "192 168 1 1",      // Spaces instead of dots
                "192-168-1-1",      // Hyphens instead of dots
                "192:168:1:1",      // Colons instead of dots
                "a.b.c.d",          // Letters
                "192.168.1.1/24",   // CIDR notation
                "192.168.1.1:8080", // With port
                "00:11:22:33:44:55", // MAC address
                "",                 // Empty string
                "   ",              // Whitespace
                "192.168.1.1 ",     // Trailing space
                " 192.168.1.1",     // Leading space
                "username",         // Random text
        })
        @DisplayName("Should return false for invalid IP address formats")
        void testIsIpAddress_InvalidFormats_ReturnsFalse(String input) {
            assertFalse(IdentifierUtil.isIpAddress(input));
        }

        @Test
        @DisplayName("Should return false for null IP address input")
        void testIsIpAddress_NullInput_ReturnsFalse() {
            assertFalse(IdentifierUtil.isIpAddress(null));
        }

        // ==================== Edge Cases and Boundary Tests ====================

        @Test
        @DisplayName("Should handle boundary values for IP octets correctly")
        void testIsIpAddress_BoundaryValues() {
            // Valid boundaries
            assertTrue(IdentifierUtil.isIpAddress("0.0.0.0"));
            assertTrue(IdentifierUtil.isIpAddress("255.255.255.255"));
            assertTrue(IdentifierUtil.isIpAddress("0.255.0.255"));
            assertTrue(IdentifierUtil.isIpAddress("255.0.255.0"));

            // Invalid boundaries
            assertFalse(IdentifierUtil.isIpAddress("256.0.0.0"));
            assertFalse(IdentifierUtil.isIpAddress("0.256.0.0"));
            assertFalse(IdentifierUtil.isIpAddress("0.0.256.0"));
            assertFalse(IdentifierUtil.isIpAddress("0.0.0.256"));
        }

        @Test
        @DisplayName("Should correctly identify common private IP ranges")
        void testIsIpAddress_PrivateIPRanges() {
            // Class A private
            assertTrue(IdentifierUtil.isIpAddress("10.0.0.0"));
            assertTrue(IdentifierUtil.isIpAddress("10.255.255.255"));

            // Class B private
            assertTrue(IdentifierUtil.isIpAddress("172.16.0.0"));
            assertTrue(IdentifierUtil.isIpAddress("172.31.255.255"));

            // Class C private
            assertTrue(IdentifierUtil.isIpAddress("192.168.0.0"));
            assertTrue(IdentifierUtil.isIpAddress("192.168.255.255"));
        }

        @Test
        @DisplayName("Should correctly identify special IP addresses")
        void testIsIpAddress_SpecialAddresses() {
            assertTrue(IdentifierUtil.isIpAddress("127.0.0.1"));    // Loopback
            assertTrue(IdentifierUtil.isIpAddress("0.0.0.0"));       // Unspecified
            assertTrue(IdentifierUtil.isIpAddress("255.255.255.255")); // Broadcast
            assertTrue(IdentifierUtil.isIpAddress("224.0.0.1"));     // Multicast
        }

        @Test
        @DisplayName("Should not confuse MAC address patterns with IP addresses")
        void testIsIpAddress_NotConfusedWithMAC() {
            assertFalse(IdentifierUtil.isIpAddress("00:11:22:33:44:55"));
            assertFalse(IdentifierUtil.isIpAddress("AA-BB-CC-DD-EE-FF"));
            assertFalse(IdentifierUtil.isIpAddress("001122334455"));
        }

        @Test
        @DisplayName("Should not confuse IP address patterns with MAC addresses")
        void testIsMacAddress_NotConfusedWithIP() {
            assertFalse(IdentifierUtil.isMacAddress("192.168.1.1"));
            assertFalse(IdentifierUtil.isMacAddress("10.0.0.1"));
            assertFalse(IdentifierUtil.isMacAddress("255.255.255.255"));
        }

        // ==================== Type Safety Tests ====================

        @Test
        @DisplayName("Should return Boolean objects, not primitives")
        void testReturnTypes_AreBoxedBooleans() {
            Boolean macResult = IdentifierUtil.isMacAddress("001122334455");
            Boolean ipResult = IdentifierUtil.isIpAddress("192.168.1.1");

            assertNotNull(macResult);
            assertNotNull(ipResult);
            assertInstanceOf(Boolean.class, macResult);
            assertInstanceOf(Boolean.class, ipResult);
        }

        // ==================== Real-World Scenario Tests ====================

        @Test
        @DisplayName("Should validate real-world MAC addresses from various vendors")
        void testIsMacAddress_RealWorldVendorMACs() {
            // Apple devices
            assertTrue(IdentifierUtil.isMacAddress("00:1F:5B:12:34:56"));

            // Cisco devices
            assertTrue(IdentifierUtil.isMacAddress("00-26-0B-AB-CD-EF"));

            // Dell devices
            assertTrue(IdentifierUtil.isMacAddress("D4-BE-D9-12-34-56"));

            // HP devices
            assertTrue(IdentifierUtil.isMacAddress("3C:D9:2B:AA:BB:CC"));

            // Compact format (common in databases)
            assertTrue(IdentifierUtil.isMacAddress("001F5B123456"));
        }

        @Test
        @DisplayName("Should validate real-world IP addresses")
        void testIsIpAddress_RealWorldIPs() {
            // Public DNS servers
            assertTrue(IdentifierUtil.isIpAddress("8.8.8.8"));         // Google
            assertTrue(IdentifierUtil.isIpAddress("1.1.1.1"));         // Cloudflare
            assertTrue(IdentifierUtil.isIpAddress("208.67.222.222")); // OpenDNS

            // Common gateway addresses
            assertTrue(IdentifierUtil.isIpAddress("192.168.0.1"));
            assertTrue(IdentifierUtil.isIpAddress("192.168.1.1"));
            assertTrue(IdentifierUtil.isIpAddress("10.0.0.1"));
        }
}
