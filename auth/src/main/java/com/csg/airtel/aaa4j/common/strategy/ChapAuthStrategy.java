package com.csg.airtel.aaa4j.common.strategy;

import com.csg.airtel.aaa4j.common.algorithm.EncryptionService;
import com.csg.airtel.aaa4j.common.util.LoggingUtil;
import com.csg.airtel.aaa4j.domain.constant.Constants;
import com.csg.airtel.aaa4j.domain.constant.ResponseCodeEnum;
import com.csg.airtel.aaa4j.domain.model.UserDetails;
import com.csg.airtel.aaa4j.exception.BaseException;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;

@ApplicationScoped
public class ChapAuthStrategy implements AuthenticationStrategy {
    private static final Logger LOG = Logger.getLogger(ChapAuthStrategy.class);
    private static final String CLASS_NAME = "ChapAuthStrategy";

    private final EncryptionService encryptionService;

    @ConfigProperty(name = "auth.algorithm.method")
    String algorithm;

    @ConfigProperty(name = "auth.AES.secret.key.value")
    String secretKeyValue;

    @Inject
    public ChapAuthStrategy(EncryptionService encryptionService) {
        this.encryptionService = encryptionService;
    }

    @Override
    public Uni<UserDetails> authenticate(String username, String inputPassword, String storedPassword,
                                         String chapChallenge, String chapPassword, Integer encryptionMethod) {
        LoggingUtil.logDebug(LOG, CLASS_NAME, "authenticate",
                "Processing CHAP authentication for user: %s", username);


        return Uni.createFrom().item(() -> {
            if (storedPassword == null || storedPassword.isEmpty()) {
                throw new BaseException("No stored password for user: " + username,
                        ResponseCodeEnum.INVALID_CREDENTIALS.description(),
                        Response.Status.INTERNAL_SERVER_ERROR,
                        ResponseCodeEnum.INVALID_CREDENTIALS.code(), null);
            }

            try {
                String decryptedPassword = storedPassword;
                if(encryptionMethod == 2){
                    decryptedPassword = encryptionService.decrypt(storedPassword, algorithm, secretKeyValue);
                }
                byte[] challengeBytes = hexStringToByteArray(chapChallenge);
                byte[] receivedChapPassword = hexStringToByteArray(chapPassword);

                byte chapId = receivedChapPassword[0];
                byte[] passwordBytes = decryptedPassword.getBytes(StandardCharsets.UTF_8);

                MessageDigest md5 = MessageDigest.getInstance("MD5");
                md5.update(chapId);
                md5.update(passwordBytes);
                md5.update(challengeBytes);

                byte[] expectedDigest = md5.digest();
                byte[] receivedDigest = Arrays.copyOfRange(receivedChapPassword, 1, receivedChapPassword.length);
                boolean authorized = Arrays.equals(expectedDigest, receivedDigest);

                return new UserDetails(username, authorized, true,true, true,null, null);

            } catch (Exception e) {
                LoggingUtil.logError(LOG, CLASS_NAME, "userAuthenticate", e,
                        Constants.CHAP_AUTH_ERROR, username);
                throw new BaseException(Constants.CHAP_AUTH_ERROR,
                        ResponseCodeEnum.AUTHENTICATION_FAILED.description(),
                        Response.Status.INTERNAL_SERVER_ERROR,
                        ResponseCodeEnum.AUTHENTICATION_FAILED.code(), null);
            }
        });
    }

    private static byte[] hexStringToByteArray(String s) {
        int len = s.length();
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(s.charAt(i), 16) << 4)
                    + Character.digit(s.charAt(i + 1), 16));
        }
        return data;
    }
}
