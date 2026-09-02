package com.classhub.auth;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import jakarta.persistence.LockModeType;

public interface ClassRepAccountSetupRepository extends JpaRepository<ClassRepAccountSetup, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<ClassRepAccountSetup> findByTokenHash(String tokenHash);
    List<ClassRepAccountSetup> findByUserIdAndUsedAtIsNullAndSupersededAtIsNull(UUID userId);
}
