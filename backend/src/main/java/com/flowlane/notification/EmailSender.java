package com.flowlane.notification;

/** Delivers one email. Implement with SMTP or a provider API for production. */
public interface EmailSender {

    void send(String to, String subject, String body) throws Exception;
}
