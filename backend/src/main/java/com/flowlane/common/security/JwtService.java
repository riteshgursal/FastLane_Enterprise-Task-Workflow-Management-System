package com.flowlane.common.security;

import com.flowlane.common.config.FlowlaneProperties;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Component;

/**
 * Minimal HS256 JWT issuer and verifier for short-lived access tokens. The header is fixed,
 * so tokens with any other algorithm (including "none") are rejected.
 */
@Component
public class JwtService {

    private static final String HEADER = b64("{\"alg\":\"HS256\",\"typ\":\"JWT\"}".getBytes(StandardCharsets.UTF_8));
    private static final Pattern SUB = Pattern.compile("\"sub\":\"([0-9a-f-]{36})\"");
    private static final Pattern EXP = Pattern.compile("\"exp\":(\\d+)");
    private static final Pattern ISS = Pattern.compile("\"iss\":\"flowlane\"");

    private final SecretKeySpec key;
    private final Duration ttl;
    private final Clock clock;

    public JwtService(FlowlaneProperties props, Clock clock) {
        String secret = props.jwt().secret();
        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException("JWT_SECRET must be set to at least 32 bytes");
        }
        this.key = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        this.ttl = props.jwt().accessTtl();
        this.clock = clock;
    }

    public long ttlSeconds() {
        return ttl.toSeconds();
    }

    public String issue(UUID userId) {
        long now = clock.instant().getEpochSecond();
        String payload = "{\"sub\":\"" + userId + "\",\"iss\":\"flowlane\",\"iat\":" + now + ",\"exp\":" + (now + ttl.toSeconds()) + "}";
        String body = HEADER + "." + b64(payload.getBytes(StandardCharsets.UTF_8));
        return body + "." + b64(sign(body));
    }

    public Optional<UUID> verify(String token) {
        if (token == null) return Optional.empty();
        String[] parts = token.split("\\.");
        if (parts.length != 3 || !HEADER.equals(parts[0])) return Optional.empty();
        byte[] expected = sign(parts[0] + "." + parts[1]);
        byte[] actual;
        try {
            actual = Base64.getUrlDecoder().decode(parts[2]);
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
        if (!MessageDigest.isEqual(expected, actual)) return Optional.empty();
        String payload;
        try {
            payload = new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
        Matcher sub = SUB.matcher(payload);
        Matcher exp = EXP.matcher(payload);
        if (!sub.find() || !exp.find() || !ISS.matcher(payload).find()) return Optional.empty();
        if (Long.parseLong(exp.group(1)) <= clock.instant().getEpochSecond()) return Optional.empty();
        return Optional.of(UUID.fromString(sub.group(1)));
    }

    private byte[] sign(String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(key);
            return mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String b64(byte[] bytes) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
