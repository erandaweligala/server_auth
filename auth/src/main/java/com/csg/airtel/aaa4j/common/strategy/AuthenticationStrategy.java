package com.csg.airtel.aaa4j.common.strategy;

import com.csg.airtel.aaa4j.domain.model.UserDetails;
import io.smallrye.mutiny.Uni;

public interface AuthenticationStrategy {
    Uni<UserDetails> authenticate(String username,
                                  String inputPassword,
                                  String storedPassword,
                                  String chapChallenge,
                                  String chapPassword,
                                  Integer encryptionMethod);
}

