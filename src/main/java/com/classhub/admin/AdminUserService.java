package com.classhub.admin;

import com.classhub.audit.AuditAction;
import com.classhub.audit.AuditEntityTypes;
import com.classhub.audit.AuditService;
import com.classhub.common.api.ErrorCodes;
import com.classhub.common.api.Pagination;
import com.classhub.common.exception.ApplicationException;
import com.classhub.security.ClassHubUserDetails;
import com.classhub.user.CreateUserCommand;
import com.classhub.user.User;
import com.classhub.user.UserRepository;
import com.classhub.user.UserRole;
import com.classhub.user.UserService;
import com.classhub.user.UserStatus;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdminUserService {

    private static final int DEFAULT_PAGE = 1;
    private static final int DEFAULT_SIZE = 20;
    private static final int MAX_SIZE = 100;

    private final UserService userService;
    private final UserRepository userRepository;
    private final AuditService auditService;

    public AdminUserService(
            UserService userService, UserRepository userRepository, AuditService auditService) {
        this.userService = userService;
        this.userRepository = userRepository;
        this.auditService = auditService;
    }

    @Transactional
    public UserResponse createUser(CreateUserRequest request) {
        if (request.role() == UserRole.SUPER_ADMIN) {
            throw new ApplicationException(
                    ErrorCodes.INVALID_ROLE_CHANGE,
                    "Creating SUPER_ADMIN users is not allowed through this endpoint",
                    HttpStatus.BAD_REQUEST);
        }
        if (request.role() != UserRole.CLASS_REP && request.role() != UserRole.STUDENT) {
            throw new ApplicationException(
                    ErrorCodes.INVALID_ROLE_CHANGE,
                    "Only CLASS_REP or STUDENT can be created",
                    HttpStatus.BAD_REQUEST);
        }

        User created = userService.create(new CreateUserCommand(
                request.firstName(),
                request.lastName(),
                request.email(),
                request.phoneNumber(),
                request.password(),
                request.role(),
                UserStatus.ACTIVE,
                false));

        auditService.record(
                AuditAction.USER_CREATED,
                AuditEntityTypes.USER,
                created.getId(),
                "Created " + created.getRole() + " user " + created.getEmail());

        return UserResponse.from(created);
    }

    @Transactional(readOnly = true)
    public ApiPage listUsers(Integer page, Integer size, UserRole role, UserStatus status) {
        int pageNumber = page == null ? DEFAULT_PAGE : page;
        int pageSize = size == null ? DEFAULT_SIZE : size;
        if (pageNumber < 1) {
            throw new ApplicationException(
                    ErrorCodes.INVALID_USER_DATA,
                    "page must be >= 1",
                    HttpStatus.BAD_REQUEST);
        }
        if (pageSize < 1 || pageSize > MAX_SIZE) {
            throw new ApplicationException(
                    ErrorCodes.INVALID_USER_DATA,
                    "size must be between 1 and " + MAX_SIZE,
                    HttpStatus.BAD_REQUEST);
        }

        PageRequest pageable = PageRequest.of(pageNumber - 1, pageSize);
        Page<User> result = userRepository.search(role, status, pageable);
        List<UserResponse> data = result.getContent().stream().map(UserResponse::from).toList();
        Pagination pagination = new Pagination(
                pageNumber,
                pageSize,
                result.getTotalElements(),
                result.getTotalPages());
        return new ApiPage(data, pagination);
    }

    @Transactional(readOnly = true)
    public UserResponse getUser(UUID id) {
        return UserResponse.from(userService.getById(id));
    }

    @Transactional
    public UserResponse updateRole(UUID id, UpdateUserRoleRequest request) {
        UserRole newRole = request.role();
        if (newRole != UserRole.CLASS_REP && newRole != UserRole.STUDENT) {
            throw new ApplicationException(
                    ErrorCodes.INVALID_ROLE_CHANGE,
                    "Role may only be changed to CLASS_REP or STUDENT",
                    HttpStatus.BAD_REQUEST);
        }

        User target = userService.getById(id);
        UUID actorId = currentAdminId();
        if (actorId.equals(target.getId())) {
            throw new ApplicationException(
                    ErrorCodes.CANNOT_MODIFY_SELF,
                    "You cannot change your own role",
                    HttpStatus.BAD_REQUEST);
        }

        if (target.getRole() == UserRole.SUPER_ADMIN) {
            protectLastSuperAdminRoleChange(target);
        }

        UserRole previous = target.getRole();
        target.changeRole(newRole);
        User saved = userRepository.saveAndFlush(target);

        auditService.record(
                AuditAction.USER_ROLE_CHANGED,
                AuditEntityTypes.USER,
                saved.getId(),
                "Changed role for " + saved.getEmail() + " from " + previous + " to " + saved.getRole());

        return UserResponse.from(saved);
    }

    @Transactional
    public UserResponse updateStatus(UUID id, UpdateUserStatusRequest request) {
        UserStatus newStatus = request.status();
        if (newStatus == UserStatus.PENDING_SETUP) {
            throw new ApplicationException(
                    ErrorCodes.INVALID_STATUS_CHANGE,
                    "PENDING_SETUP is managed by the Class Rep onboarding flow",
                    HttpStatus.BAD_REQUEST);
        }
        User target = userService.getById(id);
        UUID actorId = currentAdminId();

        if (actorId.equals(target.getId())) {
            throw new ApplicationException(
                    ErrorCodes.CANNOT_MODIFY_SELF,
                    "You cannot change your own status",
                    HttpStatus.BAD_REQUEST);
        }

        if (target.getRole() == UserRole.SUPER_ADMIN
                && newStatus != UserStatus.ACTIVE
                && target.getStatus() == UserStatus.ACTIVE) {
            protectLastActiveSuperAdmin(target);
        }

        UserStatus previous = target.getStatus();
        target.changeStatus(newStatus);
        User saved = userRepository.saveAndFlush(target);

        auditService.record(
                AuditAction.USER_STATUS_CHANGED,
                AuditEntityTypes.USER,
                saved.getId(),
                "Changed status for "
                        + saved.getEmail()
                        + " from "
                        + previous
                        + " to "
                        + saved.getStatus());

        return UserResponse.from(saved);
    }

    private void protectLastSuperAdminRoleChange(User target) {
        if (userRepository.countByRole(UserRole.SUPER_ADMIN) <= 1) {
            throw new ApplicationException(
                    ErrorCodes.LAST_SUPER_ADMIN_PROTECTED,
                    "Cannot change role of the only SUPER_ADMIN",
                    HttpStatus.BAD_REQUEST);
        }
        if (target.getStatus() == UserStatus.ACTIVE
                && userRepository.countByRoleAndStatus(UserRole.SUPER_ADMIN, UserStatus.ACTIVE) <= 1) {
            throw new ApplicationException(
                    ErrorCodes.LAST_SUPER_ADMIN_PROTECTED,
                    "Cannot change role of the only active SUPER_ADMIN",
                    HttpStatus.BAD_REQUEST);
        }
    }

    private void protectLastActiveSuperAdmin(User target) {
        if (userRepository.countByRoleAndStatus(UserRole.SUPER_ADMIN, UserStatus.ACTIVE) <= 1) {
            throw new ApplicationException(
                    ErrorCodes.LAST_SUPER_ADMIN_PROTECTED,
                    "Cannot suspend or disable the only active SUPER_ADMIN",
                    HttpStatus.BAD_REQUEST);
        }
    }

    private static UUID currentAdminId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null
                || !(authentication.getPrincipal() instanceof ClassHubUserDetails principal)) {
            throw new ApplicationException(
                    ErrorCodes.UNAUTHENTICATED,
                    "Authentication required",
                    HttpStatus.UNAUTHORIZED);
        }
        return principal.getId();
    }

    public record ApiPage(List<UserResponse> data, Pagination pagination) {
    }
}
