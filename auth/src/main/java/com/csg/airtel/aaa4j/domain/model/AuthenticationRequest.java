package com.csg.airtel.aaa4j.domain.model;

import lombok.NoArgsConstructor;

@NoArgsConstructor
public class AuthenticationRequest {
    private String username;
    private String password;
    private String chapChallenge;
    private String chapPassword;
    private String nasIpAddress;
    private String framedProtocol;

    public AuthenticationRequest(String username, String password, String chapChallenge, String chapPassword, String nasIpAddress, String framedProtocol) {
        this.username = username;
        this.password = password;
        this.chapChallenge = chapChallenge;
        this.chapPassword = chapPassword;
        this.nasIpAddress = nasIpAddress;
        this.framedProtocol = framedProtocol;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getChapChallenge() {
        return chapChallenge;
    }

    public void setChapChallenge(String chapChallenge) {
        this.chapChallenge = chapChallenge;
    }

    public String getNasIpAddress() {
        return nasIpAddress;
    }

    public void setNasIpAddress(String nasIpAddress) {
        this.nasIpAddress = nasIpAddress;
    }

    public String getFramedProtocol() {
        return framedProtocol;
    }

    public void setFramedProtocol(String framedProtocol) {
        this.framedProtocol = framedProtocol;
    }

    public String getChapPassword() {
        return chapPassword;
    }

    public void setChapPassword(String chapPassword) {
        this.chapPassword = chapPassword;
    }
}
