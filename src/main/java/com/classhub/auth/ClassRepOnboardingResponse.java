package com.classhub.auth;

public record ClassRepOnboardingResponse(String message) {
    static ClassRepOnboardingResponse accepted() {
        return new ClassRepOnboardingResponse(
                "If the registration details are valid, account setup instructions will be sent.");
    }
}
