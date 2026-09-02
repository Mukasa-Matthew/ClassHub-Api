package com.classhub.common.api;

import java.time.Instant;
import java.util.List;

public record ApiErrorBody(ApiError error) {

    public static ApiErrorBody of(String code, String message) {
        return of(code, message, null, null);
    }

    public static ApiErrorBody of(String code, String message, String path) {
        return of(code, message, path, null);
    }

    public static ApiErrorBody of(
            String code, String message, String path, List<ApiError.FieldErrorDetail> fieldErrors) {
        return new ApiErrorBody(new ApiError(code, message, Instant.now(), path, fieldErrors));
    }
}
