package com.flowlane.identity;

import com.flowlane.common.config.FlowlaneProperties;
import com.flowlane.common.exception.ApiException;
import com.flowlane.common.exception.ErrorCode;
import com.flowlane.common.persistence.UuidV7;
import com.flowlane.common.security.JwtService;
import com.flowlane.common.security.Tokens;
import com.flowlane.identity.AuthDtos.AuthResponse;
import com.flowlane.identity.AuthDtos.LoginRequest;
import com.flowlane.identity.AuthDtos.RegisterRequest;
import com.flowlane.identity.AuthDtos.Session;
import com.flowlane.identity.AuthDtos.UserDto;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import com.flowlane.common.event.DomainEvents.UserRegistered;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private static final int MAX_FAILED_LOGINS = 5;
    private static final Duration LOCKOUT = Duration.ofMinutes(15);

    private final UserRepository users;
    private final RefreshTokenRepository refreshTokens;
    private final PasswordEncoder encoder;
    private final JwtService jwt;
    private final Clock clock;
    private final Duration refreshTtl;
    private final ApplicationEventPublisher events;

    public AuthService(UserRepository users, RefreshTokenRepository refreshTokens, PasswordEncoder encoder,
                       JwtService jwt, Clock clock, FlowlaneProperties props, ApplicationEventPublisher events) {
        this.users = users;
        this.refreshTokens = refreshTokens;
        this.encoder = encoder;
        this.jwt = jwt;
        this.clock = clock;
        this.refreshTtl = props.jwt().refreshTtl();
        this.events = events;
    }

    public Duration refreshTtl() {
        return refreshTtl;
    }

    @Transactional
    public Session register(RegisterRequest req) {
        String email = req.email().trim().toLowerCase();
        if (users.existsByEmailIgnoreCase(email)) {
            throw new ApiException(ErrorCode.EMAIL_TAKEN);
        }
        User user = users.save(new User(email, encoder.encode(req.password()), req.displayName().trim()));
        events.publishEvent(new UserRegistered(user.getId(), user.getEmail(), user.getDisplayName()));
        return startSession(user, UuidV7.next());
    }

    @Transactional(noRollbackFor = ApiException.class)
    public Session login(LoginRequest req) {
        Instant now = clock.instant();
        User user = users.findByEmailIgnoreCase(req.email().trim())
                .orElseThrow(() -> new ApiException(ErrorCode.INVALID_CREDENTIALS));
        if (user.getLockedUntil() != null && user.getLockedUntil().isAfter(now)) {
            throw new ApiException(ErrorCode.ACCOUNT_LOCKED, "Too many failed attempts. Try again after "
                    + user.getLockedUntil() + " UTC or reset your password.");
        }
        if (!user.isEnabled() || !encoder.matches(req.password(), user.getPasswordHash())) {
            user.setFailedLogins(user.getFailedLogins() + 1);
            if (user.getFailedLogins() >= MAX_FAILED_LOGINS) {
                user.setLockedUntil(now.plus(LOCKOUT));
                user.setFailedLogins(0);
            }
            throw new ApiException(ErrorCode.INVALID_CREDENTIALS);
        }
        user.setFailedLogins(0);
        user.setLockedUntil(null);
        return startSession(user, UuidV7.next());
    }

    /**
     * Rotates the refresh token. Presenting a token that was already rotated means it was
     * copied, so the whole token family is revoked and the user must sign in again.
     */
    @Transactional(noRollbackFor = ApiException.class)
    public Session refresh(String rawToken) {
        Instant now = clock.instant();
        if (rawToken == null || rawToken.isBlank()) {
            throw new ApiException(ErrorCode.INVALID_REFRESH_TOKEN);
        }
        RefreshToken current = refreshTokens.findByTokenHash(Tokens.sha256(rawToken))
                .orElseThrow(() -> new ApiException(ErrorCode.INVALID_REFRESH_TOKEN));
        if (current.getRevokedAt() != null && current.getReplacedBy() != null) {
            refreshTokens.revokeFamily(current.getFamilyId(), now);
            throw new ApiException(ErrorCode.INVALID_REFRESH_TOKEN, "Session ended for security. Sign in again.");
        }
        if (!current.isActive(now)) {
            throw new ApiException(ErrorCode.INVALID_REFRESH_TOKEN);
        }
        User user = users.findById(current.getUserId()).orElseThrow(() -> new ApiException(ErrorCode.INVALID_REFRESH_TOKEN));
        Session next = startSession(user, current.getFamilyId());
        RefreshToken replacement = refreshTokens.findByTokenHash(Tokens.sha256(next.refreshToken())).orElseThrow();
        current.setRevokedAt(now);
        current.setReplacedBy(replacement.getId());
        return next;
    }

    @Transactional
    public void logout(String rawToken) {
        if (rawToken == null) return;
        refreshTokens.findByTokenHash(Tokens.sha256(rawToken))
                .ifPresent(t -> refreshTokens.revokeFamily(t.getFamilyId(), clock.instant()));
    }

    @Transactional
    public void revokeOtherSessions(UUID userId, String currentRawToken) {
        Instant now = clock.instant();
        RefreshToken current = currentRawToken == null ? null
                : refreshTokens.findByTokenHash(Tokens.sha256(currentRawToken)).orElse(null);
        if (current == null || !current.getUserId().equals(userId)) {
            refreshTokens.revokeAll(userId, now);
        } else {
            refreshTokens.revokeOtherFamilies(userId, current.getFamilyId(), now);
        }
    }

    private Session startSession(User user, UUID familyId) {
        String raw = Tokens.random();
        refreshTokens.save(new RefreshToken(user.getId(), Tokens.sha256(raw), familyId, clock.instant().plus(refreshTtl)));
        long sessions = refreshTokens.countActiveSessions(user.getId(), clock.instant());
        AuthResponse body = new AuthResponse(jwt.issue(user.getId()), jwt.ttlSeconds(), UserDto.of(user, sessions));
        return new Session(body, raw);
    }
}
