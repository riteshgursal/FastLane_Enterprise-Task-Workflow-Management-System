package com.flowlane.identity;

import com.flowlane.common.exception.ApiException;
import com.flowlane.common.exception.ErrorCode;
import com.flowlane.identity.AuthDtos.ChangePasswordRequest;
import com.flowlane.identity.AuthDtos.UpdateProfileRequest;
import com.flowlane.identity.AuthDtos.UserDto;
import java.time.Clock;
import java.time.DateTimeException;
import java.time.ZoneId;
import java.util.Set;
import java.util.UUID;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserService {

    private static final Set<String> THEMES = Set.of("dark", "light", "system");

    private final UserRepository users;
    private final RefreshTokenRepository refreshTokens;
    private final PasswordEncoder encoder;
    private final Clock clock;

    public UserService(UserRepository users, RefreshTokenRepository refreshTokens, PasswordEncoder encoder, Clock clock) {
        this.users = users;
        this.refreshTokens = refreshTokens;
        this.encoder = encoder;
        this.clock = clock;
    }

    public User require(UUID id) {
        return users.findById(id).orElseThrow(() -> ApiException.notFound("User"));
    }

    @Transactional(readOnly = true)
    public UserDto me(UUID id) {
        return UserDto.of(require(id), refreshTokens.countActiveSessions(id, clock.instant()));
    }

    @Transactional
    public UserDto update(UUID id, UpdateProfileRequest req) {
        User u = require(id);
        if (req.displayName() != null) u.setDisplayName(req.displayName().trim());
        if (req.timezone() != null) {
            try {
                ZoneId.of(req.timezone());
            } catch (DateTimeException e) {
                throw new ApiException(ErrorCode.VALIDATION_FAILED, "Unknown time zone.");
            }
            u.setTimezone(req.timezone());
        }
        if (req.theme() != null) {
            if (!THEMES.contains(req.theme())) throw new ApiException(ErrorCode.VALIDATION_FAILED, "Theme must be dark, light or system.");
            u.setTheme(req.theme());
        }
        if (req.notifyAssigned() != null) u.setNotifyAssigned(req.notifyAssigned());
        if (req.notifyMentions() != null) u.setNotifyMentions(req.notifyMentions());
        if (req.notifyDue() != null) u.setNotifyDue(req.notifyDue());
        if (req.notifyApprovals() != null) u.setNotifyApprovals(req.notifyApprovals());
        if (req.dailyDigest() != null) u.setDailyDigest(req.dailyDigest());
        return UserDto.of(u, refreshTokens.countActiveSessions(id, clock.instant()));
    }

    @Transactional
    public void changePassword(UUID id, ChangePasswordRequest req) {
        User u = require(id);
        if (!encoder.matches(req.currentPassword(), u.getPasswordHash())) {
            throw new ApiException(ErrorCode.INVALID_CREDENTIALS, "Current password is incorrect.");
        }
        u.setPasswordHash(encoder.encode(req.newPassword()));
        refreshTokens.revokeAll(id, clock.instant());
    }
}
