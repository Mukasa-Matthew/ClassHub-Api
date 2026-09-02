package com.classhub.academicclass;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record JoinClassRequest(@NotBlank @Size(min = 4, max = 16) String joinCode) {
}
