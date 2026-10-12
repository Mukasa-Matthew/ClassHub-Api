package com.classhub.auth;

import com.classhub.security.ClassHubUserDetails;
import java.util.UUID;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.stereotype.Component;

@Component
public class UserSessionInvalidator {

    private final SessionRegistry sessionRegistry;

    public UserSessionInvalidator(SessionRegistry sessionRegistry) {
        this.sessionRegistry = sessionRegistry;
    }

    public void invalidateAll(UUID userId) {
        for (Object principal : sessionRegistry.getAllPrincipals()) {
            if (principal instanceof ClassHubUserDetails details && details.getId().equals(userId)) {
                sessionRegistry.getAllSessions(principal, false)
                        .forEach(session -> session.expireNow());
            }
        }
    }
}
