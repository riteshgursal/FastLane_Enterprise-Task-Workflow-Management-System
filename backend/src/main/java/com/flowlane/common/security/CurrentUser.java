package com.flowlane.common.security;

import com.flowlane.common.exception.ApiException;
import com.flowlane.common.exception.ErrorCode;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

public final class CurrentUser {

    private CurrentUser() {}

    public static UUID id() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof AuthUser user) {
            return user.id();
        }
        throw new ApiException(ErrorCode.UNAUTHENTICATED);
    }
}
