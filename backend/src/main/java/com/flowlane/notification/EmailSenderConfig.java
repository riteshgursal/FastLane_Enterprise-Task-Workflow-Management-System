package com.flowlane.notification;

import com.flowlane.common.config.FlowlaneProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class EmailSenderConfig {

    private static final Logger log = LoggerFactory.getLogger(EmailSenderConfig.class);

    @Bean
    public EmailSender emailSender(FlowlaneProperties properties) {
        FlowlaneProperties.Resend resend = properties.resend();
        if (resend != null && resend.apiKey() != null && !resend.apiKey().isBlank()) {
            log.info("Configuring ResendEmailSender with API key");
            return new ResendEmailSender(resend.apiKey(), resend.from());
        }
        log.info("Configuring LoggingEmailSender (no Resend API key configured)");
        return new LoggingEmailSender();
    }
}
