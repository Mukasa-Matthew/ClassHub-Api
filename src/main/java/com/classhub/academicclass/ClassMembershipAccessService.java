package com.classhub.academicclass;

import com.classhub.common.api.ErrorCodes;
import com.classhub.common.exception.ApplicationException;
import com.classhub.security.ClassHubUserDetails;
import com.classhub.user.UserRole;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ClassMembershipAccessService {

    private final ClassMembershipRepository membershipRepository;

    public ClassMembershipAccessService(ClassMembershipRepository membershipRepository) {
        this.membershipRepository = membershipRepository;
    }

    @Transactional(readOnly = true)
    public Optional<ClassMembership> findActiveMembership(UUID userId) {
        List<ClassMembership> memberships =
                membershipRepository.findByUserIdAndStatus(userId, MembershipStatus.ACTIVE);
        return memberships.isEmpty() ? Optional.empty() : Optional.of(memberships.get(0));
    }

    @Transactional(readOnly = true)
    public ClassMembership requireActiveMembership(UUID userId) {
        return findActiveMembership(userId)
                .orElseThrow(() -> new ApplicationException(
                        ErrorCodes.CLASS_MEMBERSHIP_REQUIRED,
                        "Active class membership is required",
                        HttpStatus.FORBIDDEN));
    }

    @Transactional(readOnly = true)
    public UUID requireActiveClassId(UUID userId) {
        return requireActiveMembership(userId).getAcademicClass().getId();
    }

    @Transactional(readOnly = true)
    public ClassMembership requireClassRepMembership(UUID userId) {
        List<ClassMembership> memberships =
                membershipRepository.findByUserIdAndStatus(userId, MembershipStatus.ACTIVE);
        return memberships.stream()
                .filter(m -> m.getMembershipRole() == MembershipRole.CLASS_REP)
                .findFirst()
                .orElseThrow(() -> new ApplicationException(
                        ErrorCodes.FORBIDDEN,
                        "Class representative membership is required",
                        HttpStatus.FORBIDDEN));
    }

    @Transactional(readOnly = true)
    public boolean hasActiveMembershipInClass(UUID userId, UUID classId) {
        return membershipRepository
                .findByAcademicClassIdAndUserId(classId, userId)
                .map(m -> m.getStatus() == MembershipStatus.ACTIVE)
                .orElse(false);
    }

    @Transactional(readOnly = true)
    public void requireCanAccessClass(UUID userId, UUID classId) {
        if (!hasActiveMembershipInClass(userId, classId)) {
            throw new ApplicationException(
                    ErrorCodes.FORBIDDEN,
                    "Class membership is required for this resource",
                    HttpStatus.FORBIDDEN);
        }
    }

    @Transactional(readOnly = true)
    public void requireClassRepForClass(UUID userId, UUID classId) {
        ClassMembership membership = membershipRepository
                .findByAcademicClassIdAndUserId(classId, userId)
                .orElseThrow(() -> new ApplicationException(
                        ErrorCodes.FORBIDDEN,
                        "Class membership is required",
                        HttpStatus.FORBIDDEN));
        if (membership.getStatus() != MembershipStatus.ACTIVE
                || membership.getMembershipRole() != MembershipRole.CLASS_REP) {
            throw new ApplicationException(
                    ErrorCodes.FORBIDDEN,
                    "Class representative access is required",
                    HttpStatus.FORBIDDEN);
        }
    }

  @Transactional(readOnly = true)
    public ClassHubUserDetails currentPrincipal() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null
                || !(authentication.getPrincipal() instanceof ClassHubUserDetails principal)) {
            throw new ApplicationException(
                    ErrorCodes.UNAUTHENTICATED,
                    "Authentication required",
                    HttpStatus.UNAUTHORIZED);
        }
        return principal;
    }

    @Transactional(readOnly = true)
    public UserRole currentRole() {
        return currentPrincipal().getRole();
    }

    public boolean isSuperAdmin(UserRole role) {
        return role == UserRole.SUPER_ADMIN;
    }

    public boolean bypassesMembership(UserRole role) {
        return role == UserRole.SUPER_ADMIN;
    }
}
