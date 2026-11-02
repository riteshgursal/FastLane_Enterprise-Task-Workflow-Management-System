package com.flowlane.notification;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** Development sender: logs instead of sending. */
public class LoggingEmailSender implements EmailSender {

    private static final Logger log = LoggerFactory.getLogger(LoggingEmailSender.class);

    @Override
    public void send(String to, String subject, String body) {
        log.info("Email to {}: {}\n{}", to, subject, body);
    }
}
