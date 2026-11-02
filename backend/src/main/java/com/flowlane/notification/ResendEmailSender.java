package com.flowlane.notification;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Delivers emails using the Resend REST API (https://api.resend.com/emails). */
public class ResendEmailSender implements EmailSender {

    private static final Logger log = LoggerFactory.getLogger(ResendEmailSender.class);
    private static final String RESEND_URL = "https://api.resend.com/emails";

    private final String apiKey;
    private final String from;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public ResendEmailSender(String apiKey, String from) {
        this.apiKey = apiKey.trim();
        this.from = (from != null && !from.isBlank()) ? from.trim() : "Flowlane <onboarding@resend.dev>";
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        log.info("Initialized ResendEmailSender with sender: {}", this.from);
    }

    @Override
    public void send(String to, String subject, String body) throws Exception {
        Map<String, Object> payload = Map.of(
                "from", from,
                "to", List.of(to),
                "subject", subject,
                "text", body
        );

        String json = objectMapper.writeValueAsString(payload);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(RESEND_URL))
                .timeout(Duration.ofSeconds(15))
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() >= 200 && response.statusCode() < 300) {
            log.info("Sent email via Resend to {}: {} (status: {})", to, subject, response.statusCode());
        } else {
            log.error("Failed to send email via Resend to {}: status={}, body={}", to, response.statusCode(), response.body());
            throw new RuntimeException("Resend API failed with status " + response.statusCode() + ": " + response.body());
        }
    }
}
