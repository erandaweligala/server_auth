package com.csg.airtel.aaa4j.domain.constant;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SQLConstantTest {

    @Test
    void testQueryConstants() {
        assertNotNull(SQLConstant.QUERY_GET_USER);
        assertTrue(SQLConstant.QUERY_GET_USER.contains("SELECT username FROM ADMIN.USER_TABLE"));
        
        assertNotNull(SQLConstant.QUERY_GET_USER_DETAILS);
        assertTrue(SQLConstant.QUERY_GET_USER_DETAILS.contains("SELECT PASSWORD FROM USER_TABLE"));
        
        assertNotNull(SQLConstant.QUERY_GETUSER_BY_MAC_ADDRESS);
        assertTrue(SQLConstant.QUERY_GETUSER_BY_MAC_ADDRESS.contains("mac_exists"));
        
        assertNotNull(SQLConstant.QUERY_STATUS_CHECK);
        assertTrue(SQLConstant.QUERY_STATUS_CHECK.contains("is_active"));
    }
}
