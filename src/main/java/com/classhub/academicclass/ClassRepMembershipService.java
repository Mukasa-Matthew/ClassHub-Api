package com.classhub.academicclass;

import com.classhub.audit.AuditAction;
import com.classhub.audit.AuditEntityTypes;
import com.classhub.audit.AuditService;
import com.classhub.common.api.ErrorCodes;
import com.classhub.common.exception.ApplicationException;
import com.classhub.user.User;
import com.classhub.user.UserService;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ClassRepMembershipService {

    private final AcademicClassRepository academicClassRepository;
    private final ClassMembershipRepository membershipRepository;
    private final ClassMembershipAccessService accessService;
    private final JoinCodeGenerator joinCodeGenerator;
    private final UserService userService;
    private final AuditService auditService;

    public ClassRepMembershipService(
            AcademicClassRepository academicClassRepository,
            ClassMembershipRepository membershipRepository,
            ClassMembershipAccessService accessService,
            JoinCodeGenerator joinCodeGenerator,
            UserService userService,
            AuditService auditService) {
        this.academicClassRepository = academicClassRepository;
        this.membershipRepository = membershipRepository;
        this.accessService = accessService;
        this.joinCodeGenerator = joinCodeGenerator;
        this.userService = userService;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public List<ClassMemberDirectoryResponse> listMembers(UUID classRepUserId, MembershipStatus status, String search) {
        ClassMembership repMembership = accessService.requireClassRepMembership(classRepUserId);
        UUID classId = repMembership.getAcademicClass().getId();
        String normalizedSearch = normalizeSearch(search);

        return membershipRepository.findByClassIdAndStatus(classId, status).stream()
                .filter(m -> matchesSearch(m, normalizedSearch))
                .sorted(Comparator.comparing(
                        (ClassMembership m) -> m.getUser().getLastName(), String.CASE_INSENSITIVE_ORDER)
                        .thenComparing(m -> m.getUser().getFirstName(), String.CASE_INSENSITIVE_ORDER))
                .map(this::toDirectoryResponse)
                .toList();
    }

    @Transactional
    public ClassMemberDirectoryResponse approve(UUID classRepUserId, UUID membershipId) {
        ClassMembership repMembership = accessService.requireClassRepMembership(classRepUserId);
        ClassMembership membership = requireMembership(membershipId);
        requireSameClass(repMembership, membership);
        if (membership.getStatus() != MembershipStatus.PENDING) {
            throw invalidMembershipState("Only pending memberships can be approved");
        }
        User approver = userService.getById(classRepUserId);
        membership.approve(approver, Instant.now());
        ClassMembership saved = membershipRepository.saveAndFlush(membership);
        auditService.record(
                AuditAction.CLASS_MEMBER_APPROVED,
                AuditEntityTypes.CLASS_MEMBERSHIP,
                saved.getId(),
                "Approved class member " + saved.getUser().getEmail());
        return toDirectoryResponse(saved);
    }

    @Transactional
    public ClassMemberDirectoryResponse reject(UUID classRepUserId, UUID membershipId) {
        ClassMembership repMembership = accessService.requireClassRepMembership(classRepUserId);
        ClassMembership membership = requireMembership(membershipId);
        requireSameClass(repMembership, membership);
        if (membership.getStatus() != MembershipStatus.PENDING) {
            throw invalidMembershipState("Only pending memberships can be rejected");
        }
        membership.reject();
        ClassMembership saved = membershipRepository.saveAndFlush(membership);
        auditService.record(
                AuditAction.CLASS_MEMBER_REJECTED,
                AuditEntityTypes.CLASS_MEMBERSHIP,
                saved.getId(),
                "Rejected class member " + saved.getUser().getEmail());
        return toDirectoryResponse(saved);
    }

    @Transactional
    public ClassMemberDirectoryResponse deactivate(UUID classRepUserId, UUID membershipId) {
        ClassMembership repMembership = accessService.requireClassRepMembership(classRepUserId);
        ClassMembership membership = requireMembership(membershipId);
        requireSameClass(repMembership, membership);
        if (membership.getUser().getId().equals(classRepUserId)) {
            throw new ApplicationException(
                    ErrorCodes.FORBIDDEN,
                    "Class representative cannot deactivate themselves",
                    HttpStatus.FORBIDDEN);
        }
        if (membership.getMembershipRole() == MembershipRole.CLASS_REP) {
            throw new ApplicationException(
                    ErrorCodes.FORBIDDEN,
                    "Class representative membership cannot be deactivated",
                    HttpStatus.FORBIDDEN);
        }
        if (membership.getStatus() != MembershipStatus.ACTIVE
                || membership.getMembershipRole() != MembershipRole.STUDENT) {
            throw invalidMembershipState("Only active student memberships can be deactivated");
        }
        membership.deactivate();
        ClassMembership saved = membershipRepository.saveAndFlush(membership);
        auditService.record(
                AuditAction.CLASS_MEMBER_DEACTIVATED,
                AuditEntityTypes.CLASS_MEMBERSHIP,
                saved.getId(),
                "Deactivated class member " + saved.getUser().getEmail());
        return toDirectoryResponse(saved);
    }

    @Transactional
    public ClassMemberDirectoryResponse reactivate(UUID classRepUserId, UUID membershipId) {
        ClassMembership repMembership = accessService.requireClassRepMembership(classRepUserId);
        ClassMembership membership = requireMembership(membershipId);
        requireSameClass(repMembership, membership);
        if (membership.getMembershipRole() == MembershipRole.CLASS_REP) {
            throw new ApplicationException(
                    ErrorCodes.FORBIDDEN,
                    "Class representative membership cannot be reactivated",
                    HttpStatus.FORBIDDEN);
        }
        if (membership.getMembershipRole() != MembershipRole.STUDENT) {
            throw invalidMembershipState("Only student memberships can be reactivated");
        }
        if (membership.getStatus() != MembershipStatus.INACTIVE) {
            throw invalidMembershipState("Only inactive memberships can be reactivated");
        }
        User approver = userService.getById(classRepUserId);
        membership.reactivate(approver, Instant.now());
        ClassMembership saved = membershipRepository.saveAndFlush(membership);
        auditService.record(
                AuditAction.CLASS_MEMBER_REACTIVATED,
                AuditEntityTypes.CLASS_MEMBERSHIP,
                saved.getId(),
                "Reactivated class member " + saved.getUser().getEmail());
        return toDirectoryResponse(saved);
    }

    @Transactional(readOnly = true)
    public ClassListResponse classList(UUID classRepUserId) {
        ClassMembership repMembership = accessService.requireClassRepMembership(classRepUserId);
        AcademicClass academicClass = repMembership.getAcademicClass();
        List<ClassListResponse.ClassListMemberResponse> members =
                membershipRepository.findByClassIdAndStatus(academicClass.getId(), MembershipStatus.ACTIVE)
                        .stream()
                        .sorted(Comparator.comparing(
                                (ClassMembership m) -> m.getUser().getLastName(), String.CASE_INSENSITIVE_ORDER)
                                .thenComparing(m -> m.getUser().getFirstName(), String.CASE_INSENSITIVE_ORDER))
                        .map(m -> new ClassListResponse.ClassListMemberResponse(
                                fullName(m.getUser()),
                                m.getUser().getRegistrationNumber(),
                                m.getUser().getEmail(),
                                m.getUser().getPhoneNumber(),
                                m.getMembershipRole()))
                        .toList();

        return new ClassListResponse(
                new ClassListResponse.AcademicClassSummary(
                        academicClass.getId(),
                        academicClass.getName(),
                        academicClass.getProgrammeName(),
                        academicClass.getProgrammeCode()),
                members);
    }

    @Transactional(readOnly = true)
    public AcademicClassResponse ownClass(UUID classRepUserId) {
        ClassMembership repMembership = accessService.requireClassRepMembership(classRepUserId);
        return AcademicClassResponse.from(repMembership.getAcademicClass(), true);
    }

    @Transactional
    public AcademicClassResponse regenerateJoinCode(UUID classRepUserId) {
        ClassMembership repMembership = accessService.requireClassRepMembership(classRepUserId);
        AcademicClass academicClass = repMembership.getAcademicClass();
        academicClass.setJoinCode(joinCodeGenerator.generateUnique(academicClassRepository));
        AcademicClass saved = academicClassRepository.saveAndFlush(academicClass);
        auditService.record(
                AuditAction.CLASS_JOIN_CODE_REGENERATED,
                AuditEntityTypes.ACADEMIC_CLASS,
                saved.getId(),
                "Regenerated join code for class " + saved.getName());
        return AcademicClassResponse.from(saved, true);
    }

    private ClassMembership requireMembership(UUID membershipId) {
        return membershipRepository
                .findDetailedById(membershipId)
                .orElseThrow(() -> new ApplicationException(
                        ErrorCodes.CLASS_MEMBERSHIP_NOT_FOUND,
                        "Class membership not found",
                        HttpStatus.NOT_FOUND));
    }

    private static void requireSameClass(ClassMembership repMembership, ClassMembership membership) {
        if (!repMembership.getAcademicClass().getId().equals(membership.getAcademicClass().getId())) {
            throw new ApplicationException(
                    ErrorCodes.FORBIDDEN,
                    "Membership belongs to another class",
                    HttpStatus.FORBIDDEN);
        }
    }

    private ClassMemberDirectoryResponse toDirectoryResponse(ClassMembership membership) {
        User user = membership.getUser();
        return new ClassMemberDirectoryResponse(
                user.getId(),
                fullName(user),
                user.getRegistrationNumber(),
                user.getEmail(),
                user.getPhoneNumber(),
                membership.getMembershipRole(),
                membership.getStatus(),
                membership.getRequestedAt(),
                membership.getApprovedAt());
    }

    private static String fullName(User user) {
        return user.getFirstName() + " " + user.getLastName();
    }

    private static boolean matchesSearch(ClassMembership membership, String search) {
        if (search == null) {
            return true;
        }
        User user = membership.getUser();
        String haystack = (user.getFirstName()
                        + " "
                        + user.getLastName()
                        + " "
                        + user.getEmail()
                        + " "
                        + (user.getRegistrationNumber() == null ? "" : user.getRegistrationNumber()))
                .toLowerCase(Locale.ROOT);
        return haystack.contains(search);
    }

    private static String normalizeSearch(String search) {
        if (search == null || search.isBlank()) {
            return null;
        }
        return search.trim().toLowerCase(Locale.ROOT);
    }

    private static ApplicationException invalidMembershipState(String message) {
        return new ApplicationException(
                ErrorCodes.INVALID_CLASS_MEMBERSHIP_STATE, message, HttpStatus.CONFLICT);
    }
}
