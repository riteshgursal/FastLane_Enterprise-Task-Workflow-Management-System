package com.flowlane.identity;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public final class AuthDtos {

    private AuthDtos() {}

    public record RegisterRequest(
            @NotBlank @Email @Size(max = 254) String email,
            @NotBlank @Size(min = 12, max = 128) String password,
            @NotBlank @Size(max = 80) String displayName) {}

    public record LoginRequest(@NotBlank @Email String email, @NotBlank String password) {}

    public record ChangePasswordRequest(@NotBlank String currentPassword, @NotBlank @Size(min = 12, max = 128) String newPassword) {}

    public record UpdateProfileRequest(
            @Size(min = 1, max = 80) String displayName,
            @Size(max = 64) String timezone,
            String theme,
            Boolean notifyAssigned,
            Boolean notifyMentions,
            Boolean notifyDue,
            Boolean notifyApprovals,
            Boolean dailyDigest) {}

    public record UserDto(UUID id, String email, String displayName, String initials, String timezone, String theme,
                          boolean notifyAssigned, boolean notifyMentions, boolean notifyDue, boolean notifyApprovals,
                          boolean dailyDigest, long activeSessions) {

        public static UserDto of(User u, long sessions) {
            return new UserDto(u.getId(), u.getEmail(), u.getDisplayName(), u.initials(), u.getTimezone(), u.getTheme(),
                    u.isNotifyAssigned(), u.isNotifyMentions(), u.isNotifyDue(), u.isNotifyApprovals(), u.isDailyDigest(), sessions);
        }
    }

    public record AuthResponse(String accessToken, long expiresIn, UserDto user) {}

    /** Internal result carrying the raw refresh token so the controller can set the cookie. */
    public record Session(AuthResponse body, String refreshToken) {}
}
