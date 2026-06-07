package com.csg.airtel.aaa4j.domain.service;

import com.csg.airtel.aaa4j.common.util.LoggingUtil;
import com.csg.airtel.aaa4j.domain.model.VendorAttributeDetail;
import jakarta.enterprise.context.ApplicationScoped;
import org.aaa4j.radius.core.attribute.Attribute;
import org.aaa4j.radius.core.attribute.VsaData;
import org.aaa4j.radius.core.attribute.attributes.VendorSpecific;
import org.jboss.logging.Logger;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;


@ApplicationScoped
public class VendorAttributeBuilder {
    private static final Logger LOG = Logger.getLogger(VendorAttributeBuilder.class);
    private static final String CLASS_NAME = "VendorAttributeBuilder";
    private static final VendorAttributeFormatter formatter = new VendorAttributeFormatter();
    public static final String BUILD_VENDOR_ATTRIBUTES = "buildVendorAttributes";

    /**
     * Builds vendor-specific attributes dynamically based on vendor ID and attribute map.
     *
     * @param vendorId Vendor ID (e.g., 6527 for Nokia)
     * @param vendorAttributes Map of attribute ID -> value
     * @return List of RADIUS vendor-specific attributes
     */
    public List<Attribute<VsaData>> buildVendorAttributes(Integer vendorId, List<VendorAttributeDetail> vendorAttributes) {
        List<Attribute<VsaData>> attributes = new ArrayList<>();

        if (vendorId == null || vendorAttributes == null || vendorAttributes.isEmpty()) {
            LoggingUtil.logWarn(LOG, CLASS_NAME, BUILD_VENDOR_ATTRIBUTES,
                    "No vendor attributes to build: vendorId=%s, attributeCount=%d",
                    vendorId, vendorAttributes != null ? vendorAttributes.size() : 0);
            return attributes;
        }

        LoggingUtil.logInfo(LOG, CLASS_NAME, BUILD_VENDOR_ATTRIBUTES,
                "Building %d vendor-specific attributes for vendorId=%d",
                vendorAttributes.size(), vendorId);

        for (VendorAttributeDetail detail : vendorAttributes) {
            try {
                // Format value with prefix (simple!)
                String formattedValue = formatter.formatValue(
                        detail.getAttributePrefix(),  // Just pass the prefix
                        detail.getValue()
                );

                Attribute<VsaData> vsaAttribute = createVendorSpecificAttribute(
                        vendorId, detail.getAttributeId(), formattedValue);
                attributes.add(vsaAttribute);

                LoggingUtil.logDebug(LOG, CLASS_NAME, BUILD_VENDOR_ATTRIBUTES,
                        "Created VSA: vendorId=%d, attrId=%d, prefix='%s', raw='%s', formatted='%s'",
                        vendorId, detail.getAttributeId(), detail.getAttributePrefix(),
                        detail.getValue(), formattedValue);
            } catch (Exception e) {
                LoggingUtil.logError(LOG, CLASS_NAME, BUILD_VENDOR_ATTRIBUTES, e,
                        "Failed to create VSA: vendorId=%d, attrId=%d",
                        vendorId, detail.getAttributeId());
            }
        }

        LoggingUtil.logDebug(LOG, CLASS_NAME, BUILD_VENDOR_ATTRIBUTES,
                "Successfully built %d vendor-specific attributes", attributes.size());

        return attributes;
    }

    /**
     * Creates a vendor-specific attribute using VendorSpecificData.
     *
     * @param vendorId Vendor ID
     * @param attributeId Vendor-specific attribute type
     * @param value Attribute value (string)
     * @return VendorSpecificAttribute
     */
    private Attribute<VsaData> createVendorSpecificAttribute(int vendorId, String attributeId, String value) {
        byte[] valueBytes = value.getBytes(StandardCharsets.UTF_8);
        int vendorAttribute = Integer.parseInt(attributeId);
        // Create VsaData with vendorId, vendorType, and data
        VsaData vsaData = new VsaData(vendorId, vendorAttribute, valueBytes);

        // Create VendorSpecific attribute
        return new VendorSpecific(vsaData);
    }
}