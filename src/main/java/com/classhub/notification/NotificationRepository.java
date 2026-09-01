package com.classhub.notification;

import java.time.Instant;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface NotificationRepository extends JpaRepository<Notification, UUID> {

    @Query(
            value = """
                    select n from Notification n
                    where n.recipient.id = :recipientId
                      and (:read is null or n.read = :read)
                    order by n.createdAt desc
                    """,
            countQuery = """
                    select count(n) from Notification n
                    where n.recipient.id = :recipientId
                      and (:read is null or n.read = :read)
                    """)
    Page<Notification> findInbox(
            @Param("recipientId") UUID recipientId,
            @Param("read") Boolean read,
            Pageable pageable);

    long countByRecipientIdAndReadFalse(UUID recipientId);

    boolean existsByTypeAndReferenceIdAndReferenceType(
            NotificationType type, UUID referenceId, String referenceType);

    @Query("""
            select n from Notification n
            where n.id = :id and n.recipient.id = :recipientId
            """)
    java.util.Optional<Notification> findByIdAndRecipientId(
            @Param("id") UUID id, @Param("recipientId") UUID recipientId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update Notification n
            set n.read = true, n.readAt = :readAt
            where n.recipient.id = :recipientId and n.read = false
            """)
    int markAllReadForRecipient(@Param("recipientId") UUID recipientId, @Param("readAt") Instant readAt);
}
