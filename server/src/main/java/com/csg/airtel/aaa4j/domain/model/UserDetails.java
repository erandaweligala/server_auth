package com.csg.airtel.aaa4j.domain.model;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class UserDetails {
    private String username;
    private boolean isAuthorized;
    private boolean isActive;
    private boolean isEnoughBalance;
    private boolean isUserAvailable;
    private Integer vendorId;
    private List<VendorAttributeDetail> vendorAttributes;

    // No-args constructor required for JSON serialization/deserialization
    public UserDetails() {
    }

    public UserDetails(String username, boolean isAuthorized, boolean isActive,
                       boolean isEnoughBalance, boolean isUserAvailable,
                       Integer vendorId, List<VendorAttributeDetail> vendorAttributes) {
        this.username = username;
        this.isAuthorized = isAuthorized;
        this.isActive = isActive;
        this.isEnoughBalance = isEnoughBalance;
        this.isUserAvailable = isUserAvailable;
        this.vendorId = vendorId;
        this.vendorAttributes = vendorAttributes;
    }

    // Getters and setters
    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public boolean getIsAuthorized() {
        return isAuthorized;
    }

    public void setIsAuthorized(boolean isAuthorized) {
        this.isAuthorized = isAuthorized;
    }

    public boolean getIsActive() {
        return isActive;
    }

    public void setIsActive(boolean isActive) {
        this.isActive = isActive;
    }

    public boolean getIsEnoughBalance() {
        return isEnoughBalance;
    }

    public void setIsEnoughBalance(boolean isEnoughBalance) {
        this.isEnoughBalance = isEnoughBalance;
    }

    public boolean isUserAvailable() {
        return isUserAvailable;
    }

    public void setUserAvailable(boolean userAvailable) {
        isUserAvailable = userAvailable;
    }

    public Integer getVendorId() {
        return vendorId;
    }

    public void setVendorId(Integer vendorId) {
        this.vendorId = vendorId;
    }

    public List<VendorAttributeDetail> getVendorAttributes() {
        return vendorAttributes != null ? vendorAttributes : new ArrayList<>();
    }

    public void setVendorAttributes(List<VendorAttributeDetail> vendorAttributes) {
        this.vendorAttributes = vendorAttributes;
    }


    @Override
    public String toString() {
        return "UserDetails{" +
                "username='" + username + '\'' +
                ", isAuthorized=" + isAuthorized +
                ", isActive=" + isActive +
                ", isEnoughBalance=" + isEnoughBalance +
                ", isUserAvailable=" + isUserAvailable +
                ", vendorId=" + vendorId +
                ", vendorAttributesCount=" + (vendorAttributes != null ? vendorAttributes.size() : 0) +
                '}';
    }
}