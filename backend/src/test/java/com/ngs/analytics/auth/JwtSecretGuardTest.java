package com.ngs.analytics.auth;

import com.ngs.analytics.common.NgsProperties;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtSecretGuardTest {

    private final NgsProperties properties = new NgsProperties();
    private final JwtSecretGuard guard = new JwtSecretGuard(properties);

    @Test
    void refusesToStartWithAuthOnAndThePublicDefaultSecret() {
        properties.getAuth().setDisabled(false);
        properties.getJwt().setSecret(NgsProperties.Jwt.PUBLIC_DEFAULT_SECRET);

        assertThatThrownBy(guard::afterSingletonsInstantiated)
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("NGS_JWT_SECRET");
    }

    @Test
    void startsWithACustomSecretOrWhenAuthIsOff() {
        properties.getAuth().setDisabled(false);
        properties.getJwt().setSecret("a-unique-secret-that-is-at-least-32-bytes-long");
        assertThatCode(guard::afterSingletonsInstantiated).doesNotThrowAnyException();

        properties.getAuth().setDisabled(true);
        properties.getJwt().setSecret(NgsProperties.Jwt.PUBLIC_DEFAULT_SECRET);
        assertThatCode(guard::afterSingletonsInstantiated).doesNotThrowAnyException();
    }
}
