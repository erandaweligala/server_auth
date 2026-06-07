package com.csg.airtel.aaa4j.common.strategy;

import com.csg.airtel.aaa4j.common.util.LoggingUtil;
import com.csg.airtel.aaa4j.common.algorithm.EncryptionService;
import com.csg.airtel.aaa4j.domain.model.UserDetails;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

@ApplicationScoped
public class PapAuthStrategy implements AuthenticationStrategy {
    private static final Logger LOG = Logger.getLogger(PapAuthStrategy.class);
    private static final String CLASS_NAME = "PapAuthStrategy";

    private final EncryptionService encryptionService;

    @ConfigProperty(name = "auth.algorithm.method")
    String algorithm;

    @ConfigProperty(name = "auth.AES.secret.key.value")
    String secretKeyValue;

    @Inject
    public PapAuthStrategy(EncryptionService encryptionService) {
        this.encryptionService = encryptionService;
    }

    @Override
    public Uni<UserDetails> authenticate(String username, String inputPassword, String storedPassword,
                                         String chapChallenge, String chapPassword, Integer encryptionMethod) {
        LoggingUtil.logInfo(LOG, CLASS_NAME, "authenticate",
                "Processing PAP authentication for user: %s", username);

        String decryptedPassword;
        boolean isAuthorized;
        if(encryptionMethod == 2){
            decryptedPassword = encryptionService.decrypt(storedPassword, algorithm, secretKeyValue);
            isAuthorized = decryptedPassword != null && decryptedPassword.equals(inputPassword);
        } else if (encryptionMethod == 1) {
            decryptedPassword = hashMD5(inputPassword);
            isAuthorized = decryptedPassword != null && decryptedPassword.equals(storedPassword);
        }else {
            isAuthorized = inputPassword != null && inputPassword.equals(storedPassword);
        }
        return Uni.createFrom().item(new UserDetails(username, isAuthorized, true, true, true,null, null));
    }

    private String hashMD5(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] messageDigest = md.digest(input.getBytes());
            StringBuilder hexString = new StringBuilder();
            for (byte b : messageDigest) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            LoggingUtil.logError(LOG, CLASS_NAME, "authenticate",e,"MD5 algorithm not found");
            return null;
        }
    }
}

