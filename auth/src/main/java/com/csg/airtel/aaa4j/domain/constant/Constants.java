package com.csg.airtel.aaa4j.domain.constant;

public class Constants {
    private Constants(){

    }
    public static final String PAP_PROTOCOL = "PAP";
    public static final String CHAP_PROTOCOL = "CHAP";
    public static final String MAC_PROTOCOL = "MAC";

    public static final int CHAP_CHALLENGE_LENGTH = 16;
    public static final int CHAP_ID_MAX = 256;
    public static final String MAC_PATTERN = "^[0-9A-F]{12}$";
    public static final String ERROR = "error";
    public static final String CODE = "code";
    public static final String DESCRIPTION = "description";
    public static final String MESSAGE = "message";
    public static final String CHAP_AUTH_ERROR = "CHAP authentication error for user: %s";
    public static final String PASSWORD = "password";
    public static final String STATUS = "status";
    public static final String STATUS_ACTIVE = "ACTIVE";
    public static final String STATUS_BARRED = "BARRED";
    public static final String TRACE_ID = "traceId";

}
