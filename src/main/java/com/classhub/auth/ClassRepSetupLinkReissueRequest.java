package com.classhub.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ClassRepSetupLinkReissueRequest(
        @NotBlank @Email @Size(max = 320) String email,
        @NotBlank @Size(max = 32) String phoneNumber) {
}
