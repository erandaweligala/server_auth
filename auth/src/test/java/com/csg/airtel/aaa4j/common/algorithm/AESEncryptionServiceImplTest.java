package com.csg.airtel.aaa4j.common.algorithm;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AESEncryptionServiceImplTest {

    private AESEncryptionServiceImpl encryptionService;
    private static final String ALGORITHM = "AES";
    private static final String SECRET_KEY = "MySecretKey12345"; // 16 bytes for AES
    private static final String PLAIN_TEXT = "Hello World";

    @BeforeEach
    void setUp() {
        encryptionService = new AESEncryptionServiceImpl();
    }

    @Test
    void testEncryptDecryptSuccess() {
        String encrypted = encryptionService.encrypt(PLAIN_TEXT, ALGORITHM, SECRET_KEY);
        assertNotNull(encrypted);
        assertNotEquals(PLAIN_TEXT, encrypted);

        String decrypted = encryptionService.decrypt(encrypted, ALGORITHM, SECRET_KEY);
        assertEquals(PLAIN_TEXT, decrypted);
    }

    @Test
    void testEncryptProducesBase64() {
        String encrypted = encryptionService.encrypt(PLAIN_TEXT, ALGORITHM, SECRET_KEY);
        
        // Base64 encoded strings should only contain valid Base64 characters
        assertTrue(encrypted.matches("^[A-Za-z0-9+/]*={0,2}$"));
    }

    @Test
    void testEncryptDifferentInputsProduceDifferentOutputs() {
        String text1 = "Hello";
        String text2 = "World";
        
        String encrypted1 = encryptionService.encrypt(text1, ALGORITHM, SECRET_KEY);
        String encrypted2 = encryptionService.encrypt(text2, ALGORITHM, SECRET_KEY);
        
        assertNotEquals(encrypted1, encrypted2);
    }

    @Test
    void testEncryptEmptyString() {
        String emptyText = "";
        String encrypted = encryptionService.encrypt(emptyText, ALGORITHM, SECRET_KEY);
        String decrypted = encryptionService.decrypt(encrypted, ALGORITHM, SECRET_KEY);
        
        assertEquals(emptyText, decrypted);
    }


    @Test
    void testEncryptLongText() {
        String longText = "This is a very long text that should be encrypted and decrypted successfully using AES encryption algorithm.";
        
        String encrypted = encryptionService.encrypt(longText, ALGORITHM, SECRET_KEY);
        String decrypted = encryptionService.decrypt(encrypted, ALGORITHM, SECRET_KEY);
        
        assertEquals(longText, decrypted);
    }

    @Test
    void testEncryptSpecialCharacters() {
        String specialText = "Hello@#$%^&*()_+{}|:<>?[];',./`~";
        
        String encrypted = encryptionService.encrypt(specialText, ALGORITHM, SECRET_KEY);
        String decrypted = encryptionService.decrypt(encrypted, ALGORITHM, SECRET_KEY);
        
        assertEquals(specialText, decrypted);
    }

    @Test
    void testEncryptUnicodeCharacters() {
        String unicodeText = "Hello 世界 🌍";
        
        String encrypted = encryptionService.encrypt(unicodeText, ALGORITHM, SECRET_KEY);
        String decrypted = encryptionService.decrypt(encrypted, ALGORITHM, SECRET_KEY);
        
        assertEquals(unicodeText, decrypted);
    }
}
