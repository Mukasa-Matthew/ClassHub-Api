package com.classhub.auth;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

public interface PasswordResetChallengeRepository extends JpaRepository<PasswordResetChallenge, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<PasswordResetChallenge> findFirstByUserIdAndSupersededAtIsNullOrderByRequestedAtDesc(UUID userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<PasswordResetChallenge> findByResetTokenHash(String resetTokenHash);

    List<PasswordResetChallenge> findByUserIdAndSupersededAtIsNullAndResetUsedAtIsNull(UUID userId);
}
