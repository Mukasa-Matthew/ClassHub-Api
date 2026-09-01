package com.classhub.auth;

import com.classhub.common.api.ErrorCodes;
import com.classhub.common.exception.ApplicationException;
import com.classhub.security.ClassHubUserDetails;
import com.classhub.user.User;
import com.classhub.user.UserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

    private static final String AUTH_FAILED_MESSAGE = "Invalid email or password";

    private final AuthenticationManager authenticationManager;
    private final UserService userService;
    private final LoginRateLimiter loginRateLimiter;
    private final SecurityContextRepository securityContextRepository =
            new HttpSessionSecurityContextRepository();

    public AuthService(
            AuthenticationManager authenticationManager,
            UserService userService,
            LoginRateLimiter loginRateLimiter) {
        this.authenticationManager = authenticationManager;
        this.userService = userService;
        this.loginRateLimiter = loginRateLimiter;
    }

    public AuthenticatedUserResponse login(
            LoginRequest request,
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse) {
        loginRateLimiter.check(clientKey(httpRequest));

        String email;
        try {
            email = UserService.normalizeEmail(request.email());
        } catch (ApplicationException ex) {
            throw authenticationFailed();
        }

        try {
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(email, request.password()));

            SecurityContext context = SecurityContextHolder.createEmptyContext();
            context.setAuthentication(authentication);
            SecurityContextHolder.setContext(context);
            securityContextRepository.saveContext(context, httpRequest, httpResponse);

            ClassHubUserDetails principal = (ClassHubUserDetails) authentication.getPrincipal();
            return toResponse(userService.getById(principal.getId()));
        } catch (AuthenticationException ex) {
            throw authenticationFailed();
        }
    }

    public void logout(HttpServletRequest request) {
        SecurityContextHolder.clearContext();
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
    }

    public AuthenticatedUserResponse currentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null
                || !authentication.isAuthenticated()
                || !(authentication.getPrincipal() instanceof ClassHubUserDetails principal)) {
            throw new ApplicationException(
                    ErrorCodes.UNAUTHENTICATED,
                    "Authentication required",
                    HttpStatus.UNAUTHORIZED);
        }
        return toResponse(userService.getById(principal.getId()));
    }

    public static AuthenticatedUserResponse toResponse(User user) {
        return new AuthenticatedUserResponse(
                user.getId(),
                user.getFirstName(),
                user.getLastName(),
                user.getEmail(),
                user.getPhoneNumber(),
                user.getRole(),
                user.getStatus(),
                user.isEmailVerified());
    }

    private static ApplicationException authenticationFailed() {
        return new ApplicationException(
                ErrorCodes.AUTHENTICATION_FAILED,
                AUTH_FAILED_MESSAGE,
                HttpStatus.UNAUTHORIZED);
    }

    private static String clientKey(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr() == null ? "unknown" : request.getRemoteAddr();
    }
}
