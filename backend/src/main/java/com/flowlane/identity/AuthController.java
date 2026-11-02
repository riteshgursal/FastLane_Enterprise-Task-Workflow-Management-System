package com.flowlane.identity;

import com.flowlane.common.config.FlowlaneProperties;
import com.flowlane.common.exception.ApiException;
import com.flowlane.common.exception.ErrorCode;
import com.flowlane.common.security.CurrentUser;
import com.flowlane.common.security.LoginRateLimiter;
import com.flowlane.identity.AuthDtos.AuthResponse;
import com.flowlane.identity.AuthDtos.LoginRequest;
import com.flowlane.identity.AuthDtos.RegisterRequest;
import com.flowlane.identity.AuthDtos.Session;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import java.time.Duration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    static final String COOKIE = "fl_refresh";
    private static final String CSRF_HEADER = "X-Flowlane-Csrf";

    private final AuthService auth;
    private final LoginRateLimiter limiter;
    private final boolean secureCookie;

    public AuthController(AuthService auth, LoginRateLimiter limiter, FlowlaneProperties props) {
        this.auth = auth;
        this.limiter = limiter;
        this.secureCookie = props.cookies().secure();
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public AuthResponse register(@Valid @RequestBody RegisterRequest req, HttpServletRequest http, HttpServletResponse res) {
        limiter.check("register:" + http.getRemoteAddr());
        return withCookie(auth.register(req), res);
    }

    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest req, HttpServletRequest http, HttpServletResponse res) {
        limiter.check("login-ip:" + http.getRemoteAddr());
        limiter.check("login-email:" + req.email().toLowerCase());
        return withCookie(auth.login(req), res);
    }

    @PostMapping("/refresh")
    public AuthResponse refresh(@CookieValue(name = COOKIE, required = false) String token,
                                @RequestHeader(name = CSRF_HEADER, required = false) String csrf,
                                HttpServletResponse res) {
        requireCsrf(csrf);
        try {
            return withCookie(auth.refresh(token), res);
        } catch (ApiException e) {
            clearCookie(res);
            throw e;
        }
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(@CookieValue(name = COOKIE, required = false) String token,
                       @RequestHeader(name = CSRF_HEADER, required = false) String csrf,
                       HttpServletResponse res) {
        requireCsrf(csrf);
        auth.logout(token);
        clearCookie(res);
    }

    @PostMapping("/sessions/revoke-others")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void revokeOthers(@CookieValue(name = COOKIE, required = false) String token) {
        auth.revokeOtherSessions(CurrentUser.id(), token);
    }

    private void requireCsrf(String csrf) {
        if (!"1".equals(csrf)) {
            throw new ApiException(ErrorCode.FORBIDDEN, "Missing X-Flowlane-Csrf header.");
        }
    }

    private AuthResponse withCookie(Session session, HttpServletResponse res) {
        res.addHeader(HttpHeaders.SET_COOKIE, cookie(session.refreshToken(), auth.refreshTtl()).toString());
        return session.body();
    }

    private void clearCookie(HttpServletResponse res) {
        res.addHeader(HttpHeaders.SET_COOKIE, cookie("", Duration.ZERO).toString());
    }

    private ResponseCookie cookie(String value, Duration maxAge) {
        return ResponseCookie.from(COOKIE, value)
                .httpOnly(true)
                .secure(secureCookie)
                .sameSite("Strict")
                .path("/api/v1/auth")
                .maxAge(maxAge)
                .build();
    }
}
