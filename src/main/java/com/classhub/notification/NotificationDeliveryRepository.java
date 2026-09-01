package com.classhub.notification;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface NotificationDeliveryRepository extends JpaRepository<NotificationDelivery, UUID> {

    @Query(
            value = """
                    select id from notification_deliveries
                    where status = 'PENDING'
                      and next_attempt_at <= :now
                    order by next_attempt_at asc, created_at asc
                    limit :limit
                    for update skip locked
                    """,
            nativeQuery = true)
    List<UUID> lockPendingIds(@Param("now") Instant now, @Param("limit") int limit);
}
