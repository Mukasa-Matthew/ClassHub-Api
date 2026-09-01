package com.classhub.user;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserRepository extends JpaRepository<User, UUID> {

    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    boolean existsByRegistrationNumberIgnoreCase(String registrationNumber);

    List<User> findByStatus(UserStatus status);

    List<User> findByRole(UserRole role);

    List<User> findByRoleAndStatus(UserRole role, UserStatus status);

    boolean existsByRole(UserRole role);

    long countByRole(UserRole role);

    long countByRoleAndStatus(UserRole role, UserStatus status);

    long countByStatus(UserStatus status);

    @Query(
            value = """
                    select u from User u
                    where (:role is null or u.role = :role)
                      and (:status is null or u.status = :status)
                    order by u.createdAt desc
                    """,
            countQuery = """
                    select count(u) from User u
                    where (:role is null or u.role = :role)
                      and (:status is null or u.status = :status)
                    """)
    Page<User> search(
            @Param("role") UserRole role,
            @Param("status") UserStatus status,
            Pageable pageable);
}
