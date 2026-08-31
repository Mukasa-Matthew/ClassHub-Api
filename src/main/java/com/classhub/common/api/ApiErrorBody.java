package com.classhub.common.api;

public record ApiErrorBody(ApiError error) {

    public static ApiErrorBody of(String code, String message) {
        return new ApiErrorBody(new ApiError(code, message));
    }
}
