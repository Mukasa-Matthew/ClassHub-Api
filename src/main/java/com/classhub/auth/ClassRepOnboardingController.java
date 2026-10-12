package com.classhub.auth;

import com.classhub.academicclass.AcademicClassResponse;
import com.classhub.common.api.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth/class-rep")
public class ClassRepOnboardingController {

    private final ClassRepOnboardingService onboardingService;

    public ClassRepOnboardingController(ClassRepOnboardingService onboardingService) {
        this.onboardingService = onboardingService;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public ApiResponse<ClassRepOnboardingResponse> register(
            @Valid @RequestBody ClassRepRegistrationRequest request,
            HttpServletRequest httpRequest) {
        return ApiResponse.of(onboardingService.register(request, clientKey(httpRequest)));
    }

    @PostMapping("/setup-link/reissue")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public ApiResponse<ClassRepOnboardingResponse> reissue(
            @Valid @RequestBody ClassRepSetupLinkReissueRequest request,
            HttpServletRequest httpRequest) {
        return ApiResponse.of(onboardingService.reissue(request, clientKey(httpRequest)));
    }

    @PostMapping("/complete-account")
    public ApiResponse<AcademicClassResponse> complete(
            @Valid @RequestBody ClassRepSetupRequest request) {
        return ApiResponse.of(onboardingService.complete(request));
    }

    private static String clientKey(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr() == null ? "unknown" : request.getRemoteAddr();
    }
}
