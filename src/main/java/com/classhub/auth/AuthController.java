package com.classhub.auth;

import com.classhub.academicclass.ClassMembershipService;
import com.classhub.common.api.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;
    private final ClassMembershipService classMembershipService;

    public AuthController(AuthService authService, ClassMembershipService classMembershipService) {
        this.authService = authService;
        this.classMembershipService = classMembershipService;
    }

    @GetMapping("/csrf")
    public ApiResponse<CsrfTokenResponse> csrf(CsrfToken csrfToken) {
        return ApiResponse.of(new CsrfTokenResponse(csrfToken.getToken(), csrfToken.getHeaderName()));
    }

    @PostMapping("/register")
    public ApiResponse<AuthenticatedUserResponse> register(@Valid @RequestBody RegisterRequest request) {
        return ApiResponse.of(classMembershipService.register(request));
    }

    @PostMapping("/login")
    public ApiResponse<AuthenticatedUserResponse> login(
            @Valid @RequestBody LoginRequest request,
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse) {
        return ApiResponse.of(authService.login(request, httpRequest, httpResponse));
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(HttpServletRequest request) {
        authService.logout(request);
    }

    @GetMapping("/me")
    public ApiResponse<AuthenticatedUserResponse> me() {
        return ApiResponse.of(authService.currentUser());
    }
}
