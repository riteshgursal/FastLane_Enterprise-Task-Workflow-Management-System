package com.flowlane.notification;

import java.time.Instant;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface NotificationRepository extends JpaRepository<Notification, UUID> {

    @Query("select n from Notification n where n.recipientId = :userId"
            + " and (:unreadOnly = false or n.readAt is null)"
            + " and (:type is null or n.type = :type) order by n.createdAt desc")
    Page<Notification> forUser(@Param("userId") UUID userId, @Param("unreadOnly") boolean unreadOnly,
                               @Param("type") NotificationType type, Pageable pageable);

    long countByRecipientIdAndReadAtIsNull(UUID recipientId);

    @Modifying
    @Query("update Notification n set n.readAt = :now where n.recipientId = :userId and n.readAt is null")
    int markAllRead(@Param("userId") UUID userId, @Param("now") Instant now);
}
