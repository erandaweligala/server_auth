package com.csg.airtel.aaa4j.common.algorithm;

public interface EncryptionService {
    String encrypt(String plainText, String algorithm, String secretKeyValue);
    String decrypt(String encryptedText, String algorithm, String secretKeyValue);
}
