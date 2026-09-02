package com.classhub.auth;

public record PasswordRecoveryResponse(String message) {
    private static final String GENERIC =
            "If an account matches those details, verification instructions have been sent.";

    public static PasswordRecoveryResponse accepted() { return new PasswordRecoveryResponse(GENERIC); }
}
