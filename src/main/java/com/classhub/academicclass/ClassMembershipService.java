package com.classhub.academicclass;

import com.classhub.auth.AuthenticatedUserResponse;
import com.classhub.auth.AuthService;
import com.classhub.auth.RegisterRequest;
import com.classhub.common.api.ErrorCodes;
import com.classhub.common.exception.ApplicationException;
import com.classhub.user.CreateUserCommand;
import com.classhub.user.User;
import com.classhub.user.UserRole;
import com.classhub.user.UserService;
import com.classhub.user.UserStatus;
import java.time.Instant;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ClassMembershipService {

    private final AcademicClassService academicClassService;
    private final ClassMembershipRepository membershipRepository;
    private final UserService userService;

    public ClassMembershipService(
            AcademicClassService academicClassService,
            ClassMembershipRepository membershipRepository,
            UserService userService) {
        this.academicClassService = academicClassService;
        this.membershipRepository = membershipRepository;
        this.userService = userService;
    }

    @Transactional
    public AuthenticatedUserResponse register(RegisterRequest request) {
        AcademicClass academicClass = academicClassService.requireActiveClassByJoinCode(request.classJoinCode());
        String email = UserService.normalizeEmail(request.email());
        String registrationNumber = UserService.requireRegistrationNumber(request.registrationNumber());
        NameParts nameParts = splitFullName(request.fullName());

        if (userService.existsByEmail(email) || userService.existsByRegistrationNumber(registrationNumber)) {
            throw registrationConflict();
        }

        try {
            User user = userService.create(new CreateUserCommand(
                    nameParts.firstName(),
                    nameParts.lastName(),
                    email,
                    null,
                    request.password(),
                    UserRole.STUDENT,
                    UserStatus.ACTIVE,
                    false,
                    registrationNumber));

            createPendingMembership(academicClass, user);
            return AuthService.toResponse(user);
        } catch (ApplicationException ex) {
            if (ErrorCodes.USER_ALREADY_EXISTS.equals(ex.getErrorCode())) {
                throw registrationConflict();
            }
            throw ex;
        } catch (DataIntegrityViolationException ex) {
            throw registrationConflict();
        }
    }

    @Transactional
    public ClassMembershipResponse joinExistingUser(User user, JoinClassRequest request) {
        if (user.getRole() != UserRole.STUDENT) {
            throw new ApplicationException(
                    ErrorCodes.FORBIDDEN,
                    "Only students can request class membership",
                    HttpStatus.FORBIDDEN);
        }
        AcademicClass academicClass = academicClassService.requireActiveClassByJoinCode(request.joinCode());
        if (membershipRepository.existsByAcademicClassIdAndUserId(academicClass.getId(), user.getId())) {
            throw membershipConflict();
        }
        ClassMembership membership = createPendingMembership(academicClass, user);
        return ClassMembershipResponse.from(membership);
    }

    @Transactional(readOnly = true)
    public ClassMembershipResponse currentMembership(User user) {
        return membershipRepository.findAllByUserIdWithClass(user.getId()).stream()
                .findFirst()
                .map(ClassMembershipResponse::from)
                .orElseThrow(() -> new ApplicationException(
                        ErrorCodes.CLASS_MEMBERSHIP_NOT_FOUND,
                        "Class membership not found",
                        HttpStatus.NOT_FOUND));
    }

    private ClassMembership createPendingMembership(AcademicClass academicClass, User user) {
        ClassMembership membership = new ClassMembership(
                academicClass,
                user,
                MembershipRole.STUDENT,
                MembershipStatus.PENDING,
                Instant.now());
        try {
            return membershipRepository.saveAndFlush(membership);
        } catch (DataIntegrityViolationException ex) {
            throw membershipConflict();
        }
    }

    private static ApplicationException registrationConflict() {
        return new ApplicationException(
                ErrorCodes.USER_ALREADY_EXISTS,
                "Registration could not be completed",
                HttpStatus.CONFLICT);
    }

    private static ApplicationException membershipConflict() {
        return new ApplicationException(
                ErrorCodes.CLASS_MEMBERSHIP_ALREADY_EXISTS,
                "Membership request already exists for this class",
                HttpStatus.CONFLICT);
    }

    static NameParts splitFullName(String fullName) {
        String trimmed = fullName.trim().replaceAll("\\s+", " ");
        if (trimmed.isEmpty()) {
            throw new ApplicationException(
                    ErrorCodes.INVALID_USER_DATA,
                    "fullName is required",
                    HttpStatus.BAD_REQUEST);
        }
        int lastSpace = trimmed.lastIndexOf(' ');
        if (lastSpace < 0) {
            return new NameParts(trimmed, "");
        }
        return new NameParts(trimmed.substring(0, lastSpace), trimmed.substring(lastSpace + 1));
    }

    record NameParts(String firstName, String lastName) {
    }
}
