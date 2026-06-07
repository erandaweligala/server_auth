package com.csg.airtel.aaa4j.domain.model;

public class VendorAttributeDetail {
    private Integer attributeId;
    private String attributeName;
    private String attributePrefix;  // NEW
    private String value;

    public VendorAttributeDetail() {}

    public VendorAttributeDetail(Integer attributeId, String attributeName,
                                 String attributePrefix, String value) {
        this.attributeId = attributeId;
        this.attributeName = attributeName;
        this.attributePrefix = attributePrefix;
        this.value = value;
    }

    // Getters and setters
    public Integer getAttributeId() { return attributeId; }
    public void setAttributeId(Integer attributeId) { this.attributeId = attributeId; }

    public String getAttributeName() { return attributeName; }
    public void setAttributeName(String attributeName) { this.attributeName = attributeName; }

    public String getAttributePrefix() { return attributePrefix; }
    public void setAttributePrefix(String attributePrefix) { this.attributePrefix = attributePrefix; }

    public String getValue() { return value; }
    public void setValue(String value) { this.value = value; }

    @Override
    public String toString() {
        return "VendorAttributeDetail{" +
                "attributeId=" + attributeId +
                ", attributeName='" + attributeName + '\'' +
                ", attributePrefix='" + attributePrefix + '\'' +
                ", value='" + value + '\'' +
                '}';
    }
}
