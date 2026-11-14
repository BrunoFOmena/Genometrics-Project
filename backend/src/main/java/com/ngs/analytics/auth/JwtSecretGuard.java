package com.ngs.analytics.auth;

import com.ngs.analytics.common.NgsProperties;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

/** Refuses to start with auth enabled and a missing, short or public-default JWT secret: anyone could forge tokens. */
@Component
public class JwtSecretGuard implements SmartInitializingSingleton {

    private final NgsProperties properties;

    public JwtSecretGuard(NgsProperties properties) {
        this.properties = properties;
    }

    @Override
    public void afterSingletonsInstantiated() {
        if (properties.getAuth().isDisabled()) {
            return;
        }
        String secret = properties.getJwt().getSecret();
        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < 32
                || NgsProperties.Jwt.PUBLIC_DEFAULT_SECRET.equals(secret)) {
            throw new IllegalStateException(
                    "Auth is enabled but NGS_JWT_SECRET is missing, shorter than 32 bytes or the public default. "
                            + "Set a unique secret of at least 32 bytes.");
        }
    }
}
