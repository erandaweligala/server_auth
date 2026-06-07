package com.csg.airtel.aaa4j.domain.model;

import java.util.ArrayList;
import java.util.List;

public class VendorAttributeConfig {
    private String nasIpAddress;
    private String vendorName;
    private Integer vendorId;
    private List<VendorAttribute> attributes;

    // Constructor
    public VendorAttributeConfig(String nasIpAddress, String vendorName, Integer vendorId) {
        this.nasIpAddress = nasIpAddress;
        this.vendorName = vendorName;
        this.vendorId = vendorId;
        this.attributes = new ArrayList<>();
    }

    // Getters and Setters
    public String getNasIpAddress() {
        return nasIpAddress;
    }

    public void setNasIpAddress(String nasIpAddress) {
        this.nasIpAddress = nasIpAddress;
    }

    public String getVendorName() {
        return vendorName;
    }

    public void setVendorName(String vendorName) {
        this.vendorName = vendorName;
    }

    public Integer getVendorId() {
        return vendorId;
    }

    public void setVendorId(Integer vendorId) {
        this.vendorId = vendorId;
    }

    public List<VendorAttribute> getAttributes() {
        return attributes;
    }

    public void setAttributes(List<VendorAttribute> attributes) {
        this.attributes = attributes;
    }

    public void addAttribute(VendorAttribute attribute) {
        this.attributes.add(attribute);
    }

    @Override
    public String toString() {
        return "VendorAttributeConfig{" +
                "nasIpAddress='" + nasIpAddress + '\'' +
                ", vendorName='" + vendorName + '\'' +
                ", vendorId=" + vendorId +
                ", attributes=" + attributes.size() +
                '}';
    }
}
