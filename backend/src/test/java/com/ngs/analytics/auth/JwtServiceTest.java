package com.ngs.analytics.auth;

import com.ngs.analytics.common.NgsProperties;
import com.ngs.analytics.domain.Role;
import com.ngs.analytics.domain.UserAccount;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class JwtServiceTest {

    private static final String SECRET = "change-me-ngs-analytics-platform-jwt-secret-key-32bytes-min";
    private static final String OTHER_SECRET = "other-secret-ngs-analytics-platform-jwt-key-32bytes-min!!";

    @Test
    void roundTripValidToken() {
        JwtService jwt = service(SECRET, 60_000);
        UserAccount user = user();
        String token = jwt.generateToken(user);

        assertTrue(jwt.isValid(token));
        assertEquals(user.getId(), jwt.extractUserId(token));
        assertEquals(Role.RESEARCHER, jwt.extractRole(token));
    }

    @Test
    void garbageTokenIsInvalid() {
        assertFalse(service(SECRET, 60_000).isValid("not-a-jwt"));
    }

    @Test
    void tokenSignedWithOtherSecretIsInvalid() {
        UserAccount user = user();
        String token = service(SECRET, 60_000).generateToken(user);
        assertFalse(service(OTHER_SECRET, 60_000).isValid(token));
    }

    private static JwtService service(String secret, long expirationMs) {
        NgsProperties properties = new NgsProperties();
        properties.getJwt().setSecret(secret);
        properties.getJwt().setExpirationMs(expirationMs);
        return new JwtService(properties);
    }

    private static UserAccount user() {
        UserAccount user = new UserAccount();
        user.setId(UUID.fromString("11111111-1111-1111-1111-111111111111"));
        user.setEmail("researcher@example.com");
        user.setRole(Role.RESEARCHER);
        return user;
    }
}
