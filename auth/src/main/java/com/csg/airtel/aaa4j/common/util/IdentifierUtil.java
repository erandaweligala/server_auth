package com.csg.airtel.aaa4j.common.util;

import java.util.regex.Pattern;

public final class IdentifierUtil {
    private static final Pattern MAC_ADDRESS_PATTERN = Pattern.compile("^[0-9A-Fa-f]{12}$|([0-9A-Fa-f]{2}[:-]){5}([0-9A-Fa-f]{2})$|([0-9A-Fa-f]{4}\\.){2}([0-9A-Fa-f]{4})$");
    private static final Pattern IP_ADDRESS_PATTERN = Pattern.compile("^((25[0-5]|2[0-4]\\d|1\\d{2}|[1-9]?\\d)(\\.|$)){4}$");
    private IdentifierUtil() {
        // Prevent instantiation
    }

    /**
     * Normalizes a MAC address or username by removing ":" and "-"
     * and converting it to uppercase.
     *
     * Examples:
     * - 00:11:22:33:44:55 → 001122334455
     * - 00-11-22-33-44-55 → 001122334455
     * - user123 → USER123
     *
     * @param input the string to normalize
     * @return normalized uppercase string without separators
     */
    public static String normalizeIdentifier(String input) {
        if (input == null) {
            return null;
        }
        return input.replaceAll("[:-]", "").toUpperCase();
    }

    public static boolean isMacAddress(String input){
        if (input == null) {
            return false;
        }
        return MAC_ADDRESS_PATTERN.matcher(input).matches();
    }

    public static boolean isIpAddress(String input) {
        if (input == null) {
            return false;
        }
        return IP_ADDRESS_PATTERN.matcher(input).matches();
    }


}

