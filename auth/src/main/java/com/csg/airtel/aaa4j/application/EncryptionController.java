package com.csg.airtel.aaa4j.application;

import com.csg.airtel.aaa4j.common.algorithm.AESEncryptionServiceImpl;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.config.inject.ConfigProperty;

@Path("/encrypt")
@ApplicationScoped
public class EncryptionController {

    private final AESEncryptionServiceImpl encryptionService;

    @ConfigProperty(name = "auth.algorithm.method")
    String algorithm;

    @ConfigProperty(name = "auth.AES.secret.key.value")
    String secretKeyValue;

    @Inject
    public EncryptionController(AESEncryptionServiceImpl encryptionService) {
        this.encryptionService = encryptionService;
    }

    @POST
    @Consumes(MediaType.TEXT_PLAIN)
    @Produces(MediaType.TEXT_PLAIN)
    public String encryptPassword(@QueryParam("plainPassword") String plainPassword) {
        return encryptionService.encrypt(plainPassword, algorithm, secretKeyValue);
    }
}
