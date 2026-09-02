package com.classhub.academicclass;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ClassMembershipRepository extends JpaRepository<ClassMembership, UUID> {

    Optional<ClassMembership> findByAcademicClassIdAndUserId(UUID academicClassId, UUID userId);

    boolean existsByAcademicClassIdAndUserId(UUID academicClassId, UUID userId);

    @Query("""
            select m from ClassMembership m
            join fetch m.academicClass
            join fetch m.user
            where m.user.id = :userId
            order by m.createdAt desc
            """)
    List<ClassMembership> findAllByUserIdWithClass(@Param("userId") UUID userId);

    @Query("""
            select m from ClassMembership m
            join fetch m.user
            where m.academicClass.id = :classId
              and (:status is null or m.status = :status)
            order by m.user.lastName asc, m.user.firstName asc
            """)
    List<ClassMembership> findByClassIdAndStatus(
            @Param("classId") UUID classId, @Param("status") MembershipStatus status);

    @Query("""
            select m from ClassMembership m
            join fetch m.academicClass
            join fetch m.user
            where m.id = :id
            """)
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<ClassMembership> findDetailedById(@Param("id") UUID id);

    @Query("""
            select m from ClassMembership m
            join fetch m.academicClass
            where m.user.id = :userId and m.status = :status
            """)
    List<ClassMembership> findByUserIdAndStatus(
            @Param("userId") UUID userId, @Param("status") MembershipStatus status);

    @Query("""
            select m.user from ClassMembership m
            where m.academicClass.id = :classId
              and m.status = :status
              and m.membershipRole in :roles
            """)
    List<com.classhub.user.User> findActiveMemberUsersByClassId(
            @Param("classId") UUID classId,
            @Param("status") MembershipStatus status,
            @Param("roles") List<MembershipRole> roles);
}
