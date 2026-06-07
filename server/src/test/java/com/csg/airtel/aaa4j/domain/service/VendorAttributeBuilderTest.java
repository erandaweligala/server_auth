package com.csg.airtel.aaa4j.domain.service;

import com.csg.airtel.aaa4j.domain.model.VendorAttributeDetail;
import org.aaa4j.radius.core.attribute.Attribute;
import org.aaa4j.radius.core.attribute.VsaData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class VendorAttributeBuilderTest {

    @InjectMocks
    private VendorAttributeBuilder vendorAttributeBuilder;

    private VendorAttributeDetail validDetail;

    @BeforeEach
    void setUp() {
        validDetail = new VendorAttributeDetail();
        validDetail.setAttributeId("10");
        validDetail.setAttributePrefix("Prefix-");
        validDetail.setValue("Value");
    }

    @Test
    @DisplayName("Should return empty list when inputs are null or empty")
    void testBuildVendorAttributes_EmptyInputs() {
        // Test null vendorId
        assertTrue(vendorAttributeBuilder.buildVendorAttributes(null, new ArrayList<>()).isEmpty());

        // Test null list
        assertTrue(vendorAttributeBuilder.buildVendorAttributes(6527, null).isEmpty());

        // Test empty list
        assertTrue(vendorAttributeBuilder.buildVendorAttributes(6527, Collections.emptyList()).isEmpty());
    }

    @Test
    @DisplayName("Should successfully build vendor attributes for valid input")
    void testBuildVendorAttributes_Success() {
        List<VendorAttributeDetail> details = Arrays.asList(validDetail);
        Integer vendorId = 6527;

        List<Attribute<VsaData>> result = vendorAttributeBuilder.buildVendorAttributes(vendorId, details);

        assertNotNull(result);
        assertEquals(1, result.size());

        VsaData data = result.get(0).getData();
        assertEquals(vendorId, data.getVendorId());
        // The attributeId "10" is parsed to integer
        // The value "Value" with prefix "Prefix-" becomes "Prefix-Value"
        assertEquals(10, data.getVendorType());
    }

}