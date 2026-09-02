package com.classhub.security;

import com.classhub.academicclass.ClassMembershipAccessService;
import com.classhub.common.api.ApiErrorBody;
import com.classhub.common.api.ErrorCodes;
import tools.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

public class ActiveClassMembershipFilter extends OncePerRequestFilter {

    private static final Set<String> ALLOWED_WITHOUT_ACTIVE_MEMBERSHIP = Set.of(
            "/api/v1/me/class-membership",
            "/api/v1/classes/join",
            "/health",
            "/ready");

    private final ClassMembershipAccessService membershipAccessService;
    private final ObjectMapper objectMapper;

    public ActiveClassMembershipFilter(
            ClassMembershipAccessService membershipAccessService, ObjectMapper objectMapper) {
        this.membershipAccessService = membershipAccessService;
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        if (isAllowedWithoutMembership(request)) {
            filterChain.doFilter(request, response);
            return;
        }

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null
                || !authentication.isAuthenticated()
                || !(authentication.getPrincipal() instanceof ClassHubUserDetails principal)) {
            filterChain.doFilter(request, response);
            return;
        }

        if (membershipAccessService.bypassesMembership(principal.getRole())) {
            filterChain.doFilter(request, response);
            return;
        }

        if (membershipAccessService.findActiveMembership(principal.getId()).isPresent()) {
            filterChain.doFilter(request, response);
            return;
        }

        response.setStatus(HttpStatus.FORBIDDEN.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(
                response.getOutputStream(),
                ApiErrorBody.of(
                        ErrorCodes.CLASS_MEMBERSHIP_REQUIRED,
                        "Active class membership is required",
                        request.getRequestURI()));
    }

    private static boolean isAllowedWithoutMembership(HttpServletRequest request) {
        String path = request.getRequestURI();
        if (path.startsWith("/api/v1/auth/")) {
            return true;
        }
        if (path.startsWith("/api/v1/me/push-subscriptions")) {
            return true;
        }
        if (ALLOWED_WITHOUT_ACTIVE_MEMBERSHIP.contains(path)) {
            return true;
        }
        return path.startsWith("/api/v1/admin/");
    }
}
