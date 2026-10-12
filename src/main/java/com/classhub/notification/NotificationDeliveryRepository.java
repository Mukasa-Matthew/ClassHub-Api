package com.classhub.notification;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Modifying;
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

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
            update notification_deliveries d
            set status = 'SKIPPED',
                next_attempt_at = null,
                last_error_code = 'SETUP_TOKEN_INVALIDATED',
                last_error_message = 'Account setup link is no longer active',
                updated_at = :now
            from notifications n
            where d.notification_id = n.id
              and n.type = 'ACCOUNT_SETUP'
              and n.reference_id = :setupId
              and d.status in ('PENDING', 'FAILED')
            """, nativeQuery = true)
    int skipPendingAccountSetupDeliveries(@Param("setupId") UUID setupId, @Param("now") Instant now);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
            update notification_deliveries d
            set status = 'SKIPPED',
                next_attempt_at = null,
                last_error_code = 'RESET_OTP_INVALIDATED',
                last_error_message = 'Password reset code is no longer active',
                updated_at = :now
            from notifications n
            where d.notification_id = n.id
              and n.type = 'PASSWORD_RESET_OTP'
              and n.reference_id = :challengeId
              and d.status in ('PENDING', 'FAILED')
            """, nativeQuery = true)
    int skipPendingPasswordResetDeliveries(@Param("challengeId") UUID challengeId, @Param("now") Instant now);
}
