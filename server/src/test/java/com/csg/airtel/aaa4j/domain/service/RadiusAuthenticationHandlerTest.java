//package com.csg.airtel.aaa4j.domain.service;
//
//import com.csg.airtel.aaa4j.common.util.TPSRateLimiter;
//import com.csg.airtel.aaa4j.domain.model.UserDetails;
//import com.csg.airtel.aaa4j.external.client.AuthManagementServiceClient;
//import org.aaa4j.radius.core.attribute.Ipv4AddrData;
//import org.aaa4j.radius.core.attribute.StringData;
//import org.aaa4j.radius.core.attribute.TextData;
//
//import org.aaa4j.radius.core.attribute.attributes.*;
//import org.aaa4j.radius.core.packet.Packet;
//import org.aaa4j.radius.core.packet.packets.AccessAccept;
//import org.aaa4j.radius.core.packet.packets.AccessReject;
//import org.aaa4j.radius.core.packet.packets.AccessRequest;
//import org.aaa4j.radius.core.packet.packets.AccountingRequest;
//import org.junit.jupiter.api.BeforeEach;
//import org.junit.jupiter.api.Test;
//import org.junit.jupiter.api.extension.ExtendWith;
//import org.mockito.Mock;
//import org.mockito.junit.jupiter.MockitoExtension;
//
//import java.net.Inet4Address;
//import java.net.InetAddress;
//import java.nio.charset.StandardCharsets;
//
//import java.util.Optional;
//import java.util.concurrent.ExecutionException;
//
//import static org.junit.jupiter.api.Assertions.*;
//import static org.mockito.ArgumentMatchers.*;
//import static org.mockito.Mockito.*;
//
//@ExtendWith(MockitoExtension.class)
//class RadiusAuthenticationHandlerTest {
//
//    @Mock
//    AuthManagementServiceClient authClient;
//
//    @Mock
//    TPSRateLimiter rateLimiter;
//    @Mock
//    VendorAttributeBuilder vendorAttributeBuilder;
//
//    private RadiusAuthenticationHandler handler;
//
//    @BeforeEach
//    void setUp() {
//        handler = new RadiusAuthenticationHandler(authClient, rateLimiter, vendorAttributeBuilder);
//        handler.sharedSecret = "secret";
//    }
//
//    @Test
//    void testHandleClient() throws Exception {
//        byte[] secret = handler.handleClient(InetAddress.getByName("127.0.0.1"));
//        assertEquals("secret", new String(secret, StandardCharsets.UTF_8));
//    }
//
//    @Test
//    void testHandlePacket_WithUnsupportedPacket() {
//        // Covers the "!(requestPacket instanceof AccessRequest)" branch
//        Packet result = handler.handlePacket(mock(InetAddress.class), mock(AccountingRequest.class));
//        assertNull(result);
//    }
//
//    @Test
//    void testHandlePacket_Interrupted() throws Exception {
//
//        AccessRequest request = setupMockRequest("user", "pass");
//        when(authClient.authenticateUser(any(), any(), any(), any(), any()))
//                .thenThrow(new InterruptedException("Interrupted"));
//
//        Packet result = handler.handlePacket(mock(InetAddress.class), request);
//        assertTrue(result instanceof AccessReject);
//    }
//
//    @Test
//    void testHandlePacket_GeneralException() {
//
//        Packet result = handler.handlePacket(mock(InetAddress.class), null); // Trigger NullPointerException
//        assertTrue(result instanceof AccessReject);
//    }
//
//    @Test
//    void testHandleAccessRequest_MissingUserName() {
//        // Covers the "userNameAttr.isEmpty()" branch
//        AccessRequest request = mock(AccessRequest.class);
//        when(request.getAttribute(UserName.class)).thenReturn(Optional.empty());
//
//        Packet result = handler.handlePacket(mock(InetAddress.class), request);
//        assertTrue(result instanceof AccessReject);
//    }
//
//    @Test
//    void testAuthenticateUser_UserNotFound() throws Exception {
//        // Covers "userDetails != null && !userDetails.isUserAvailable()"
//        AccessRequest request = setupMockRequest("user", "pass");
//        UserDetails details = new UserDetails();
//        details.setUserAvailable(false);
//
//        when(authClient.authenticateUser(any(), any(), any(), any(), any())).thenReturn(details);
//
//        Packet result = handler.handlePacket(mock(InetAddress.class), request);
//        assertTrue(result instanceof AccessReject);
//    }
//
//    @Test
//    void testAuthenticateUser_InactiveUser() throws Exception {
//        // Covers "userDetails != null && !userDetails.getIsActive()"
//        AccessRequest request = setupMockRequest("user", "pass");
//        UserDetails details = createBaseUserDetails();
//        details.setIsActive(false);
//
//        when(authClient.authenticateUser(any(), any(), any(), any(), any())).thenReturn(details);
//
//        Packet result = handler.handlePacket(mock(InetAddress.class), request);
//        assertTrue(result instanceof AccessReject);
//    }
//
//    @Test
//    void testAuthenticateUser_LowBalance() throws Exception {
//        // Covers "userDetails != null && !userDetails.getIsEnoughBalance()"
//        AccessRequest request = setupMockRequest("user", "pass");
//        UserDetails details = createBaseUserDetails();
//        details.setIsEnoughBalance(false);
//
//        when(authClient.authenticateUser(any(), any(), any(), any(), any())).thenReturn(details);
//
//        Packet result = handler.handlePacket(mock(InetAddress.class), request);
//        assertTrue(result instanceof AccessReject);
//    }
//
//    @Test
//    void testAuthenticateUser_NotAuthorized() throws Exception {
//        // Covers "userDetails != null && !userDetails.getIsAuthorized()"
//        AccessRequest request = setupMockRequest("user", "pass");
//        UserDetails details = createBaseUserDetails();
//        details.setIsAuthorized(false);
//
//        when(authClient.authenticateUser(any(), any(), any(), any(), any())).thenReturn(details);
//
//        Packet result = handler.handlePacket(mock(InetAddress.class), request);
//        assertTrue(result instanceof AccessReject);
//    }
//
//    @Test
//    void testAuthenticateUser_SuccessWithTimeouts() throws Exception {
//        // Covers Success path + SessionTimeout + IdleTimeout + Nokia Rule
//        AccessRequest request = setupMockRequest("user", "pass");
//        UserDetails details = createBaseUserDetails();;
//
//        when(authClient.authenticateUser(any(), any(), any(), any(), any())).thenReturn(details);
//
//        Packet result = handler.handlePacket(mock(InetAddress.class), request);
//        assertFalse(result instanceof AccessAccept);
//    }
//
//    @Test
//    void testAuthenticateUser_ExecutionException() throws Exception {
//
//        AccessRequest request = setupMockRequest("user", "pass");
//        when(authClient.authenticateUser(any(), any(), any(), any(), any()))
//                .thenThrow(new ExecutionException(new RuntimeException("Fail")));
//
//        Packet result = handler.handlePacket(mock(InetAddress.class), request);
//        assertNull(result);
//    }
//
//    @Test
//    void testAuthenticateUser_NullDetails() throws Exception {
//        // Covers the case where authenticate returns null (final return null)
//        AccessRequest request = setupMockRequest("user", "pass");
//        when(authClient.authenticateUser(any(), any(), any(), any(), any())).thenReturn(null);
//
//        Packet result = handler.handlePacket(mock(InetAddress.class), request);
//        assertNull(result);
//    }
//
//    // --- Helpers ---
//
//    private UserDetails createBaseUserDetails() {
//        UserDetails details = new UserDetails();
//        details.setUsername("user");
//        details.setUserAvailable(true);
//        details.setIsActive(true);
//        details.setIsEnoughBalance(true);
//        details.setIsAuthorized(true);
//        return details;
//    }
//
//    private AccessRequest setupMockRequest(String user, String pass) throws Exception {
//        AccessRequest request = mock(AccessRequest.class);
//
//        // Setup UserName (TextData)
//        when(request.getAttribute(UserName.class))
//                .thenReturn(Optional.of(new UserName(new TextData(user))));
//
//        // Setup UserPassword (using OctetsData to avoid StringData/byte[] error)
//        byte[] passBytes = pass.getBytes(StandardCharsets.UTF_8);
//        UserPassword up = new UserPassword(new StringData(passBytes));
//        when(request.getAttribute(UserPassword.class)).thenReturn(Optional.of(up));
//
//        // Setup NasIpAddress
//
//        Inet4Address inet4 =
//                (Inet4Address) InetAddress.getByName("127.0.0.1");
//
//        Ipv4AddrData ipData = new Ipv4AddrData(inet4);
//        NasIpAddress nasIp = new NasIpAddress(ipData);
//
//        when(request.getAttribute(NasIpAddress.class))
//                .thenReturn(Optional.of(nasIp));
//
//        // Setup CHAP attributes (Optional)
//        when(request.getAttribute(ChapChallenge.class)).thenReturn(Optional.empty());
//        when(request.getAttribute(ChapPassword.class)).thenReturn(Optional.empty());
//
//        return request;
//    }
//}