package com.flowlane.common.security;

import com.flowlane.common.exception.ApiException;
import com.flowlane.common.exception.ErrorCode;
import java.time.Clock;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/**
 * Sliding-window limit for authentication endpoints: 10 attempts per key per 5 minutes.
 * In-memory, so it is per instance; move it to Redis when running more than one instance.
 */
@Component
public class LoginRateLimiter {

    private static final int LIMIT = 10;
    private static final long WINDOW_MS = 5 * 60 * 1000L;

    private final Map<String, Deque<Long>> hits = new ConcurrentHashMap<>();
    private final Clock clock;

    public LoginRateLimiter(Clock clock) {
        this.clock = clock;
    }

    public void check(String key) {
        long now = clock.millis();
        Deque<Long> q = hits.computeIfAbsent(key, k -> new ArrayDeque<>());
        synchronized (q) {
            while (!q.isEmpty() && now - q.peekFirst() > WINDOW_MS) {
                q.pollFirst();
            }
            if (q.size() >= LIMIT) {
                throw new ApiException(ErrorCode.RATE_LIMITED, "Too many attempts. Wait a few minutes and try again.");
            }
            q.addLast(now);
        }
    }
}
