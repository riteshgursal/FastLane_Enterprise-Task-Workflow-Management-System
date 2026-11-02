package com.flowlane.notification;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Sends queued emails with exponential backoff; gives up after 8 attempts. */
@Component
public class OutboxWorker {

    private static final Logger log = LoggerFactory.getLogger(OutboxWorker.class);
    private static final int MAX_ATTEMPTS = 8;

    private final OutboxRepository outbox;
    private final EmailSender sender;
    private final Clock clock;

    public OutboxWorker(OutboxRepository outbox, EmailSender sender, Clock clock) {
        this.outbox = outbox;
        this.sender = sender;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "PT30S")
    @Transactional
    public void drain() {
        Instant now = clock.instant();
        for (OutboxEvent e : outbox.due(now, PageRequest.of(0, 50))) {
            try {
                sender.send(e.getRecipientEmail(), e.getSubject(), e.getBody());
                e.setStatus(OutboxEvent.SENT);
            } catch (Exception ex) {
                e.setAttempts(e.getAttempts() + 1);
                e.setLastError(String.valueOf(ex.getMessage()).substring(0, Math.min(500, String.valueOf(ex.getMessage()).length())));
                if (e.getAttempts() >= MAX_ATTEMPTS) {
                    e.setStatus(OutboxEvent.FAILED);
                    log.warn("Giving up on email {} to {}", e.getId(), e.getRecipientEmail());
                } else {
                    e.setNextAttemptAt(now.plus(Duration.ofSeconds(30L << e.getAttempts())));
                }
            }
        }
    }
}
