package com.csg.airtel.aaa4j.external.client;

import com.csg.airtel.aaa4j.application.config.WebClientProvider;
import com.csg.airtel.aaa4j.domain.model.UserDetails;
import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.HttpRequest;
import io.vertx.ext.web.client.HttpResponse;
import io.vertx.ext.web.client.WebClient;
import org.junit.jupiter.api.*;
import org.mockito.*;
import org.slf4j.MDC;

import java.util.concurrent.ExecutionException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AuthManagementServiceClientTest {

    @Mock
    private WebClientProvider webClientProvider;

    @Mock
    private WebClient webClient;

    @Mock
    private HttpRequest<Buffer> httpRequest;

    @Mock
    private HttpResponse<Buffer> httpResponse;

    @InjectMocks
    private AuthManagementServiceClient authClient;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        when(webClientProvider.getClient()).thenReturn(webClient);
        when(httpRequest.timeout(anyLong())).thenReturn(httpRequest);

        // manually inject config property
        authClient.authServiceUrl = "http://localhost:8083/api/users/authenticate";
    }

    @AfterEach
    void tearDown() {
        MDC.clear();
    }

    @Test
    void testAuthenticate_User_failureResponse_shouldReturnNull() throws InterruptedException {
        // Given
        String username = "user2";
        when(webClient.postAbs(anyString())).thenReturn(httpRequest);
        when(httpRequest.putHeader(anyString(), anyString())).thenReturn(httpRequest);

        doAnswer(invocation -> {
            Handler<AsyncResult<HttpResponse<Buffer>>> handler = invocation.getArgument(1);
            AsyncResult<HttpResponse<Buffer>> asyncResult = mock(AsyncResult.class);
            when(asyncResult.succeeded()).thenReturn(false);
            when(asyncResult.cause()).thenReturn(new RuntimeException("Connection failed"));
            handler.handle(asyncResult);
            return null;
        }).when(httpRequest).sendJsonObject(any(JsonObject.class), any());

        // When
        UserDetails result;
        try {
            result = authClient.authenticateUser(username, "pass", null, null, null, null);
        } catch (ExecutionException e) {
            fail("ExecutionException should be caught internally, not thrown: " + e.getMessage());
            return;
        }

        // Then
        assertNull(result, "Should return null when authentication fails");
        verify(webClient).postAbs(authClient.authServiceUrl);
        verify(httpRequest, times(3)).putHeader(anyString(), anyString());
        verify(httpRequest).sendJsonObject(any(JsonObject.class), any());
    }

    @Test
    void testAuthenticate_User_successResponse_shouldReturnUserDetails() throws ExecutionException, InterruptedException {
        // Given
        String username = "testuser";
        String password = "testpass";

        when(webClient.postAbs(anyString())).thenReturn(httpRequest);
        when(httpRequest.putHeader(anyString(), anyString())).thenReturn(httpRequest);

        JsonObject responseJson = new JsonObject()
                .put("username", username);

        when(httpResponse.statusCode()).thenReturn(200);
        when(httpResponse.bodyAsJsonObject()).thenReturn(responseJson);

        doAnswer(invocation -> {
            Handler<AsyncResult<HttpResponse<Buffer>>> handler = invocation.getArgument(1);
            AsyncResult<HttpResponse<Buffer>> asyncResult = mock(AsyncResult.class);
            when(asyncResult.succeeded()).thenReturn(true);
            when(asyncResult.result()).thenReturn(httpResponse);
            handler.handle(asyncResult);
            return null;
        }).when(httpRequest).sendJsonObject(any(JsonObject.class), any());

        // When
        UserDetails result = authClient.authenticateUser(username, password, null, null, null, null);

        // Then
        assertNotNull(result);
        assertEquals(username, result.getUsername());
        verify(httpRequest).sendJsonObject(any(JsonObject.class), any());
    }

    @Test
    void testAuthenticate_User_errorInResponseBody_shouldReturnNull() throws InterruptedException {
        // Given
        String username = "user3";
        when(webClient.postAbs(anyString())).thenReturn(httpRequest);
        when(httpRequest.putHeader(anyString(), anyString())).thenReturn(httpRequest);

        JsonObject errorResponse = new JsonObject()
                .put("error", "INVALID_CREDENTIALS")
                .put("description", "Invalid username or password");

        when(httpResponse.statusCode()).thenReturn(200);
        when(httpResponse.bodyAsJsonObject()).thenReturn(errorResponse);

        doAnswer(invocation -> {
            Handler<AsyncResult<HttpResponse<Buffer>>> handler = invocation.getArgument(1);
            AsyncResult<HttpResponse<Buffer>> asyncResult = mock(AsyncResult.class);
            when(asyncResult.succeeded()).thenReturn(true);
            when(asyncResult.result()).thenReturn(httpResponse);
            handler.handle(asyncResult);
            return null;
        }).when(httpRequest).sendJsonObject(any(JsonObject.class), any());

        // When
        UserDetails result;
        try {
            result = authClient.authenticateUser(username, "wrongpass", null, null, null, null);
        } catch (ExecutionException e) {
            fail("ExecutionException should be caught internally: " + e.getMessage());
            return;
        }

        // Then
        assertNull(result, "Should return null when response contains error field");
        verify(httpRequest).sendJsonObject(any(JsonObject.class), any());
    }

}