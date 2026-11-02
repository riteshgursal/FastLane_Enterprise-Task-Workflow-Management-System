package com.flowlane.common.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "flowlane")
public record FlowlaneProperties(String frontendOrigin, Jwt jwt, Cookies cookies, Storage storage, Resend resend) {

    public record Jwt(String secret, Duration accessTtl, Duration refreshTtl) {}

    public record Cookies(boolean secure) {}

    public record Storage(String dir) {}

    public record Resend(String apiKey, String from) {}
}
