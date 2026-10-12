package com.classhub.notification;

import com.classhub.common.api.ErrorCodes;
import com.classhub.common.exception.ApplicationException;
import com.classhub.security.ClassHubUserDetails;
import com.classhub.user.User;
import com.classhub.user.UserRepository;
import com.classhub.user.UserRole;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class NotificationPreferenceService {

    private final NotificationPreferenceRepository preferenceRepository;
    private final UserRepository userRepository;

    public NotificationPreferenceService(
            NotificationPreferenceRepository preferenceRepository, UserRepository userRepository) {
        this.preferenceRepository = preferenceRepository;
        this.userRepository = userRepository;
    }

    @Transactional
    public NotificationPreferenceResponse getOwn() {
        User student = requireStudent();
        return NotificationPreferenceResponse.from(resolvePreference(student));
    }

    @Transactional
    public NotificationPreferenceResponse updateOwn(UpdateNotificationPreferenceRequest request) {
        User student = requireStudent();
        NotificationPreference preference = resolvePreference(student);
        preference.update(request.emailEnabled(), request.whatsappEnabled());
        return NotificationPreferenceResponse.from(preferenceRepository.saveAndFlush(preference));
    }

    private NotificationPreference resolvePreference(User student) {
        return preferenceRepository
                .findByUserId(student.getId())
                .orElseGet(() -> preferenceRepository.saveAndFlush(
                        new NotificationPreference(student, true, false)));
    }

    private User requireStudent() {
        ClassHubUserDetails principal = currentPrincipal();
        if (!principal.getRole().isStudentLike()) {
            throw new ApplicationException(
                    ErrorCodes.FORBIDDEN,
                    "Notification preferences are only available to students",
                    HttpStatus.FORBIDDEN);
        }
        return userRepository
                .findById(principal.getId())
                .orElseThrow(() -> new ApplicationException(
                        ErrorCodes.UNAUTHENTICATED,
                        "Authentication required",
                        HttpStatus.UNAUTHORIZED));
    }

    private static ClassHubUserDetails currentPrincipal() {
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
}
