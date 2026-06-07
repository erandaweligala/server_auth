package com.csg.airtel.aaa4j.common.algorithm;

import jakarta.enterprise.context.ApplicationScoped;
import lombok.SneakyThrows;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.util.Base64;

@ApplicationScoped
public class AESEncryptionServiceImpl implements EncryptionService{
    @Override
    @SneakyThrows
    public String encrypt(String plainText, String algorithm, String secretKeyValue) {
        Cipher cipher = getCipher(algorithm);
        SecretKey secretKey = getSecretKey(algorithm, secretKeyValue);
        cipher.init(Cipher.ENCRYPT_MODE, secretKey);
        byte[] encryptedBytes = cipher.doFinal(plainText.getBytes());
        return Base64.getEncoder().encodeToString(encryptedBytes);
    }

    @Override
    @SneakyThrows
    public String decrypt(String encryptedText, String algorithm, String secretKeyValue) {
        Cipher cipher = getCipher(algorithm);
        SecretKey secretKey = getSecretKey(algorithm, secretKeyValue);
        cipher.init(Cipher.DECRYPT_MODE, secretKey);
        byte[] decryptedBytes = cipher.doFinal(Base64.getDecoder().decode(encryptedText));
        return new String(decryptedBytes);
    }

    private SecretKey getSecretKey(String algorithm, String secretKeyValue) {
        return new SecretKeySpec(secretKeyValue.getBytes(), algorithm);
    }

    @SneakyThrows
    private Cipher getCipher(String algorithm) {
        return Cipher.getInstance(algorithm);
    }
}
