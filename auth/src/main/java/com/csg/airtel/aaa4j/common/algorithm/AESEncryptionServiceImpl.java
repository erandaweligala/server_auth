package com.csg.airtel.aaa4j.common.algorithm;

import jakarta.enterprise.context.ApplicationScoped;
import lombok.SneakyThrows;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@ApplicationScoped
public class AESEncryptionServiceImpl implements EncryptionService{

    // Cipher is NOT thread-safe, so keep one instance per (thread, transformation).
    // It is re-initialised via cipher.init(...) on every call, which fully resets
    // any prior state, so reuse is safe. This avoids a JCA provider lookup
    // (Cipher.getInstance) on every authentication request.
    private static final ThreadLocal<Map<String, Cipher>> CIPHERS =
            ThreadLocal.withInitial(HashMap::new);

    // SecretKeySpec is immutable and thread-safe; cache one per (algorithm,key)
    // instead of allocating a new key (and copying the key bytes) every call.
    private static final ConcurrentHashMap<String, SecretKey> KEY_CACHE = new ConcurrentHashMap<>();

    @Override
    @SneakyThrows
    public String encrypt(String plainText, String algorithm, String secretKeyValue) {
        Cipher cipher = getCipher(algorithm);
        cipher.init(Cipher.ENCRYPT_MODE, getSecretKey(algorithm, secretKeyValue));
        byte[] encryptedBytes = cipher.doFinal(plainText.getBytes());
        return Base64.getEncoder().encodeToString(encryptedBytes);
    }

    @Override
    @SneakyThrows
    public String decrypt(String encryptedText, String algorithm, String secretKeyValue) {
        Cipher cipher = getCipher(algorithm);
        cipher.init(Cipher.DECRYPT_MODE, getSecretKey(algorithm, secretKeyValue));
        byte[] decryptedBytes = cipher.doFinal(Base64.getDecoder().decode(encryptedText));
        return new String(decryptedBytes);
    }

    private SecretKey getSecretKey(String algorithm, String secretKeyValue) {
        return KEY_CACHE.computeIfAbsent(algorithm + '\0' + secretKeyValue,
                k -> new SecretKeySpec(secretKeyValue.getBytes(), algorithm));
    }

    @SneakyThrows
    private Cipher getCipher(String algorithm) {
        Map<String, Cipher> perThread = CIPHERS.get();
        Cipher cipher = perThread.get(algorithm);
        if (cipher == null) {
            cipher = Cipher.getInstance(algorithm);
            perThread.put(algorithm, cipher);
        }
        return cipher;
    }
}
