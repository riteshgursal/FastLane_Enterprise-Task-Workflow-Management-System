package com.flowlane.notification;

import com.flowlane.common.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** An email waiting to be sent. Written in the same transaction as the change that caused it. */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "outbox_event")
public class OutboxEvent extends BaseEntity {

    public static final String PENDING = "PENDING";
    public static final String SENT = "SENT";
    public static final String FAILED = "FAILED";

    @Column(nullable = false)
    private String type;

    @Column(nullable = false)
    private String recipientEmail;

    @Column(nullable = false)
    private String subject;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String body;

    @Column(nullable = false)
    private String status = PENDING;

    private int attempts;

    @Column(nullable = false)
    private Instant nextAttemptAt;

    private String lastError;
}
