package com.classhub.notification.push;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PushSubscriptionRepository extends JpaRepository<PushSubscription, UUID> {
    Optional<PushSubscription> findByEndpointHash(String endpointHash);
    boolean existsByUserId(UUID userId);
    boolean existsByUserIdAndEndpointHash(UUID userId, String endpointHash);
    long countByUserId(UUID userId);
    List<PushSubscription> findByUserId(UUID userId);
}
