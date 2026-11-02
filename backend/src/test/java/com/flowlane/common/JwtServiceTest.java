package com.flowlane.common;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.flowlane.common.config.FlowlaneProperties;
import com.flowlane.common.security.JwtService;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class JwtServiceTest {

    private static FlowlaneProperties props(String secret) {
        return new FlowlaneProperties("http://localhost:5173",
                new FlowlaneProperties.Jwt(secret, Duration.ofMinutes(15), Duration.ofDays(14)),
                new FlowlaneProperties.Cookies(false), new FlowlaneProperties.Storage("./data"),
                new FlowlaneProperties.Resend(null, null));
    }

    private final Clock now = Clock.fixed(Instant.parse("2026-11-02T10:00:00Z"), ZoneOffset.UTC);
    private final JwtService jwt = new JwtService(props("test-secret-that-is-at-least-32-bytes-long"), now);

    @Test
    void issuedTokenVerifies() {
        UUID id = UUID.randomUUID();
        assertEquals(id, jwt.verify(jwt.issue(id)).orElseThrow());
    }

    @Test
    void tamperedTokenIsRejected() {
        String token = jwt.issue(UUID.randomUUID());
        String tampered = token.substring(0, token.length() - 2) + (token.endsWith("A") ? "BB" : "AA");
        assertTrue(jwt.verify(tampered).isEmpty());
    }

    @Test
    void expiredTokenIsRejected() {
        String token = jwt.issue(UUID.randomUUID());
        JwtService later = new JwtService(props("test-secret-that-is-at-least-32-bytes-long"),
                Clock.offset(now, Duration.ofMinutes(16)));
        assertTrue(later.verify(token).isEmpty());
    }

    @Test
    void tokenSignedWithAnotherSecretIsRejected() {
        JwtService other = new JwtService(props("another-secret-that-is-at-least-32-bytes"), now);
        assertTrue(jwt.verify(other.issue(UUID.randomUUID())).isEmpty());
    }
}
