package com.csg.airtel.aaa4j.domain.constant;

public class SQLConstant {
    private SQLConstant(){

    }
    public static final String QUERY_GET_USER = "SELECT username FROM ADMIN.USER_TABLE WHERE id=?";
    public static final String QUERY_GET_USER_DETAILS =
            "SELECT PASSWORD FROM USER_TABLE WHERE USERNAME = ?";
    public static final String QUERY_GETUSER_BY_MAC_ADDRESS = """
            SELECT CASE
            WHEN COUNT(*) > 0 THEN 1
            ELSE 0
            END AS mac_exists
            FROM USER_TABLE
            WHERE UPPER(REGEXP_REPLACE(mac_address, '[^0-9A-Fa-f]', '')) = UPPER(?)
            """;
    public static final String QUERY_STATUS_CHECK = """
            SELECT CASE
                     WHEN COUNT(*) > 0 THEN 1
                     ELSE 0
                   END AS is_active
            FROM USER_TABLE
            WHERE status = 'ACTIVE'
            AND (
                (
                    REGEXP_LIKE(?, '^([0-9A-Fa-f]{2}[:-]){5}([0-9A-Fa-f]{2})$')
                    AND UPPER(REGEXP_REPLACE(mac_address, '[^0-9A-Fa-f]', '')) = UPPER(REGEXP_REPLACE(?, '[^0-9A-Fa-f]', ''))
                )
                OR
                (
                    NOT REGEXP_LIKE(?, '^([0-9A-Fa-f]{2}[:-]){5}([0-9A-Fa-f]{2})$')
                    AND username = ?
                )
            )
            
            """;

}
