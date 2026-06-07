package com.csg.airtel.aaa4j.domain.service;

import com.csg.airtel.aaa4j.common.util.LoggingUtil;
import jakarta.enterprise.context.ApplicationScoped;
import org.jboss.logging.Logger;

@ApplicationScoped
public class VendorAttributeFormatter {
    private static final Logger LOG = Logger.getLogger(VendorAttributeFormatter.class);
    private static final String CLASS_NAME = "VendorAttributeFormatter";
    public static final String FORMAT_VALUE = "formatValue";

    /**
     * Format a value with the given prefix.
     *
     * @param prefix Attribute prefix (e.g., "subscriber:sa=", or null)
     * @param rawValue Raw value from database
     * @return Formatted value for RADIUS packet
     */
    public String formatValue(String prefix, String rawValue) {
        if (rawValue == null || rawValue.trim().isEmpty()) {
            LoggingUtil.logTrace(LOG, CLASS_NAME, FORMAT_VALUE,
                    "Raw value is null or empty");
            return rawValue;
        }

        // If no prefix, return raw value (Nokia, etc.)
        if (prefix == null || prefix.trim().isEmpty()) {
            LoggingUtil.logTrace(LOG, CLASS_NAME, FORMAT_VALUE,
                    "No prefix specified, returning raw value: %s", rawValue);
            return rawValue;
        }

        // If value already contains '=', it might be pre-formatted
        if (rawValue.contains("=")) {
            LoggingUtil.logTrace(LOG, CLASS_NAME, FORMAT_VALUE,
                    "Value already contains '=', returning as-is: %s", rawValue);
            return rawValue;
        }

        // Apply prefix
        String formatted = prefix + rawValue;

        LoggingUtil.logDebug(LOG, CLASS_NAME, FORMAT_VALUE,
                "Formatted value: prefix='%s', raw='%s', result='%s'",
                prefix, rawValue, formatted);

        return formatted;
    }
}
