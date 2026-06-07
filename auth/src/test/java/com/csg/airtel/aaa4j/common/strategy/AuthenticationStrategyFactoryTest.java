package com.csg.airtel.aaa4j.common.strategy;

import com.csg.airtel.aaa4j.domain.constant.Constants;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import static org.junit.jupiter.api.Assertions.*;

class AuthenticationStrategyFactoryTest {


    @Mock
    private ChapAuthStrategy chapAuthStrategy;

    @Mock
    private PapAuthStrategy papAuthStrategy;

    private AuthenticationStrategyFactory factory;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        factory = new AuthenticationStrategyFactory(chapAuthStrategy, papAuthStrategy);
    }

    @Test
    void testGetChapStrategy() {
        AuthenticationStrategy strategy = factory.getStrategy(Constants.CHAP_PROTOCOL);
        assertEquals(chapAuthStrategy, strategy);
    }

    @Test
    void testGetPapStrategyDefault() {
        AuthenticationStrategy strategy = factory.getStrategy("PAP");
        assertEquals(papAuthStrategy, strategy);
    }

    @Test
    void testGetPapStrategyForUnknownProtocol() {
        AuthenticationStrategy strategy = factory.getStrategy("UNKNOWN");
        assertEquals(papAuthStrategy, strategy);
    }

}
