package com.csg.airtel.aaa4j.domain.model;


public class VendorAttribute {
    private Integer attributeId;
    private String attributeName;
    private String valuePath;
    private String dataType;
    private String attributePrefix;  // NEW: Prefix for formatting

    public VendorAttribute(Integer attributeId, String attributeName,
                           String valuePath, String dataType, String attributePrefix) {
        this.attributeId = attributeId;
        this.attributeName = attributeName;
        this.valuePath = valuePath;
        this.dataType = dataType;
        this.attributePrefix = attributePrefix;
    }

    // Getters
    public Integer getAttributeId() { return attributeId; }
    public String getAttributeName() { return attributeName; }
    public String getValuePath() { return valuePath; }
    public String getDataType() { return dataType; }
    public String getAttributePrefix() { return attributePrefix; }  // NEW

    // Setters
    public void setAttributeId(Integer attributeId) { this.attributeId = attributeId; }
    public void setAttributeName(String attributeName) { this.attributeName = attributeName; }
    public void setValuePath(String valuePath) { this.valuePath = valuePath; }
    public void setDataType(String dataType) { this.dataType = dataType; }
    public void setAttributePrefix(String attributePrefix) { this.attributePrefix = attributePrefix; }

    @Override
    public String toString() {
        return "VendorAttribute{" +
                "attributeId=" + attributeId +
                ", attributeName='" + attributeName + '\'' +
                ", valuePath='" + valuePath + '\'' +
                ", dataType='" + dataType + '\'' +
                ", attributePrefix='" + attributePrefix + '\'' +
                '}';
    }
}
