package com.flowlane.notification;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OutboxRepository extends JpaRepository<OutboxEvent, UUID> {

    @Query("select o from OutboxEvent o where o.status = 'PENDING' and o.nextAttemptAt <= :now order by o.nextAttemptAt")
    List<OutboxEvent> due(@Param("now") Instant now, Pageable pageable);
}
