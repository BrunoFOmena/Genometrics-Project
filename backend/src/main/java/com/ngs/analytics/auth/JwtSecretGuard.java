package com.ngs.analytics.auth;

import com.ngs.analytics.common.NgsProperties;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.stereotype.Component;

/** Refuses to start with auth enabled and the public default JWT secret: anyone could forge tokens. */
@Component
public class JwtSecretGuard implements SmartInitializingSingleton {

    private final NgsProperties properties;

    public JwtSecretGuard(NgsProperties properties) {
        this.properties = properties;
    }

    @Override
    public void afterSingletonsInstantiated() {
        if (!properties.getAuth().isDisabled()
                && NgsProperties.Jwt.PUBLIC_DEFAULT_SECRET.equals(properties.getJwt().getSecret())) {
            throw new IllegalStateException(
                    "Auth is enabled but NGS_JWT_SECRET is the public default. Set a unique secret of at least 32 bytes.");
        }
    }
}
