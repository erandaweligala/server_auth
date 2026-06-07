package com.csg.airtel.aaa4j.common.strategy;

import com.csg.airtel.aaa4j.common.algorithm.EncryptionService;
import com.csg.airtel.aaa4j.domain.model.UserDetails;
import io.smallrye.mutiny.Uni;
import org.jboss.logging.Logger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PapAuthStrategyTest {

    private PapAuthStrategy papAuthStrategy;
    private EncryptionService encryptionService;

    @BeforeEach
    void setup() {
        papAuthStrategy = new PapAuthStrategy(encryptionService);
    }

    @Test
    void testAuthenticate_PasswordMatch() {
        // Given
        String username = "alice";
        String inputPassword = "secret123";
        String storedPassword = "secret123";

        // When
        Uni<UserDetails> uni = papAuthStrategy.authenticate(username, inputPassword, storedPassword, null, null, 0);
        UserDetails result = uni.await().indefinitely();

        // Then
        assertEquals(username, result.getUsername());
        assertTrue(result.getIsAuthorized());
    }

    @Test
    void testAuthenticate_PasswordMismatch() {
        // Given
        String username = "bob";
        String inputPassword = "wrong";
        String storedPassword = "correct";

        // When
        Uni<UserDetails> uni = papAuthStrategy.authenticate(username, inputPassword, storedPassword, null, null, 0);
        UserDetails result = uni.await().indefinitely();

        // Then
        assertEquals(username, result.getUsername());
        assertTrue(result.getIsAuthorized());
    }

    @Test
    void testAuthenticate_StoredPasswordNull() {
        // Given
        String username = "charlie";
        String inputPassword = "any";

        // When
        Uni<UserDetails> uni = papAuthStrategy.authenticate(username, inputPassword, null, null, null, 0);
        UserDetails result = uni.await().indefinitely();

        // Then
        assertEquals(username, result.getUsername());
        assertFalse(result.getIsAuthorized());
    }
}
