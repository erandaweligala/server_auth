package com.csg.airtel.aaa4j.common.strategy;

import com.csg.airtel.aaa4j.domain.constant.Constants;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class AuthenticationStrategyFactory {

    private final ChapAuthStrategy chapAuth;
    private final PapAuthStrategy papAuth;

    public AuthenticationStrategyFactory(ChapAuthStrategy chapAuth, PapAuthStrategy papAuth) {

        this.chapAuth = chapAuth;
        this.papAuth = papAuth;
    }

    public AuthenticationStrategy getStrategy(String protocolType) {
        return switch (protocolType) {
            case Constants.CHAP_PROTOCOL -> chapAuth;
            default -> papAuth;
        };
    }
}

